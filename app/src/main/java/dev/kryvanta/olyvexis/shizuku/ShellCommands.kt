package dev.kryvanta.olyvexis.shizuku

/** Các lệnh shell dùng qua Shizuku, gom về một chỗ để dễ kiểm tra. */
object ShellCommands {
    /** Bật chế độ desktop bị giấu của Android cho màn hình ngoài (cách qua cáp). */
    const val FORCE_DESKTOP_MODE_ON = "settings put global force_desktop_mode_on_external_displays 1"
    const val ENABLE_FREEFORM = "settings put global enable_freeform_support 1"

    /** Tạo lệnh trả lại giá trị cũ (hoặc xoá nếu trước đó là null) cho một setting global. */
    fun restoreGlobalSetting(setting: String, oldValue: String?): String =
        if (oldValue == null || oldValue == "null" || oldValue.isBlank()) {
            "settings delete global $setting"
        } else {
            "settings put global $setting $oldValue"
        }

    /** Mở một activity trên màn hình chỉ định (dự phòng khi ActivityOptions bị hãng chặn). */
    fun startOnDisplay(component: String, displayId: Int) =
        "am start --display $displayId --windowingMode 5 -n $component"
}
