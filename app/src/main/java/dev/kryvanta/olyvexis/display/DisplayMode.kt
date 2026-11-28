package dev.kryvanta.olyvexis.display

/** Display output mode selected by the app. */
sealed interface DisplayMode {
    /**
     * Physical external display (HDMI/DisplayPort over USB-C, or Miracast): open desktop there.
     * [vendorDesktop]: OEM desktop mode (e.g. Samsung DeX) is running and holding the external display.
     */
    data class External(
        val displayId: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val vendorDesktop: String? = null,
    ) : DisplayMode

    /** No external display: create a virtual display and stream over Wi-Fi. */
    data object VirtualOverWifi : DisplayMode
}
