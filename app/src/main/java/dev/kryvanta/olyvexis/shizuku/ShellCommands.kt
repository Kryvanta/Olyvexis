package dev.kryvanta.olyvexis.shizuku

/** Centralized shell commands executed via Shizuku. */
object ShellCommands {
    /** Enable Android's hidden desktop mode on external displays (for wired mode). */
    const val FORCE_DESKTOP_MODE_ON = "settings put global force_desktop_mode_on_external_displays 1"
    const val ENABLE_FREEFORM = "settings put global enable_freeform_support 1"

    /** Build a command to restore the previous global setting value (or delete if it was null). */
    fun restoreGlobalSetting(setting: String, oldValue: String?): String =
        if (oldValue == null || oldValue == "null" || oldValue.isBlank()) {
            "settings delete global $setting"
        } else {
            "settings put global $setting $oldValue"
        }

    /** Launch an activity on a target display (fallback when ActivityOptions is blocked by OEM). */
    fun startOnDisplay(component: String, displayId: Int) =
        "am start --display $displayId --windowingMode 5 -n $component"
}
