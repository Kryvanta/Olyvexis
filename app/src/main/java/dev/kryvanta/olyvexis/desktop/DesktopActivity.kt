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
 * Giao diện desktop trên màn hình lớn (màn hình ảo của phiên Wi-Fi; về sau cả màn hình ngoài).
 *
 * Giao diện kiểu Samsung DeX (xem DexUi.kt): hình nền có icon lối tắt, cửa sổ app
 * ([DesktopController]), thanh tác vụ có icon app và khay hệ thống, ngăn ứng dụng dạng lưới.
 * Activity này được mở lên màn hình desktop bằng ShellService, nên phải exported.
 */
class DesktopActivity : ComponentActivity() {
    /** Màn hình desktop này được mở lên. */
    private var homeDisplayId = Display.INVALID_DISPLAY

    /**
     * Rút cáp HDMI: Android chuyển activity của màn hình vừa mất về màn hình điện thoại. Desktop
     * không bao giờ được hiện trên điện thoại, nên tự đóng khi màn hình của nó bị gỡ hoặc khi bị
     * chuyển sang màn hình khác.
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
            finish() // không mở desktop trên màn hình điện thoại
            return
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        setContent {
            // Phóng giao diện theo màn hình: màn rộng hơn 1920 dp (vd. Honor HDMI 2560x1600 @ 160
            // dpi) thì phóng lên cho bằng desktop Full HD, giống DeX; không thì giữ mật độ của màn hình.
            val base = LocalDensity.current
            val widthPx = resources.displayMetrics.widthPixels
            val scale = maxOf(base.density, widthPx / 1920f)
            CompositionLocalProvider(LocalDensity provides Density(scale, base.fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Desktop()
                }
            }
        }
        // Dừng phiên qua cáp thì đóng desktop trên màn hình ngoài.
        val startCount = DesktopController.finishRequests.value
        lifecycleScope.launch {
            DesktopController.finishRequests.collect { if (it != startCount) finish() }
        }
    }

    /** Có phím nào khác được nhấn trong lúc giữ Meta không (để Meta nhấn-nhả riêng mới mở ngăn). */
    private var metaUsedWithOther = false

    /**
     * Phím tắt kiểu Android/DeX, còn lại đi vào cửa sổ trên cùng:
     * - Meta (nhấn rồi nhả): mở/đóng ngăn ứng dụng
     * - Meta+D: thu nhỏ tất cả; Meta+↑: phóng to; Meta+←/→: bám nửa trái/phải
     * - Alt+F4: đóng cửa sổ; Alt+Tab (nếu trình duyệt cho qua): chuyển cửa sổ
     * - Esc: đóng ngăn ứng dụng / menu
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
        val top = DesktopController.topWindow()
        if (event.isMetaPressed && down) {
            metaUsedWithOther = true
            when (code) {
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
        if (DesktopController.drawerOpen.value || DesktopController.contextMenu.value != null || DesktopController.quickPanelOpen.value) {
            if (down && code == KeyEvent.KEYCODE_ESCAPE) {
                DesktopController.drawerOpen.value = false
                DesktopController.contextMenu.value = null
                DesktopController.quickPanelOpen.value = false
                return true
            }
            return super.dispatchKeyEvent(event) // gõ vào ô tìm kiếm của ngăn ứng dụng
        }
        if (top == null) return super.dispatchKeyEvent(event)
        DesktopController.forwardKey(top.id, event)
        return true
    }
}

/**
 * TextureView (không dùng SurfaceView: nhiều SurfaceView chồng nhau thì thứ tự vẽ không đúng)
 * nhận hình từ màn hình ảo của cửa sổ, và chuyển chạm/chuột vào màn hình đó.
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
                    // Toạ độ trong view -> toạ độ trên màn hình ảo (khác nhau khi đang kéo đổi cỡ).
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
                // Chuột: di chuột (hover), cuộn bánh xe.
                setOnGenericMotionListener { _, ev -> forward(ev) }
            }
        },
        update = { view ->
            // Sau khi đổi kích thước xong, buffer phải theo kích thước mới của màn hình ảo.
            view.surfaceTexture?.setDefaultBufferSize(window.width, window.height)
        },
    )
}
