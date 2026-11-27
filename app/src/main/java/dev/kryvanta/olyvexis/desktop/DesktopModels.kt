package dev.kryvanta.olyvexis.desktop

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** Một app có thể mở từ menu ứng dụng. */
data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    val icon: Drawable?,
)

/** Một cửa sổ đang mở trên màn hình lớn (hiện ở thanh tác vụ). */
data class DesktopWindow(
    val taskId: Int,
    val label: String,
    val component: ComponentName?,
    val isFocused: Boolean,
)
