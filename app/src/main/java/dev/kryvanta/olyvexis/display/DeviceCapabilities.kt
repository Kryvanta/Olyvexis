package dev.kryvanta.olyvexis.display

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Khả năng của máy ảnh hưởng tới cách mở app lên desktop. */
data class DeviceCapabilities(
    /**
     * Máy có cửa sổ tự do (freeform) của Android không. Không có thì app trên màn hình ảo mở
     * toàn màn hình (vd. MagicOS 11, cả khi bật enable_freeform_support).
     */
    val freeform: Boolean,
    val sdk: Int,
    val manufacturer: String,
) {
    companion object {
        fun detect(context: Context): DeviceCapabilities {
            val feature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
            // Tuỳ chọn nhà phát triển "Bật cửa sổ dạng tự do" (AOSP, Pixel). Có ROM bỏ qua cài đặt này.
            val devSetting = Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0
            return DeviceCapabilities(
                freeform = feature || devSetting,
                sdk = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
            )
        }
    }
}
