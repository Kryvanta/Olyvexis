package dev.kryvanta.olyvexis.display

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Device capabilities affecting how apps are launched onto the desktop. */
data class DeviceCapabilities(
    /**
     * Whether Android freeform windows are supported. When false, apps on the virtual display open
     * fullscreen (e.g. MagicOS 11, even with `enable_freeform_support`).
     */
    val freeform: Boolean,
    val sdk: Int,
    val manufacturer: String,
) {
    companion object {
        fun detect(context: Context): DeviceCapabilities {
            val feature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
            // Developer option "Enable freeform windows" (AOSP/Pixel). Some OEM ROMs ignore this.
            val devSetting = Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0
            return DeviceCapabilities(
                freeform = feature || devSetting,
                sdk = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
            )
        }
    }
}
