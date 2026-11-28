package dev.kryvanta.olyvexis.desktop

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** Launchable app entry for the desktop app drawer. */
data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    val icon: Drawable?,
)
