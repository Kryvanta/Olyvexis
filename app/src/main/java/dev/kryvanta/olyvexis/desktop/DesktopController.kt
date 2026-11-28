package dev.kryvanta.olyvexis.desktop

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import dev.kryvanta.olyvexis.R
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

/** Desktop window. Coordinates and dimensions are in desktop display pixels. */
data class AppWindow(
    val id: Int,
    val app: LaunchableApp,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    /** Z-order: higher values render on top. */
    val z: Int,
    val minimized: Boolean = false,
    val keepOnTop: Boolean = false,
    val workspace: Int = 1,
    /** Bounds before maximizing (x, y, w, h); null when not maximized. */
    val restoreBounds: List<Int>? = null,
    /** Virtual display backing this window; null until created. */
    val displayId: Int? = null,
    val error: String? = null,
)

/** Unified desktop context menu (wallpaper, taskbar, icons, windows). */
data class DesktopContextMenu(
    val x: Int,
    val y: Int,
    val items: List<Pair<String, () -> Unit>>,
)

/**
 * Olyvexis desktop window manager.
 */
object DesktopController {
    private const val TAG = "Olyvexis/Desktop"
    private const val PREFS = "desktop_prefs"
    const val MAX_WINDOWS = 6
    const val WORKSPACE_COUNT = 4

    @Volatile var density = 1f

    val TITLE_BAR get() = (34 * density).toInt()
    val TASKBAR get() = (56 * density).toInt()
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
    val spotlightOpen = MutableStateFlow(false)
    val shortcutsOpen = MutableStateFlow(false)
    val settingsOpen = MutableStateFlow(false)
    val clipboardOpen = MutableStateFlow(false)
    val lockOpen = MutableStateFlow(false)
    val quickPanelOpen = MutableStateFlow(false)
    val contextMenu = MutableStateFlow<DesktopContextMenu?>(null)
    val snapPreview = MutableStateFlow<Snap?>(null)

    val currentWorkspace = MutableStateFlow(1)
    val stickyNoteOpen = MutableStateFlow(false)
    val stickyNoteText = MutableStateFlow("")

    /** Wallpaper index (-1 = dynamic time-of-day wallpaper, 0..N = gradient presets). */
    val wallpaper = MutableStateFlow(-1)
    val nightLight = MutableStateFlow(false)
    val brightness = MutableStateFlow(1f)
    val showWidgets = MutableStateFlow(true)
    val autoHideTaskbar = MutableStateFlow(false)

    /** Recently launched package names (most recent first). */
    val recentPackages = MutableStateFlow<List<String>>(emptyList())

    /** Monotonic counter incremented whenever pinned apps change. */
    val pinsVersion = MutableStateFlow(0)

    /** Alt+Tab window switcher overlay. */
    val switcherActive = MutableStateFlow(false)

    /** Transient toast banner at the top edge of the desktop. */
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
        showWidgets.value = p.getBoolean("show_widgets", true)
        autoHideTaskbar.value = p.getBoolean("auto_hide_taskbar", false)
        stickyNoteOpen.value = p.getBoolean("sticky_note_open", false)
        stickyNoteText.value = p.getString("sticky_note_text", "") ?: ""
        val recentsRaw = p.getString("recent_packages", "") ?: ""
        recentPackages.value = recentsRaw.split('|').filter { it.isNotBlank() }
    }

    fun toggleStickyNote() {
        val next = !stickyNoteOpen.value
        stickyNoteOpen.value = next
        prefs?.edit()?.putBoolean("sticky_note_open", next)?.apply()
    }

    fun setStickyNoteText(text: String) {
        stickyNoteText.value = text
        prefs?.edit()?.putString("sticky_note_text", text)?.apply()
    }

    fun switchWorkspace(ws: Int) {
        val target = ws.coerceIn(1, WORKSPACE_COUNT)
        if (currentWorkspace.value == target) return
        currentWorkspace.value = target
        closeOverlays()
    }

    fun moveToWorkspace(windowId: Int, ws: Int) {
        val target = ws.coerceIn(1, WORKSPACE_COUNT)
        update(windowId) { it.copy(workspace = target, minimized = false) }
        currentWorkspace.value = target
        focus(windowId)
    }

    fun lockDesktop() {
        closeOverlays()
        lockOpen.value = true
    }

    fun toggleWidgets() {
        val next = !showWidgets.value
        showWidgets.value = next
        prefs?.edit()?.putBoolean("show_widgets", next)?.apply()
    }

    fun toggleAutoHideTaskbar() {
        val next = !autoHideTaskbar.value
        autoHideTaskbar.value = next
        prefs?.edit()?.putBoolean("auto_hide_taskbar", next)?.apply()
    }

    fun closeOverlays() {
        drawerOpen.value = false
        spotlightOpen.value = false
        shortcutsOpen.value = false
        settingsOpen.value = false
        clipboardOpen.value = false
        quickPanelOpen.value = false
        contextMenu.value = null
    }

    /** Open Android System Settings in a desktop window. */
    fun openAndroidSettings(context: Context) {
        val apps = AppRepository(context).launchableApps()
        val settingsApp = apps.firstOrNull { it.component.packageName == "com.android.settings" }
        if (settingsApp != null) {
            open(settingsApp)
        } else {
            showToast(context.getString(R.string.desk_win_error, context.getString(R.string.desk_quick_android_settings)))
        }
    }

    fun setWallpaper(index: Int) {
        wallpaper.value = index
        prefs?.edit()?.putInt("wallpaper", index)?.apply()
    }

    fun toggleNightLight() {
        val next = !nightLight.value
        nightLight.value = next
        prefs?.edit()?.putBoolean("night_light", next)?.apply()
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
     * Launch or focus an app: brings an existing window to front, or opens a new window
     * restoring its last saved position and size.
     */
    fun open(app: LaunchableApp) {
        val pkg = app.component.packageName
        recordRecent(pkg)
        quickPanelOpen.value = false
        contextMenu.value = null

        val existing = _windows.value.firstOrNull { it.app.component.packageName == pkg }
        if (existing != null) {
            if (existing.workspace != currentWorkspace.value) {
                currentWorkspace.value = existing.workspace
            }
            focus(existing.id)
            return
        }

        if (_windows.value.size >= MAX_WINDOWS) {
            showToast("Max $MAX_WINDOWS windows open")
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
            list + AppWindow(ids.getAndIncrement(), app, x, y, w, h, z = topZ(list) + 1, workspace = currentWorkspace.value)
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
        showToast(if (pinned) "Pinned on top" else "Unpinned")
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

    enum class Snap { Left, Right, LeftThird, CenterThird, RightThird, Top, TopLeft, TopRight, BottomLeft, BottomRight }

    fun snap(windowId: Int, side: Snap) {
        snapPreview.value = null
        if (side == Snap.Top) {
            if (find(windowId)?.restoreBounds == null) toggleMaximize(windowId)
            return
        }
        val halfW = desktopWidth / 2
        val availH = desktopHeight - TASKBAR
        val halfH = availH / 2
        val (nx, ny, nw, nh) = when (side) {
            Snap.Left -> listOf(0, 0, halfW, availH - TITLE_BAR)
            Snap.Right -> listOf(halfW, 0, desktopWidth - halfW, availH - TITLE_BAR)
            Snap.LeftThird -> listOf(0, 0, desktopWidth / 3, availH - TITLE_BAR)
            Snap.CenterThird -> listOf(desktopWidth / 3, 0, desktopWidth / 3, availH - TITLE_BAR)
            Snap.RightThird -> listOf(desktopWidth * 2 / 3, 0, desktopWidth - desktopWidth * 2 / 3, availH - TITLE_BAR)
            Snap.TopLeft -> listOf(0, 0, halfW, halfH - TITLE_BAR)
            Snap.TopRight -> listOf(halfW, 0, desktopWidth - halfW, halfH - TITLE_BAR)
            Snap.BottomLeft -> listOf(0, halfH, halfW, availH - halfH - TITLE_BAR)
            Snap.BottomRight -> listOf(halfW, halfH, desktopWidth - halfW, availH - halfH - TITLE_BAR)
            Snap.Top -> return
        }
        update(windowId) {
            it.copy(
                x = nx,
                y = ny,
                width = nw.coerceAtLeast(MIN_WIDTH),
                height = nh.coerceAtLeast(MIN_HEIGHT),
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
        val ws = currentWorkspace.value
        _windows.update { list -> list.map { if (it.workspace == ws) it.copy(minimized = true) else it } }
        quickPanelOpen.value = false
    }

    /** Alt+Tab: focus the next window and briefly show the window switcher overlay. */
    fun cycle() {
        val ws = currentWorkspace.value
        val list = _windows.value.filter { it.workspace == ws }
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

    /** Taskbar Back button: inject a Back key event into the focused window. */
    fun sendBack(windowId: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        forwardKey(windowId, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0))
        forwardKey(windowId, KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0))
    }

    fun forwardKey(windowId: Int, event: KeyEvent) {
        val displayId = find(windowId)?.displayId ?: return
        inputQueue.trySend(displayId to KeyEvent(event))
    }

    fun topWindow(): AppWindow? {
        val ws = currentWorkspace.value
        return _windows.value
            .filter { !it.minimized && it.workspace == ws }
            .maxWithOrNull(compareBy<AppWindow> { it.keepOnTop }.thenBy { it.z })
    }

    private fun find(windowId: Int) = _windows.value.firstOrNull { it.id == windowId }

    private fun topZ(list: List<AppWindow>) = list.maxOfOrNull { it.z } ?: 0

    private fun update(windowId: Int, change: (AppWindow) -> AppWindow) {
        _windows.update { list -> list.map { if (it.id == windowId) change(it) else it } }
    }
}
