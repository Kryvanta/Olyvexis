package dev.kryvanta.olyvexis.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Nhận biết máy có màn hình ngoài thật không và chọn [DisplayMode].
 *
 * Chỉ biết chắc khi màn hình đã cắm vào: không có cách tin cậy để biết trước cổng USB-C có
 * xuất hình (DisplayPort Alt Mode) hay không. Vd. Galaxy Z Flip5 ROM gốc không xuất hình,
 * ROM mod thì có ("Màn hình HDMI" 2560x1600).
 */
class DisplayDetector(context: Context) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun currentMode(): DisplayMode {
        val displays = displayManager.displays
        val external = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && isRealExternal(it) }
            ?: return DisplayMode.VirtualOverWifi
        @Suppress("DEPRECATION")
        val size = android.graphics.Point().also { external.getRealSize(it) }
        return DisplayMode.External(
            displayId = external.displayId,
            name = external.name,
            width = size.x,
            height = size.y,
            vendorDesktop = vendorDesktop(displays),
        )
    }

    /** Phát lại [DisplayMode] mỗi khi cắm/rút màn hình hoặc DeX bật/tắt. */
    fun modes(): Flow<DisplayMode> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { trySend(currentMode()) }
            override fun onDisplayRemoved(displayId: Int) { trySend(currentMode()) }
            override fun onDisplayChanged(displayId: Int) { trySend(currentMode()) }
        }
        trySend(currentMode())
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { displayManager.unregisterDisplayListener(listener) }
    }

    /**
     * Màn hình cắm ngoài (TYPE_EXTERNAL) hoặc Miracast (TYPE_WIFI). Bỏ qua màn hình tích hợp thứ
     * hai của máy gập, màn hình ảo của Olyvexis và của DeX. Display.getType() bị ẩn nên gọi qua
     * reflection; nếu không gọi được thì dựa vào cờ PRESENTATION và tên.
     */
    private fun isRealExternal(display: Display): Boolean {
        val type = typeOf(display)
        if (type != null) return type == TYPE_EXTERNAL || type == TYPE_WIFI
        val presentation = display.flags and Display.FLAG_PRESENTATION != 0
        return presentation && !display.name.startsWith(VirtualDisplayController.DISPLAY_NAME) && display.name != "Desktop"
    }

    /** Tên chế độ desktop của hãng nếu nó đang chạy (đang giữ màn hình ngoài), không thì null. */
    private fun vendorDesktop(displays: Array<Display>): String? {
        for (d in displays) {
            if (typeOf(d) != TYPE_VIRTUAL || d.state != Display.STATE_ON) continue
            val name = d.name.lowercase()
            return when {
                d.name == "Desktop" -> "Samsung DeX"
                name.contains("ready for") || name.contains("smart connect") -> "Motorola Ready For"
                name.contains("hndesktop") || name.contains("pc mode") -> "Honor / Huawei PC mode"
                name.contains("hyperconnect") || name.contains("miui_desktop") -> "Xiaomi HyperConnect"
                else -> continue
            }
        }
        return null
    }

    private fun typeOf(display: Display): Int? =
        runCatching { Display::class.java.getMethod("getType").invoke(display) as Int }.getOrNull()

    private companion object {
        // Display.TYPE_* (ẩn)
        const val TYPE_EXTERNAL = 2
        const val TYPE_WIFI = 3
        const val TYPE_VIRTUAL = 5
    }
}
