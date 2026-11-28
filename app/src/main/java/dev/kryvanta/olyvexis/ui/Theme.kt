package dev.kryvanta.olyvexis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pure Dark palette shared by the setup and phone controller.
 */
object DeskColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF101010)
    val SurfaceElevated = Color(0xFF212121)
    val SurfaceInteractive = Color(0xFF292929)
    val PanelGlass = Color(0xEA101010)
    val TaskbarGlass = Color(0xEB000000)

    val BorderSubtle = Color(0x1AFFFFFF)
    val BorderMedium = Color(0x2EFFFFFF)
    val BorderAccent = Color(0x660A84FF)

    val Accent = Color(0xFF0A84FF)
    val OnAccent = Color.Black
    val AccentBright = Color(0xFF409CFF)
    val AccentContainer = Color(0x2B0A84FF)

    val Success = Color(0xFF30D158)
    val SuccessContainer = Color(0x2430D158)
    val Warning = Color(0xFFFF9F0A)
    val WarningContainer = Color(0x24FF9F0A)
    val Error = Color(0xFFFF453A)
    val ErrorContainer = Color(0x24FF453A)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFABABAB)
    val TextMuted = Color(0xFF8E8E8E)
}

private val OlyvexisColorScheme = darkColorScheme(
    primary = DeskColors.Accent,
    onPrimary = DeskColors.OnAccent,
    primaryContainer = DeskColors.AccentContainer,
    onPrimaryContainer = DeskColors.AccentBright,
    secondary = DeskColors.AccentBright,
    onSecondary = DeskColors.OnAccent,
    background = DeskColors.Background,
    onBackground = DeskColors.TextPrimary,
    surface = DeskColors.Surface,
    onSurface = DeskColors.TextPrimary,
    surfaceVariant = DeskColors.SurfaceElevated,
    onSurfaceVariant = DeskColors.TextSecondary,
    outline = DeskColors.BorderMedium,
    outlineVariant = DeskColors.BorderSubtle,
    error = DeskColors.Error,
    onError = Color.White,
)

private val OlyvexisTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = DeskColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = DeskColors.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = DeskColors.TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = DeskColors.TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = DeskColors.TextSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = DeskColors.TextSecondary,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = DeskColors.TextSecondary,
    ),
)

@Composable
fun OlyvexisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OlyvexisColorScheme,
        typography = OlyvexisTypography,
        content = content,
    )
}

/** Primary action button for setup and connection screens. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val bg = if (enabled) DeskColors.Accent else DeskColors.SurfaceInteractive
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 18.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) DeskColors.OnAccent else DeskColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = if (enabled) DeskColors.OnAccent else DeskColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Secondary dark button with subtle border. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = DeskColors.TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) DeskColors.BorderMedium else DeskColors.BorderSubtle),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DeskColors.SurfaceElevated,
            contentColor = tint,
            disabledContainerColor = DeskColors.Surface,
            disabledContentColor = DeskColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
