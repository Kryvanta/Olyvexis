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
 * Detects physical external displays and selects the appropriate [DisplayMode].
 *
 * External display capability can only be confirmed when connected: there is no reliable way to
 * probe USB-C DisplayPort Alt Mode beforehand (e.g. stock Galaxy Z Flip5 disables it, while
 * modified ROMs expose "HDMI Screen" 2560x1600).
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

    /** Re-emits [DisplayMode] whenever an external display connects/disconnects or DeX toggles. */
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
     * External (TYPE_EXTERNAL) or Miracast (TYPE_WIFI) display, ignoring foldable secondary built-in
     * screens, Olyvexis virtual displays, and DeX. Display.getType() is hidden, so invoke via
     * reflection with PRESENTATION flag + name fallback.
     */
    private fun isRealExternal(display: Display): Boolean {
        val type = typeOf(display)
        if (type != null) return type == TYPE_EXTERNAL || type == TYPE_WIFI
        val presentation = display.flags and Display.FLAG_PRESENTATION != 0
        return presentation && !display.name.startsWith(VirtualDisplayController.DISPLAY_NAME) && display.name != "Desktop"
    }

    /** Name of the OEM desktop mode if currently active on an external display, otherwise null. */
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
        // Display.TYPE_* (hidden)
        const val TYPE_EXTERNAL = 2
        const val TYPE_WIFI = 3
        const val TYPE_VIRTUAL = 5
    }
}
