package dev.kryvanta.olyvexis.display

import android.content.ComponentName
import android.view.Surface
import dev.kryvanta.olyvexis.shizuku.ShellResult
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Màn hình ảo cho cách Wi-Fi, giống `--new-display` của scrcpy.
 *
 * App thường không tạo được màn hình ảo chứa được app khác, nên việc tạo chạy trong
 * ShellService (uid shell). Màn hình là PUBLIC + TRUSTED + OWN_CONTENT_ONLY + OWN_FOCUS...,
 * xem ShellService.virtualDisplayFlags().
 *
 * Màn hình ảo thuộc về tiến trình ShellService: nếu tiến trình đó chết (Shizuku dừng, app bị
 * tắt) thì màn hình mất theo, và các app trên đó bị đóng (DESTROY_CONTENT_ON_REMOVAL).
 */
class VirtualDisplayController {
    companion object {
        const val DISPLAY_NAME = "Olyvexis"
    }

    var displayId: Int? = null
        private set

    /**
     * Các thao tác tạo/gắn surface/giải phóng phải chạy lần lượt. Trên MagicOS, SurfaceView gọi
     * surfaceCreated hai lần liền nhau; nếu không khoá, lần thứ hai thấy displayId vẫn null và
     * tạo thêm một màn hình ảo, màn hình đầu bị sót lại.
     */
    private val mutex = Mutex()

    /**
     * Gắn [surface] vào màn hình ảo: tạo mới nếu chưa có, nếu có rồi thì chỉ đổi surface.
     * Trả về displayId và true nếu vừa tạo mới.
     */
    suspend fun attach(width: Int, height: Int, densityDpi: Int, surface: Surface): Pair<Int, Boolean> =
        mutex.withLock {
            val existing = displayId
            if (existing != null) {
                ShizukuManager.withShell { it.setVirtualDisplaySurface(existing, surface) }
                existing to false
            } else {
                createLocked(width, height, densityDpi, surface, systemDecorations = false) to true
            }
        }

    /**
     * Tạo màn hình ảo vẽ vào [surface]: ở mốc 2 là SurfaceView để xem thử, về sau là surface
     * đầu vào của bộ mã hoá H.264.
     */
    suspend fun create(
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface,
        systemDecorations: Boolean = false,
    ): Int = mutex.withLock { createLocked(width, height, densityDpi, surface, systemDecorations) }

    private suspend fun createLocked(
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface,
        systemDecorations: Boolean,
    ): Int {
        check(displayId == null) { "Màn hình ảo đã được tạo" }
        val id = ShizukuManager.withShell {
            it.createVirtualDisplay(DISPLAY_NAME, width, height, densityDpi, surface, systemDecorations)
        }
        displayId = id
        return id
    }

    /** Đổi kích thước màn hình ảo (vd. khi cửa sổ trình duyệt đổi cỡ hoặc thu phóng cửa sổ app). */
    suspend fun resize(width: Int, height: Int, densityDpi: Int) {
        val id = displayId ?: return
        ShizukuManager.withShell { it.resizeVirtualDisplay(id, width, height, densityDpi) }
    }

    /** Đổi surface đích; null = tạm ngừng vẽ (vd. SurfaceView bị huỷ khi app vào nền). */
    suspend fun setSurface(surface: Surface?) = mutex.withLock {
        val id = displayId ?: return@withLock
        ShizukuManager.withShell { it.setVirtualDisplaySurface(id, surface) }
    }

    /**
     * Mở một app lên màn hình ảo. [freeform]: mở dạng cửa sổ; chỉ bật khi máy hỗ trợ
     * ([DeviceCapabilities.freeform]), vì có ROM từ chối hoặc xử lý lạ chế độ không hỗ trợ.
     */
    suspend fun launch(component: ComponentName, freeform: Boolean): ShellResult {
        val id = checkNotNull(displayId) { "Chưa tạo màn hình ảo" }
        return ShizukuManager.withShell { it.startActivityOnDisplay(component.flattenToShortString(), id, freeform) }
    }

    /** Mở app vào màn hình ảo này như một cửa sổ (kéo task sẵn có từ điện thoại sang nếu cần). */
    suspend fun launchInWindow(component: ComponentName): ShellResult {
        val id = checkNotNull(displayId) { "Chưa tạo màn hình ảo" }
        return ShizukuManager.withShell { it.launchAppInWindow(component.flattenToShortString(), id) }
    }

    suspend fun release() = mutex.withLock {
        val id = displayId ?: return@withLock
        displayId = null
        runCatching { ShizukuManager.withShell { it.releaseVirtualDisplay(id) } }
        Unit
    }
}
