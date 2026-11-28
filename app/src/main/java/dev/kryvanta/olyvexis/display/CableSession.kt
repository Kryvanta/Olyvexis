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
    /** [vendorDesktop]: OEM desktop mode still running on the display (e.g. "Samsung DeX"). */
    data class Running(val displayId: Int, val name: String, val vendorDesktop: String? = null) : CableState
    data class Failed(val message: String) : CableState
}

/**
 * Wired mode: launches Olyvexis desktop directly onto a physical external display (HDMI/DP over USB-C).
 *
 * Enables Android's hidden desktop mode (`force_desktop_mode_on_external_displays`) so the system
 * treats the external display as a desktop display (hardware cursor, etc.). Does not enable
 * `enable_freeform_support` (see [enableHiddenDesktopMode]). Saves previous values and restores
 * them on stop. App windows are still managed by Olyvexis, independent of OEM freeform support.
 *
 * Only works when OEM desktop mode (e.g. Samsung DeX) is off; otherwise DeX owns the display.
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

    /** Cable unplugged: stop session (close windows, restore settings) instead of falling back to phone. */
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
            // Launch fullscreen (windowingMode 1): if DeX/OEM desktop mode is active it wraps apps in
            // a floating window; fullscreen lets Olyvexis cover the entire external display.
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
     * Whether an OEM desktop mode is active on [displayId]. Apps often cannot see DeX's private
     * display directly, so query via Shizuku whether a DeX launcher task is running on it.
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

    /** OEM desktop launcher packages -> user-facing name. Samsung verified on device; others are best-effort. */
    private val VENDOR_LAUNCHERS = mapOf(
        "com.sec.android.app.desktoplauncher" to "Samsung DeX",
        "com.motorola.mobiledesktop" to "Motorola Ready For",
        "com.hihonor.desktop.systemui" to "Honor PC mode",
        "com.huawei.desktop.systemui" to "Huawei PC mode",
    )

    /**
     * Some ROMs (e.g. Honor MagicOS) still open the desktop as a floating window despite requesting
     * fullscreen. Resize its task bounds to fill the external display when that happens.
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
     * Restore system settings if a previous wired session crashed, was force-stopped, or updated
     * before cleanup. Called on startup once Shizuku is ready; no-op if active or nothing saved.
     */
    suspend fun recoverLeftoverSettings(context: Context) = mutex.withLock {
        if (_state.value is CableState.Running || _state.value is CableState.Starting) return@withLock
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("saved", false)) return@withLock
        Log.i(TAG, "Trả lại cài đặt hệ thống còn sót từ lần chạy trước")
        runCatching { restoreSettings(context) }.onFailure { Log.w(TAG, "Không trả lại được cài đặt", it) }
    }

    /** Enable Android's hidden desktop mode and save previous values for restoration. */
    private suspend fun enableHiddenDesktopMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("saved")) {
            val desktop = ShizukuManager.runCommand("settings get global force_desktop_mode_on_external_displays").stdout
            val freeform = ShizukuManager.runCommand("settings get global enable_freeform_support").stdout
            prefs.edit().putString("desktop", desktop).putString("freeform", freeform).putBoolean("saved", true).apply()
        }
        ShizukuManager.runCommand(ShellCommands.FORCE_DESKTOP_MODE_ON)
        // Do not enable enable_freeform_support: it causes external displays to open everything as
        // floating windows (e.g. Honor places the desktop in a corner). Olyvexis manages windows
        // itself. If a previous run left it enabled, restore the original value.
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
