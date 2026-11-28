package dev.kryvanta.olyvexis.display

import android.content.ComponentName
import android.view.Surface
import dev.kryvanta.olyvexis.shizuku.ShellResult
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Virtual display for Wi-Fi mode (similar to scrcpy's `--new-display`).
 *
 * Normal apps cannot create a virtual display that hosts other apps, so creation runs inside
 * [ShellService] (shell UID) with `PUBLIC + TRUSTED + OWN_CONTENT_ONLY + OWN_FOCUS`
 * (see `ShellService.virtualDisplayFlags()`).
 *
 * The virtual display is owned by [ShellService]: if that process dies (Shizuku stops or the app
 * is killed), the display is removed along with its hosted apps (`DESTROY_CONTENT_ON_REMOVAL`).
 */
class VirtualDisplayController {
    companion object {
        const val DISPLAY_NAME = "Olyvexis"
    }

    var displayId: Int? = null
        private set

    /**
     * Operations that create/attach/release surfaces must be serialized. On MagicOS, SurfaceView
     * invokes surfaceCreated twice in a row; without locking, the second call sees displayId == null
     * and leaks a duplicate virtual display.
     */
    private val mutex = Mutex()

    /**
     * Attach [surface] to the virtual display: creates one if absent, or swaps the surface if
     * already created. Returns displayId and whether a new display was created.
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
     * Create a virtual display rendering into [surface] (e.g. preview surface or H.264 encoder input).
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

    /** Resize the virtual display (e.g. when the browser viewport or app window resizes). */
    suspend fun resize(width: Int, height: Int, densityDpi: Int) {
        val id = displayId ?: return
        ShizukuManager.withShell { it.resizeVirtualDisplay(id, width, height, densityDpi) }
    }

    /** Swap the target surface; null pauses rendering (e.g. when the app goes to background). */
    suspend fun setSurface(surface: Surface?) = mutex.withLock {
        val id = displayId ?: return@withLock
        ShizukuManager.withShell { it.setVirtualDisplaySurface(id, surface) }
    }

    /**
     * Launch an app on the virtual display. [freeform]: open as a window; enable only when supported
     * ([DeviceCapabilities.freeform]), as some ROMs reject or misbehave with unsupported modes.
     */
    suspend fun launch(component: ComponentName, freeform: Boolean): ShellResult {
        val id = checkNotNull(displayId) { "Chưa tạo màn hình ảo" }
        return ShizukuManager.withShell { it.startActivityOnDisplay(component.flattenToShortString(), id, freeform) }
    }

    /** Launch an app into this virtual display as a window (moving any existing phone task if needed). */
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
