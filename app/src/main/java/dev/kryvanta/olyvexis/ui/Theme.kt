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
import androidx.compose.ui.graphics.Brush
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
 * Hệ màu Obsidian / Glassmorphic thống nhất cho Olyvexis (lấy cảm hứng từ iDesk).
 */
object DeskColors {
    val Background = Color(0xFF07090E)
    val Surface = Color(0xFF10141D)
    val SurfaceElevated = Color(0xFF161C28)
    val SurfaceInteractive = Color(0xFF1E2636)
    val PanelGlass = Color(0xEB121722)
    val TaskbarGlass = Color(0xEB0B0E15)

    val BorderSubtle = Color(0x1AFFFFFF)
    val BorderMedium = Color(0x2EFFFFFF)
    val BorderAccent = Color(0x663B82F6)

    val Accent = Color(0xFF3B82F6)
    val AccentBright = Color(0xFF60A5FA)
    val AccentContainer = Color(0x2B3B82F6)

    val Success = Color(0xFF22C55E)
    val SuccessContainer = Color(0x2422C55E)
    val Warning = Color(0xFFF59E0B)
    val WarningContainer = Color(0x24F59E0B)
    val Error = Color(0xFFEF4444)
    val ErrorContainer = Color(0x24EF4444)

    val TextPrimary = Color(0xFFF4F6FA)
    val TextSecondary = Color(0xFFA0AAC0)
    val TextMuted = Color(0xFF6B7489)
}

private val OlyvexisColorScheme = darkColorScheme(
    primary = DeskColors.Accent,
    onPrimary = Color.White,
    primaryContainer = DeskColors.AccentContainer,
    onPrimaryContainer = DeskColors.AccentBright,
    secondary = DeskColors.AccentBright,
    onSecondary = Color.White,
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

/** Thẻ nền kính tối có viền mảnh, dùng xuyên suốt màn hình điện thoại. */
@Composable
fun DeskCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = DeskColors.Surface,
    borderColor: Color = DeskColors.BorderSubtle,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Viên trạng thái nhỏ gọn kèm chấm màu. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, color.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Nút hành động chính có dải gradient nhẹ và bo tròn 14dp. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val bg = if (enabled) {
        Brush.horizontalGradient(listOf(DeskColors.Accent, Color(0xFF2563EB)))
    } else {
        Brush.horizontalGradient(listOf(DeskColors.SurfaceInteractive, DeskColors.SurfaceInteractive))
    }
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
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
                    tint = if (enabled) Color.White else DeskColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = if (enabled) Color.White else DeskColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Nút phụ nền tối viền mảnh. */
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

/** Nút tròn kèm nhãn phía dưới giống thanh công cụ trên trackpad của iDesk. */
@Composable
fun ActionIconPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    showLabel: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !enabled -> Color(0x14FFFFFF)
                        active -> DeskColors.Accent
                        else -> Color(0x88000000)
                    },
                )
                .border(
                    1.dp,
                    when {
                        active -> DeskColors.AccentBright.copy(alpha = 0.7f)
                        else -> DeskColors.BorderSubtle
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> DeskColors.TextMuted
                    active -> Color.White
                    else -> DeskColors.TextPrimary.copy(alpha = 0.85f)
                },
                modifier = Modifier.size(19.dp),
            )
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) DeskColors.AccentBright else DeskColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Khung mở/đóng mượt mà cho phần hướng dẫn hoặc chi tiết. */
@Composable
fun AnimatedExpandable(
    expanded: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        content()
    }
}

@Composable
fun DeskTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DeskColors.SurfaceElevated)
                    .border(1.dp, DeskColors.BorderSubtle, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DeskIcons.ArrowBack, contentDescription = "Quay lại", tint = DeskColors.TextPrimary, modifier = Modifier.size(19.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}

@Composable
fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = DeskColors.TextPrimary,
    containerColor: Color = DeskColors.SurfaceElevated,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, DeskColors.BorderSubtle, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}
