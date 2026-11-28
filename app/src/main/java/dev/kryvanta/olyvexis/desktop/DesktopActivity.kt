package dev.kryvanta.olyvexis.desktop

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop UI hosted on the secondary display (Wi-Fi virtual display or physical external display).
 *
 * Renders wallpaper, desktop shortcuts, app windows ([DesktopController]), taskbar with pinned/running
 * apps and system tray, and the app drawer. Launched onto the target display via ShellService, so
 * it must be exported.
 */
class DesktopActivity : ComponentActivity() {
    /** Target display ID where this desktop is hosted. */
    private var homeDisplayId = Display.INVALID_DISPLAY

    /**
     * On HDMI unplug, Android moves activities from the removed display back to the phone screen.
     * DesktopActivity must never appear on the phone, so close immediately if its display is removed
     * or it is reparented to another display.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayRemoved(displayId: Int) {
            if (displayId == homeDisplayId) finish()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    private fun movedAway(): Boolean {
        val now = display?.displayId ?: return false
        return now == Display.DEFAULT_DISPLAY || (homeDisplayId != Display.INVALID_DISPLAY && now != homeDisplayId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (movedAway()) finish()
    }

    override fun onResume() {
        super.onResume()
        if (movedAway()) finish()
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        homeDisplayId = display?.displayId ?: Display.INVALID_DISPLAY
        if (homeDisplayId == Display.DEFAULT_DISPLAY) {
            finish() // never show desktop on the phone screen
            return
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            // Scale UI for ultra-wide/high-res external displays (>1920dp, e.g. Honor HDMI 2560x1600 @ 160dpi)
            // to match Full HD desktop proportions; otherwise keep the display's native density.
            val base = LocalDensity.current
            val widthPx = resources.displayMetrics.widthPixels
            val scale = maxOf(base.density, widthPx / 1920f)
            CompositionLocalProvider(LocalDensity provides Density(scale, base.fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Desktop()
                }
            }
        }
        // Stopping the wired session closes the desktop on the external display.
        val startCount = DesktopController.finishRequests.value
        lifecycleScope.launch {
            DesktopController.finishRequests.collect { if (it != startCount) finish() }
        }
    }

    /** Tracks whether another key was pressed while Meta is held (so only a bare Meta tap toggles the drawer). */
    private var metaUsedWithOther = false

    /**
     * Desktop keyboard shortcuts (remaining keys go to the top-most window):
     * - Meta (tap): toggle app drawer
     * - Meta+D: minimize all; Meta+Up: maximize; Meta+Left/Right: snap left/right half
     * - Alt+F4: close window; Alt+Tab (when forwarded by browser): switch window
     * - Esc: close app drawer / overlays
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        val isMetaKey = code == KeyEvent.KEYCODE_META_LEFT || code == KeyEvent.KEYCODE_META_RIGHT
        if (isMetaKey) {
            if (down && event.repeatCount == 0) metaUsedWithOther = false
            if (!down && !metaUsedWithOther) {
                DesktopController.drawerOpen.value = !DesktopController.drawerOpen.value
            }
            return true
        }
        if (DesktopController.lockOpen.value) {
            if (down && (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_SPACE)) {
                DesktopController.lockOpen.value = false
            }
            return true
        }
        val top = DesktopController.topWindow()
        if (event.isMetaPressed && down) {
            metaUsedWithOther = true
            when (code) {
                KeyEvent.KEYCODE_SPACE -> {
                    val next = !DesktopController.spotlightOpen.value
                    DesktopController.closeOverlays()
                    DesktopController.spotlightOpen.value = next
                }
                KeyEvent.KEYCODE_SLASH -> {
                    val next = !DesktopController.shortcutsOpen.value
                    DesktopController.closeOverlays()
                    DesktopController.shortcutsOpen.value = next
                }
                KeyEvent.KEYCODE_V -> if (event.isShiftPressed) {
                    val next = !DesktopController.clipboardOpen.value
                    DesktopController.closeOverlays()
                    DesktopController.clipboardOpen.value = next
                }
                KeyEvent.KEYCODE_L -> DesktopController.lockDesktop()
                KeyEvent.KEYCODE_1 -> DesktopController.switchWorkspace(1)
                KeyEvent.KEYCODE_2 -> DesktopController.switchWorkspace(2)
                KeyEvent.KEYCODE_3 -> DesktopController.switchWorkspace(3)
                KeyEvent.KEYCODE_4 -> DesktopController.switchWorkspace(4)
                KeyEvent.KEYCODE_D -> DesktopController.minimizeAll()
                KeyEvent.KEYCODE_DPAD_UP -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Top) }
                KeyEvent.KEYCODE_DPAD_DOWN -> top?.let { DesktopController.minimize(it.id) }
                KeyEvent.KEYCODE_DPAD_LEFT -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Left) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Right) }
            }
            return true
        }
        if (event.isMetaPressed) return true
        if (down && event.isAltPressed && code == KeyEvent.KEYCODE_F4) {
            top?.let { DesktopController.close(it.id) }
            return true
        }
        if (down && event.isAltPressed && code == KeyEvent.KEYCODE_TAB) {
            DesktopController.cycle()
            return true
        }
        if (DesktopController.drawerOpen.value || DesktopController.spotlightOpen.value ||
            DesktopController.shortcutsOpen.value || DesktopController.settingsOpen.value ||
            DesktopController.clipboardOpen.value || DesktopController.contextMenu.value != null ||
            DesktopController.quickPanelOpen.value
        ) {
            if (down && code == KeyEvent.KEYCODE_ESCAPE) {
                DesktopController.closeOverlays()
                return true
            }
            return super.dispatchKeyEvent(event) // typing into app drawer / search field
        }
        if (top == null) return super.dispatchKeyEvent(event)
        DesktopController.forwardKey(top.id, event)
        return true
    }
}

/**
 * TextureView (instead of SurfaceView, which mis-orders overlapping surfaces) rendering the
 * window's virtual display and forwarding touch/mouse input into it.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
internal fun AppSurface(window: AppWindow) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val current = DesktopController.windows.value.firstOrNull { it.id == window.id } ?: window
                        texture.setDefaultBufferSize(current.width, current.height)
                        surface = Surface(texture).also { DesktopController.attachSurface(window.id, it) }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        DesktopController.detachSurface(window.id)
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }

                val forward = { ev: MotionEvent ->
                    if (ev.actionMasked == MotionEvent.ACTION_DOWN) DesktopController.focus(window.id)
                    // View coordinates -> virtual display coordinates (differ during live resize).
                    val current = DesktopController.windows.value.firstOrNull { it.id == window.id }
                    val copy = MotionEvent.obtain(ev)
                    if (current != null && width > 0 && height > 0) {
                        copy.transform(Matrix().apply { setScale(current.width.toFloat() / width, current.height.toFloat() / height) })
                    }
                    DesktopController.forwardMotion(window.id, copy)
                    copy.recycle()
                    true
                }
                setOnTouchListener { _, ev -> forward(ev) }
                // Mouse: hover and wheel scroll.
                setOnGenericMotionListener { _, ev -> forward(ev) }
            }
        },
        update = { view ->
            // Update buffer size to match the resized virtual display.
            view.surfaceTexture?.setDefaultBufferSize(window.width, window.height)
        },
    )
}
