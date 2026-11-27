package dev.kryvanta.olyvexis.display

/** Cách xuất hình mà app chọn. */
sealed interface DisplayMode {
    /**
     * Có màn hình ngoài thật (HDMI/DisplayPort qua USB-C, hoặc Miracast): mở desktop lên đó.
     * [vendorDesktop]: chế độ desktop của hãng (Samsung DeX...) đang chạy và giữ màn hình ngoài.
     */
    data class External(
        val displayId: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val vendorDesktop: String? = null,
    ) : DisplayMode

    /** Không có màn hình ngoài: tạo màn hình ảo và stream qua Wi-Fi. */
    data object VirtualOverWifi : DisplayMode
}
