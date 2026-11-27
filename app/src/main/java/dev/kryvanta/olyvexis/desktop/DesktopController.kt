package dev.kryvanta.olyvexis.desktop

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import dev.kryvanta.olyvexis.display.VirtualDisplayController
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Một cửa sổ trên desktop. Toạ độ và kích thước tính bằng pixel của màn hình desktop. */
data class AppWindow(
    val id: Int,
    val app: LaunchableApp,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** Thứ tự chồng: số lớn hơn nằm trên. */
    val z: Int,
    val minimized: Boolean = false,
    val keepOnTop: Boolean = false,
    /** Khung trước khi phóng to (x, y, w, h); null nếu đang không phóng to. */
    val restoreBounds: List<Int>? = null,
    /** Màn hình ảo riêng của app trong cửa sổ; null khi chưa tạo xong. */
    val displayId: Int? = null,
    val error: String? = null,
)

/** Menu ngữ cảnh tổng quát trên desktop (nền, thanh tác vụ, icon, cửa sổ). */
data class DesktopContextMenu(
    val x: Int,
    val y: Int,
    val items: List<Pair<String, () -> Unit>>,
)

/**
 * Trình quản lý cửa sổ của Olyvexis.
 */
object DesktopController {
    private const val TAG = "Olyvexis/Desktop"
    private const val PREFS = "desktop_prefs"

    @Volatile var density = 1f

    val TITLE_BAR get() = (34 * density).toInt() // như iDesk
    val TASKBAR get() = (48 * density).toInt() // như iDesk
    val MIN_WIDTH get() = (340 * density).toInt()
    val MIN_HEIGHT get() = (220 * density).toInt()

    private val WINDOW_DPI get() = (160 * density).toInt()

    val cursor = MutableStateFlow<Pair<Float, Float>?>(null)
    val finishRequests = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ids = AtomicInteger(1)
    private val displays = mutableMapOf<Int, VirtualDisplayController>()
    private var prefs: SharedPreferences? = null
    private var toastJob: Job? = null
    private var switcherJob: Job? = null

    private val inputQueue = Channel<Pair<Int, android.view.InputEvent>>(Channel.UNLIMITED)

    private val _windows = MutableStateFlow<List<AppWindow>>(emptyList())
    val windows: StateFlow<List<AppWindow>> = _windows.asStateFlow()

    val drawerOpen = MutableStateFlow(false)
    val quickPanelOpen = MutableStateFlow(false)
    val contextMenu = MutableStateFlow<DesktopContextMenu?>(null)
    val snapPreview = MutableStateFlow<Snap?>(null)

    /** Chỉ số hình nền (-1 = hình nền động theo giờ, 0..N = hình nền gradient). */
    val wallpaper = MutableStateFlow(-1)
    val nightLight = MutableStateFlow(false)
    val brightness = MutableStateFlow(1f)

    /** Danh sách tên gói mở gần đây (mới nhất đứng đầu). */
    val recentPackages = MutableStateFlow<List<String>>(emptyList())

    /** Bộ đếm tăng mỗi khi danh sách app ghim thay đổi. */
    val pinsVersion = MutableStateFlow(0)

    /** Thanh chuyển đổi cửa sổ Alt+Tab. */
    val switcherActive = MutableStateFlow(false)

    /** Thông báo nổi ngắn ở mép trên desktop. */
    val toastMessage = MutableStateFlow<String?>(null)

    var desktopWidth = 1280
    var desktopHeight = 720

    init {
        scope.launch(Dispatchers.IO) {
            for ((displayId, event) in inputQueue) {
                runCatching {
                    ShizukuManager.withShell {
                        when (event) {
                            is MotionEvent -> it.injectMotionEvent(displayId, event)
                            is KeyEvent -> it.injectKeyEvent(displayId, event)
                            else -> false
                        }
                    }
                }.onFailure { Log.w(TAG, "Không chuyển được sự kiện", it) }
                if (event is MotionEvent) event.recycle()
            }
        }
    }

    fun initPrefs(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        wallpaper.value = p.getInt("wallpaper", -1)
        nightLight.value = p.getBoolean("night_light", false)
        brightness.value = p.getFloat("brightness", 1f).coerceIn(0.35f, 1f)
        val recentsRaw = p.getString("recent_packages", "") ?: ""
        recentPackages.value = recentsRaw.split('|').filter { it.isNotBlank() }
    }

    fun setWallpaper(index: Int) {
        wallpaper.value = index
        prefs?.edit()?.putInt("wallpaper", index)?.apply()
    }

    fun toggleNightLight() {
        val next = !nightLight.value
        nightLight.value = next
        prefs?.edit()?.putBoolean("night_light", next)?.apply()
        showToast(if (next) "Đã bật Ánh sáng đêm" else "Đã tắt Ánh sáng đêm")
    }

    fun setBrightness(value: Float) {
        val v = value.coerceIn(0.35f, 1f)
        brightness.value = v
        prefs?.edit()?.putFloat("brightness", v)?.apply()
    }

    fun showToast(message: String) {
        toastMessage.value = message
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2_200)
            toastMessage.value = null
        }
    }

    /**
     * Mở app: nếu đã có cửa sổ thì đưa lên trước; nếu chưa thì mở cửa sổ mới (khôi phục vị trí/cỡ đã nhớ).
     */
    fun open(app: LaunchableApp) {
        val pkg = app.component.packageName
        recordRecent(pkg)
        quickPanelOpen.value = false
        contextMenu.value = null

        val existing = _windows.value.firstOrNull { it.app.component.packageName == pkg }
        if (existing != null) {
            focus(existing.id)
            return
        }

        val saved = loadBounds(pkg)
        _windows.update { list ->
            val n = list.size
            val maxW = desktopWidth.coerceAtLeast(MIN_WIDTH)
            val maxH = (desktopHeight - TASKBAR - TITLE_BAR).coerceAtLeast(MIN_HEIGHT)
            val defaultW = (desktopWidth * 0.56).toInt().coerceIn(MIN_WIDTH, maxW)
            val defaultH = ((desktopHeight - TASKBAR) * 0.68).toInt().coerceIn(MIN_HEIGHT, maxH)
            val w = (saved?.getOrNull(2) ?: defaultW).coerceIn(MIN_WIDTH, maxW)
            val h = (saved?.getOrNull(3) ?: defaultH).coerceIn(MIN_HEIGHT, maxH)
            val x = (saved?.getOrNull(0) ?: (44 + 34 * n)).coerceIn(0, (desktopWidth - w).coerceAtLeast(0))
            val y = (saved?.getOrNull(1) ?: (28 + 30 * n)).coerceIn(0, (desktopHeight - TASKBAR - h - TITLE_BAR).coerceAtLeast(0))
            list + AppWindow(ids.getAndIncrement(), app, x, y, w, h, z = topZ(list) + 1)
        }
    }

    private fun recordRecent(pkg: String) {
        val updated = (listOf(pkg) + recentPackages.value.filterNot { it == pkg }).take(8)
        recentPackages.value = updated
        prefs?.edit()?.putString("recent_packages", updated.joinToString("|"))?.apply()
    }

    private fun saveBounds(window: AppWindow) {
        if (window.restoreBounds != null) return
        prefs?.edit()?.putString(
            "bounds_${window.app.component.packageName}",
            "${window.x},${window.y},${window.width},${window.height}",
        )?.apply()
    }

    private fun loadBounds(pkg: String): List<Int>? {
        val raw = prefs?.getString("bounds_$pkg", null) ?: return null
        val parts = raw.split(',').mapNotNull { it.toIntOrNull() }
        return if (parts.size == 4) parts else null
    }

    fun attachSurface(windowId: Int, surface: Surface) {
        scope.launch {
            val window = find(windowId) ?: return@launch
            try {
                val existing = displays[windowId]
                if (existing != null) {
                    existing.setSurface(surface)
                    return@launch
                }
                val vd = VirtualDisplayController()
                displays[windowId] = vd
                val (displayId, _) = vd.attach(window.width, window.height, WINDOW_DPI, surface)
                update(windowId) { it.copy(displayId = displayId, error = null) }
                val r = vd.launchInWindow(window.app.component)
                if (!r.isSuccess || r.stdout.contains("Error")) {
                    update(windowId) { it.copy(error = (r.stdout + "\n" + r.stderr).trim()) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Không mở được cửa sổ ${window.app.label}", e)
                update(windowId) { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    fun retryLaunch(windowId: Int) {
        val window = find(windowId) ?: return
        val vd = displays[windowId] ?: return
        update(windowId) { it.copy(error = null) }
        scope.launch {
            try {
                val r = vd.launchInWindow(window.app.component)
                if (!r.isSuccess || r.stdout.contains("Error")) {
                    update(windowId) { it.copy(error = (r.stdout + "\n" + r.stderr).trim()) }
                }
            } catch (e: Exception) {
                update(windowId) { it.copy(error = e.message ?: e.toString()) }
            }
        }
    }

    fun detachSurface(windowId: Int) {
        scope.launch { runCatching { displays[windowId]?.setSurface(null) } }
    }

    fun focus(windowId: Int) {
        _windows.update { list ->
            val top = topZ(list)
            list.map {
                if (it.id == windowId && (it.z != top || it.minimized)) {
                    it.copy(z = top + 1, minimized = false)
                } else {
                    it
                }
            }
        }
    }

    fun toggleKeepOnTop(windowId: Int) {
        update(windowId) { it.copy(keepOnTop = !it.keepOnTop) }
        val pinned = find(windowId)?.keepOnTop == true
        showToast(if (pinned) "Đã ghim cửa sổ lên trên" else "Đã bỏ ghim cửa sổ")
    }

    fun move(windowId: Int, dx: Int, dy: Int) = update(windowId) {
        it.copy(
            restoreBounds = null,
            x = (it.x + dx).coerceIn(-it.width + 80, desktopWidth - 80),
            y = (it.y + dy).coerceIn(0, desktopHeight - TASKBAR - TITLE_BAR),
        )
    }

    fun commitMove(windowId: Int) {
        find(windowId)?.let(::saveBounds)
    }

    fun resizeFrame(windowId: Int, dw: Int, dh: Int) = update(windowId) {
        it.copy(
            restoreBounds = null,
            width = (it.width + dw).coerceIn(MIN_WIDTH, desktopWidth),
            height = (it.height + dh).coerceIn(MIN_HEIGHT, desktopHeight - TASKBAR - TITLE_BAR),
        )
    }

    fun commitResize(windowId: Int) {
        val window = find(windowId) ?: return
        saveBounds(window)
        scope.launch {
            runCatching { displays[windowId]?.resize(window.width, window.height, WINDOW_DPI) }
                .onFailure { Log.w(TAG, "Không đổi được kích thước", it) }
        }
    }

    fun minimize(windowId: Int) = update(windowId) { it.copy(minimized = true) }

    fun toggleMaximize(windowId: Int) {
        update(windowId) {
            val restore = it.restoreBounds
            if (restore != null) {
                it.copy(x = restore[0], y = restore[1], width = restore[2], height = restore[3], restoreBounds = null)
            } else {
                it.copy(
                    x = 0, y = 0,
                    width = desktopWidth,
                    height = desktopHeight - TASKBAR - TITLE_BAR,
                    restoreBounds = listOf(it.x, it.y, it.width, it.height),
                )
            }
        }
        focus(windowId)
        commitResize(windowId)
    }

    enum class Snap { Left, Right, Top }

    fun snap(windowId: Int, side: Snap) {
        snapPreview.value = null
        if (side == Snap.Top) {
            if (find(windowId)?.restoreBounds == null) toggleMaximize(windowId)
            return
        }
        val half = desktopWidth / 2
        update(windowId) {
            it.copy(
                x = if (side == Snap.Left) 0 else half,
                y = 0,
                width = half,
                height = desktopHeight - TASKBAR - TITLE_BAR,
                restoreBounds = it.restoreBounds ?: listOf(it.x, it.y, it.width, it.height),
            )
        }
        focus(windowId)
        commitResize(windowId)
    }

    fun resizeFromLeft(windowId: Int, dx: Int, dh: Int) = update(windowId) {
        val newWidth = (it.width - dx).coerceIn(MIN_WIDTH, desktopWidth)
        it.copy(
            x = it.x + (it.width - newWidth),
            width = newWidth,
            height = (it.height + dh).coerceIn(MIN_HEIGHT, desktopHeight - TASKBAR - TITLE_BAR),
            restoreBounds = null,
        )
    }

    fun minimizeAll() {
        _windows.update { list -> list.map { it.copy(minimized = true) } }
        quickPanelOpen.value = false
    }

    /** Alt+Tab: đưa cửa sổ tiếp theo lên trên và hiện thanh chuyển cửa sổ ngắn. */
    fun cycle() {
        val list = _windows.value
        if (list.isEmpty()) return
        val bottom = list.minByOrNull { if (it.minimized) Int.MIN_VALUE else it.z } ?: return
        focus(bottom.id)
        if (list.size > 1) {
            switcherActive.value = true
            switcherJob?.cancel()
            switcherJob = scope.launch {
                delay(1_100)
                switcherActive.value = false
            }
        }
    }

    fun toggleFromTaskbar(windowId: Int) {
        val window = find(windowId) ?: return
        val isTop = window.id == topWindow()?.id && !window.minimized
        if (isTop) minimize(windowId) else focus(windowId)
    }

    fun close(windowId: Int) {
        find(windowId)?.let(::saveBounds)
        _windows.update { list -> list.filterNot { it.id == windowId } }
        val vd = displays.remove(windowId) ?: return
        scope.launch { vd.release() }
    }

    fun closeAll() {
        _windows.value.forEach { close(it.id) }
        quickPanelOpen.value = false
    }

    fun forwardMotion(windowId: Int, event: MotionEvent) {
        val displayId = find(windowId)?.displayId ?: return
        inputQueue.trySend(displayId to MotionEvent.obtain(event))
    }

    fun forwardToDisplay(displayId: Int, event: MotionEvent) {
        inputQueue.trySend(displayId to MotionEvent.obtain(event))
    }

    /** Nút Quay lại trên thanh tác vụ: gửi phím Back vào cửa sổ. */
    fun sendBack(windowId: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        forwardKey(windowId, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        forwardKey(windowId, KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
    }

    fun forwardKey(windowId: Int, event: KeyEvent) {
        val displayId = find(windowId)?.displayId ?: return
        inputQueue.trySend(displayId to KeyEvent(event))
    }

    fun topWindow(): AppWindow? =
        _windows.value.filterNot { it.minimized }.maxWithOrNull(compareBy<AppWindow> { it.keepOnTop }.thenBy { it.z })

    private fun find(windowId: Int) = _windows.value.firstOrNull { it.id == windowId }

    private fun topZ(list: List<AppWindow>) = list.maxOfOrNull { it.z } ?: 0

    private fun update(windowId: Int, change: (AppWindow) -> AppWindow) {
        _windows.update { list -> list.map { if (it.id == windowId) change(it) else it } }
    }
}
