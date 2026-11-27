package dev.kryvanta.olyvexis.display

import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.kryvanta.olyvexis.desktop.DesktopActivity
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.input.ActiveInput
import dev.kryvanta.olyvexis.input.BrowserInput
import dev.kryvanta.olyvexis.shizuku.ShellCommands
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CableState {
    data object Idle : CableState
    data object Starting : CableState
    /** [vendorDesktop]: chế độ desktop của hãng vẫn đang chạy trên màn hình (vd. "Samsung DeX"). */
    data class Running(val displayId: Int, val name: String, val vendorDesktop: String? = null) : CableState
    data class Failed(val message: String) : CableState
}

/**
 * Cách qua cáp: mở desktop của Olyvexis lên màn hình ngoài thật (HDMI/DisplayPort qua USB-C).
 *
 * Bật chế độ desktop ẩn của Android (force_desktop_mode_on_external_displays) để hệ thống coi
 * màn hình ngoài là màn hình desktop (con trỏ chuột thật nằm trên đó...). Không bật
 * enable_freeform_support (xem enableHiddenDesktopMode). Lưu giá trị cũ và trả lại khi dừng. Cửa sổ app vẫn do Olyvexis tự
 * quản lý như cách Wi-Fi, nên không phụ thuộc freeform của hãng.
 *
 * Chỉ dùng được khi chế độ desktop của hãng (Samsung DeX...) đã tắt; nếu không, DeX giữ màn hình.
 */
object CableSession {
    private const val TAG = "Olyvexis/Cable"
    private const val PREFS = "cable"

    private val _state = MutableStateFlow<CableState>(CableState.Idle)
    val state: StateFlow<CableState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var input: BrowserInput? = null
    private var unplugListener: DisplayManager.DisplayListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Rút cáp: tự dừng phiên (đóng cửa sổ, trả cài đặt), không để desktop nhảy về điện thoại. */
    private fun watchUnplug(context: Context, displayId: Int) {
        val dm = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayRemoved(id: Int) {
                if (id == displayId) {
                    Log.i(TAG, "Màn hình #$id bị rút, dừng phiên")
                    scope.launch { stop(context) }
                }
            }
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayChanged(id: Int) = Unit
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        unplugListener = listener
    }

    suspend fun start(context: Context, mode: DisplayMode.External) = mutex.withLock {
        if (_state.value is CableState.Running) return@withLock
        _state.value = CableState.Starting
        try {
            enableHiddenDesktopMode(context)
            val vendor = mode.vendorDesktop ?: vendorDesktopOn(mode.displayId)
            // Mở toàn màn hình (windowingMode 1): nếu DeX/chế độ desktop của hãng vẫn chạy, nó sẽ
            // bọc app trong cửa sổ nổi; toàn màn hình thì desktop của Olyvexis phủ kín màn hình.
            val component = ComponentName(context, DesktopActivity::class.java).flattenToShortString()
            val r = ShizukuManager.runCommand(
                "am start --display ${mode.displayId} --windowingMode $WINDOWING_MODE_FULLSCREEN -n $component",
            )
            if (!r.isSuccess || r.stdout.contains("Error")) error("Không mở được desktop: ${r.stdout} ${r.stderr}".trim())
            fillDisplay(mode)
            input = BrowserInput(mode.displayId, mode.width, mode.height).also {
                it.drawCursor = true
                ActiveInput.current = it
            }
            watchUnplug(context.applicationContext, mode.displayId)
            _state.value = CableState.Running(mode.displayId, mode.name, vendor)
            Log.i(TAG, "Desktop trên ${mode.name} (#${mode.displayId}) ${mode.width}x${mode.height}")
        } catch (e: Exception) {
            Log.e(TAG, "Không bắt đầu được", e)
            _state.value = CableState.Failed(e.message ?: e.toString())
        }
    }

    suspend fun stop(context: Context) = mutex.withLock {
        unplugListener?.let { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        unplugListener = null
        DesktopController.closeAll()
        DesktopController.finishRequests.value += 1
        if (ActiveInput.current === input) ActiveInput.current = null
        input?.close()
        input = null
        runCatching { restoreSettings(context) }
        _state.value = CableState.Idle
    }

    private const val WINDOWING_MODE_FULLSCREEN = 1

    /**
     * Chế độ desktop của hãng đang chạy trên màn hình [displayId]? App thường không thấy màn hình
     * riêng của DeX, nên dò qua Shizuku: launcher của DeX có task trên màn hình đó không.
     */
    private suspend fun vendorDesktopOn(displayId: Int): String? {
        val tasks = runCatching { ShizukuManager.runCommand("am stack list").stdout }.getOrDefault("")
        var onDisplay = false
        for (line in tasks.lineSequence()) {
            val root = Regex("""RootTask id=\d+ .*displayId=(\d+)""").find(line)
            if (root != null) {
                onDisplay = root.groupValues[1].toInt() == displayId
                continue
            }
            if (onDisplay && VENDOR_LAUNCHERS.keys.any { line.contains(": $it/") }) {
                return VENDOR_LAUNCHERS.entries.first { line.contains(": ${it.key}/") }.value
            }
        }
        return null
    }

    /** Launcher của chế độ desktop các hãng -> tên hiện cho người dùng. Chỉ gói của Samsung đã kiểm trên máy thật; Motorola/Honor/Huawei là đoán, cần kiểm. */
    private val VENDOR_LAUNCHERS = mapOf(
        "com.sec.android.app.desktoplauncher" to "Samsung DeX",
        "com.motorola.mobiledesktop" to "Motorola Ready For",
        "com.hihonor.desktop.systemui" to "Honor PC mode",
        "com.huawei.desktop.systemui" to "Huawei PC mode",
    )

    /**
     * Một số hãng (vd. Honor MagicOS) vẫn mở desktop thành cửa sổ nổi dù đã xin toàn màn hình.
     * Khi đó kéo khung cửa sổ ra kín màn hình ngoài.
     */
    private suspend fun fillDisplay(mode: DisplayMode.External) {
        repeat(6) { attempt ->
            kotlinx.coroutines.delay(if (attempt == 0) 400 else 300)
            val list = ShizukuManager.runCommand("am stack list").stdout
            val line = list.lineSequence().firstOrNull { it.contains("dev.kryvanta.olyvexis/dev.kryvanta.olyvexis.desktop.DesktopActivity") }
                ?: return@repeat
            val task = Regex("""taskId=(\d+):""").find(line)?.groupValues?.get(1) ?: return
            val b = Regex("""bounds=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""").find(line)?.groupValues?.drop(1)?.map { it.toInt() }
            val full = b != null && b[0] <= 0 && b[1] <= 0 && b[2] >= mode.width && b[3] >= mode.height
            if (full) return
            Log.i(TAG, "Desktop đang là cửa sổ $b, kéo ra kín ${mode.width}x${mode.height}")
            ShizukuManager.runCommand("am task resize $task 0 0 ${mode.width} ${mode.height}")
            return
        }
    }

    /**
     * Trả lại cài đặt hệ thống nếu lần chạy trước không kịp trả (app bị tắt hẳn, cài lại hoặc văng
     * khi desktop đang chạy qua cáp). Gọi khi app mở lên và Shizuku sẵn sàng. Không làm gì nếu
     * phiên qua cáp đang chạy hoặc không có gì để trả.
     */
    suspend fun recoverLeftoverSettings(context: Context) = mutex.withLock {
        if (_state.value is CableState.Running || _state.value is CableState.Starting) return@withLock
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("saved", false)) return@withLock
        Log.i(TAG, "Trả lại cài đặt hệ thống còn sót từ lần chạy trước")
        runCatching { restoreSettings(context) }.onFailure { Log.w(TAG, "Không trả lại được cài đặt", it) }
    }

    /** Bật chế độ desktop ẩn của Android, lưu giá trị cũ để trả lại. */
    private suspend fun enableHiddenDesktopMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("saved")) {
            val desktop = ShizukuManager.runCommand("settings get global force_desktop_mode_on_external_displays").stdout
            val freeform = ShizukuManager.runCommand("settings get global enable_freeform_support").stdout
            prefs.edit().putString("desktop", desktop).putString("freeform", freeform).putBoolean("saved", true).apply()
        }
        ShizukuManager.runCommand(ShellCommands.FORCE_DESKTOP_MODE_ON)
        // Không bật enable_freeform_support: có nó, màn hình ngoài mặc định mở mọi thứ thành cửa sổ
        // nổi (vd. Honor đặt desktop thành cửa sổ ở góc). Olyvexis tự quản lý cửa sổ nên không cần.
        // Nếu lần trước đã bật thì trả về giá trị gốc.
        val freeform = prefs.getString("freeform", "null")
        ShizukuManager.runCommand(ShellCommands.restoreGlobalSetting("enable_freeform_support", freeform))
    }

    private suspend fun restoreSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("saved", false)) return
        for ((key, setting) in listOf("desktop" to "force_desktop_mode_on_external_displays", "freeform" to "enable_freeform_support")) {
            val old = prefs.getString(key, "null")
            ShizukuManager.runCommand(ShellCommands.restoreGlobalSetting(setting, old))
        }
        prefs.edit().clear().apply()
    }
}
