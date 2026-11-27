package dev.kryvanta.olyvexis.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bộ icon vector sắc nét (24dp viewport) dùng chung cho cả UI điện thoại và desktop,
 * không cần phụ thuộc thêm thư viện ngoài.
 */
object DeskIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private val fill = SolidColor(Color.White)

    val Monitor: ImageVector by lazy {
        icon("Monitor") {
            path(
                fill = null,
                stroke = fill,
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 4f)
                horizontalLineTo(21f)
                arcTo(2f, 2f, 0f, false, true, 23f, 6f)
                verticalLineTo(15f)
                arcTo(2f, 2f, 0f, false, true, 21f, 17f)
                horizontalLineTo(3f)
                arcTo(2f, 2f, 0f, false, true, 1f, 15f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 3f, 4f)
                close()
                moveTo(8f, 21f)
                horizontalLineTo(16f)
                moveTo(12f, 17f)
                verticalLineTo(21f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        icon("Wifi") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(2f, 8.5f)
                quadTo(12f, 0.5f, 22f, 8.5f)
                moveTo(5.5f, 12.2f)
                quadTo(12f, 6.8f, 18.5f, 12.2f)
                moveTo(9f, 15.8f)
                quadTo(12f, 13f, 15f, 15.8f)
            }
            path(fill = fill) {
                moveTo(12f, 19.5f)
                moveToRelative(-1.5f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
            }
        }
    }

    val Usb: ImageVector by lazy {
        icon("Usb") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f)
                verticalLineTo(17f)
                moveTo(9f, 6f)
                lineTo(12f, 3f)
                lineTo(15f, 6f)
                moveTo(12f, 13f)
                lineTo(7f, 10f)
                verticalLineTo(8f)
                moveTo(12f, 11f)
                lineTo(17f, 8.5f)
                verticalLineTo(7f)
                moveTo(10f, 19f)
                horizontalLineTo(14f)
                verticalLineTo(21f)
                horizontalLineTo(10f)
                close()
            }
        }
    }

    val Keyboard: ImageVector by lazy {
        icon("Keyboard") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 6f)
                horizontalLineTo(21f)
                arcTo(2f, 2f, 0f, false, true, 23f, 8f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, true, 21f, 18f)
                horizontalLineTo(3f)
                arcTo(2f, 2f, 0f, false, true, 1f, 16f)
                verticalLineTo(8f)
                arcTo(2f, 2f, 0f, false, true, 3f, 6f)
                close()
                moveTo(5f, 10f); horizontalLineTo(7f)
                moveTo(10f, 10f); horizontalLineTo(12f)
                moveTo(15f, 10f); horizontalLineTo(17f)
                moveTo(19f, 10f); horizontalLineTo(19.5f)
                moveTo(7f, 14f); horizontalLineTo(17f)
            }
        }
    }

    val Touchpad: ImageVector by lazy {
        icon("Touchpad") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 6f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 20f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 18f)
                verticalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 4f)
                close()
                moveTo(2f, 15f); horizontalLineTo(22f)
                moveTo(12f, 15f); verticalLineTo(20f)
            }
        }
    }

    val AppsGrid: ImageVector by lazy {
        icon("AppsGrid") {
            val positions = listOf(5f, 12f, 19f)
            for (y in positions) {
                for (x in positions) {
                    path(fill = fill) {
                        moveTo(x, y)
                        moveToRelative(-1.6f, 0f)
                        arcToRelative(1.6f, 1.6f, 0f, true, true, 3.2f, 0f)
                        arcToRelative(1.6f, 1.6f, 0f, true, true, -3.2f, 0f)
                    }
                }
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(11f, 11f)
                moveToRelative(-6.5f, 0f)
                arcToRelative(6.5f, 6.5f, 0f, true, true, 13f, 0f)
                arcToRelative(6.5f, 6.5f, 0f, true, true, -13f, 0f)
                moveTo(16f, 16f)
                lineTo(21f, 21f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 12f)
                moveToRelative(-3f, 0f)
                arcToRelative(3f, 3f, 0f, true, true, 6f, 0f)
                arcToRelative(3f, 3f, 0f, true, true, -6f, 0f)
                moveTo(19.4f, 15f)
                lineTo(21f, 16.2f); lineTo(19f, 19.7f); lineTo(17.1f, 18.9f)
                lineTo(14.5f, 20.4f); lineTo(14f, 22.3f); lineTo(10f, 22.3f); lineTo(9.5f, 20.4f)
                lineTo(6.9f, 18.9f); lineTo(5f, 19.7f); lineTo(3f, 16.2f); lineTo(4.6f, 15f)
                lineTo(4.6f, 9f); lineTo(3f, 7.8f); lineTo(5f, 4.3f); lineTo(6.9f, 5.1f)
                lineTo(9.5f, 3.6f); lineTo(10f, 1.7f); lineTo(14f, 1.7f); lineTo(14.5f, 3.6f)
                lineTo(17.1f, 5.1f); lineTo(19f, 4.3f); lineTo(21f, 7.8f); lineTo(19.4f, 9f)
                close()
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        icon("CheckCircle") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 12f)
                moveToRelative(-9.5f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, 19f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, -19f, 0f)
                moveTo(8f, 12.2f); lineTo(10.8f, 15f); lineTo(16.2f, 9.2f)
            }
        }
    }

    val Warning: ImageVector by lazy {
        icon("Warning") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(22f, 20f); lineTo(2f, 20f); close()
                moveTo(12f, 9.5f); verticalLineTo(14f)
                moveTo(12f, 17f); verticalLineTo(17.2f)
            }
        }
    }

    val Info: ImageVector by lazy {
        icon("Info") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 12f)
                moveToRelative(-9.5f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, 19f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, -19f, 0f)
                moveTo(12f, 11f); verticalLineTo(16.5f)
                moveTo(12f, 7.5f); verticalLineTo(7.7f)
            }
        }
    }

    val Checklist: ImageVector by lazy {
        icon("Checklist") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 7f); lineTo(6f, 9f); lineTo(9.5f, 5f)
                moveTo(12f, 7f); horizontalLineTo(20f)
                moveTo(4f, 13f); lineTo(6f, 15f); lineTo(9.5f, 11f)
                moveTo(12f, 13f); horizontalLineTo(20f)
                moveTo(12f, 19f); horizontalLineTo(20f)
                moveTo(5f, 19f); horizontalLineTo(8f)
            }
        }
    }

    val Help: ImageVector by lazy {
        icon("Help") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 12f)
                moveToRelative(-9.5f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, 19f, 0f)
                arcToRelative(9.5f, 9.5f, 0f, true, true, -19f, 0f)
                moveTo(9.3f, 9.2f)
                arcTo(2.7f, 2.7f, 0f, true, true, 14.2f, 11f)
                curveTo(13.5f, 12f, 12f, 12.5f, 12f, 14.2f)
                moveTo(12f, 17.2f); verticalLineTo(17.4f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            path(fill = null, stroke = fill, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 9f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(9f); close()
                moveTo(5f, 15f); horizontalLineTo(4f); verticalLineTo(4f); horizontalLineTo(15f); verticalLineTo(5f)
            }
        }
    }

    val Paste: ImageVector by lazy {
        icon("Paste") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(16f, 4f); horizontalLineTo(19f); verticalLineTo(20f); horizontalLineTo(5f); verticalLineTo(4f); horizontalLineTo(8f)
                moveTo(9f, 2f); horizontalLineTo(15f); verticalLineTo(6f); horizontalLineTo(9f); close()
            }
        }
    }

    val Send: ImageVector by lazy {
        icon("Send") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(22f, 2f); lineTo(11f, 13f)
                moveTo(22f, 2f); lineTo(15f, 22f); lineTo(11f, 13f); lineTo(2f, 9f); close()
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            path(fill = fill) {
                moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            path(fill = fill) {
                moveTo(6f, 6f); horizontalLineTo(18f); verticalLineTo(18f); horizontalLineTo(6f); close()
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(20f, 11f)
                arcTo(8f, 8f, 0f, true, false, 18f, 17.5f)
                moveTo(20f, 5f); verticalLineTo(11f); horizontalLineTo(14f)
            }
        }
    }

    val Pin: ImageVector by lazy {
        icon("Pin") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 3f); lineTo(21f, 9f)
                moveTo(16f, 4f); lineTo(9f, 11f); lineTo(5f, 12f); lineTo(12f, 19f); lineTo(13f, 15f); lineTo(20f, 8f)
                moveTo(8.5f, 15.5f); lineTo(3f, 21f)
            }
        }
    }

    val NightLight: ImageVector by lazy {
        icon("NightLight") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(20f, 14.5f)
                arcTo(8.5f, 8.5f, 0f, true, true, 9.5f, 4f)
                arcTo(6.8f, 6.8f, 0f, false, false, 20f, 14.5f)
                close()
            }
        }
    }

    val Wallpaper: ImageVector by lazy {
        icon("Wallpaper") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 4f); horizontalLineTo(21f); verticalLineTo(20f); horizontalLineTo(3f); close()
                moveTo(3f, 16f); lineTo(8.5f, 10.5f); lineTo(13.5f, 15f); lineTo(16.5f, 12f); lineTo(21f, 16f)
            }
        }
    }

    val Brightness: ImageVector by lazy {
        icon("Brightness") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
                moveTo(12f, 12f)
                moveToRelative(-4f, 0f)
                arcToRelative(4f, 4f, 0f, true, true, 8f, 0f)
                arcToRelative(4f, 4f, 0f, true, true, -8f, 0f)
                moveTo(12f, 2f); verticalLineTo(4f)
                moveTo(12f, 20f); verticalLineTo(22f)
                moveTo(2f, 12f); horizontalLineTo(4f)
                moveTo(20f, 12f); horizontalLineTo(22f)
                moveTo(4.9f, 4.9f); lineTo(6.3f, 6.3f)
                moveTo(17.7f, 17.7f); lineTo(19.1f, 19.1f)
                moveTo(19.1f, 4.9f); lineTo(17.7f, 6.3f)
                moveTo(6.3f, 17.7f); lineTo(4.9f, 19.1f)
            }
        }
    }

    val Home: ImageVector by lazy {
        icon("Home") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
                moveTo(5f, 9.5f); verticalLineTo(20f); horizontalLineTo(19f); verticalLineTo(9.5f)
            }
        }
    }

    val Recents: ImageVector by lazy {
        icon("Recents") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 7f); horizontalLineTo(16f); verticalLineTo(19f); horizontalLineTo(4f); close()
                moveTo(8f, 4f); horizontalLineTo(20f); verticalLineTo(16f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    val Minimize: ImageVector by lazy {
        icon("Minimize") {
            path(fill = null, stroke = fill, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                moveTo(5f, 15f); horizontalLineTo(19f)
            }
        }
    }

    val SwapHoriz: ImageVector by lazy {
        icon("SwapHoriz") {
            path(fill = null, stroke = fill, strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 9f); horizontalLineTo(20f); moveTo(16f, 5f); lineTo(20f, 9f); lineTo(16f, 13f)
                moveTo(20f, 16f); horizontalLineTo(4f); moveTo(8f, 12f); lineTo(4f, 16f); lineTo(8f, 20f)
            }
        }
    }
}
