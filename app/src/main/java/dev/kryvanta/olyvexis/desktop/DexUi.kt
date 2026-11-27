package dev.kryvanta.olyvexis.desktop

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.ui.DeskColors
import dev.kryvanta.olyvexis.ui.DeskIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private object DexTheme {
    // Theo iDesk: kính mờ tối, nền cửa sổ xám 10%, thanh tiêu đề xám 20% (đang chọn) / 15%.
    val taskbar = Color(0xD91C1C1E)
    val panelGlass = Color(0xF22C2C2E)
    val panelElevated = Color(0xFF3A3A3C)
    val titleActive = Color(0xFF333333)
    val titleInactive = Color(0xFF262626)
    val windowBackground = Color(0xFF1A1A1A)
    val borderLight = Color(0x21FFFFFF)
    val borderAccent = Color(0xB30A84FF)
    val accent = Color(0xFF0A84FF)
    val accentBright = Color(0xFF409CFF)
    val text = Color(0xFFFFFFFF)
    val textDim = Color(0x99EBEBF5)
    val close = Color(0xFFFF5F57)
    val minimize = Color(0xFFFEBC2E)
    val maximize = Color(0xFF28C840)
    val dotInactive = Color(0xFF595959)
}

/** Hình nền: 5 bộ gradient chéo như iDesk (chỉ số -1 là hình nền động theo giờ). */
internal val WALLPAPERS = listOf(
    listOf(Color(0.06f, 0.10f, 0.22f), Color(0.18f, 0.10f, 0.30f), Color(0.05f, 0.22f, 0.30f)),
    listOf(Color(0.95f, 0.45f, 0.30f), Color(0.55f, 0.18f, 0.45f), Color(0.15f, 0.10f, 0.35f)),
    listOf(Color(0.05f, 0.25f, 0.20f), Color(0.10f, 0.45f, 0.40f), Color(0.02f, 0.12f, 0.18f)),
    listOf(Color(0.20f, 0.45f, 0.85f), Color(0.45f, 0.30f, 0.85f), Color(0.10f, 0.20f, 0.50f)),
    listOf(Color(0.12f, 0.12f, 0.14f), Color(0.22f, 0.22f, 0.26f), Color(0.08f, 0.08f, 0.10f)),
)

private fun dynamicWallpaperColors(): List<Color> {
    val c = Calendar.getInstance()
    val h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
    val keys = listOf(
        0f to listOf(Color(0xFF06091A), Color(0xFF11122E), Color(0xFF071524)),
        6f to listOf(Color(0xFF21163A), Color(0xFF5D2D5B), Color(0xFF1B2B4B)),
        8f to listOf(Color(0xFFD66A4A), Color(0xFF6C467A), Color(0xFF1E3C6E)),
        11f to listOf(Color(0xFF1A4E8A), Color(0xFF2D6FA8), Color(0xFF112C54)),
        17f to listOf(Color(0xFFC95B3C), Color(0xFF7B3156), Color(0xFF221A40)),
        20f to listOf(Color(0xFF121330), Color(0xFF221A45), Color(0xFF0A1429)),
        24f to listOf(Color(0xFF06091A), Color(0xFF11122E), Color(0xFF071524)),
    )
    var i = 0
    while (i < keys.size - 2 && h >= keys[i + 1].first) i++
    val (t0, a) = keys[i]
    val (t1, b) = keys[i + 1]
    val k = ((h - t0) / (t1 - t0).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
    return (0..2).map { j ->
        Color(
            red = a[j].red + (b[j].red - a[j].red) * k,
            green = a[j].green + (b[j].green - a[j].green) * k,
            blue = a[j].blue + (b[j].blue - a[j].blue) * k,
        )
    }
}

@Composable
internal fun Desktop() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DesktopController.initPrefs(context) }

    val windows by DesktopController.windows.collectAsState()
    val drawerOpen by DesktopController.drawerOpen.collectAsState()
    val quickPanelOpen by DesktopController.quickPanelOpen.collectAsState()
    val switcherActive by DesktopController.switcherActive.collectAsState()
    val menu by DesktopController.contextMenu.collectAsState()
    val snapPreview by DesktopController.snapPreview.collectAsState()
    val wallpaper by DesktopController.wallpaper.collectAsState()
    val nightLight by DesktopController.nightLight.collectAsState()
    val brightness by DesktopController.brightness.collectAsState()
    val recentPkgs by DesktopController.recentPackages.collectAsState()
    val pinsVersion by DesktopController.pinsVersion.collectAsState()
    val toast by DesktopController.toastMessage.collectAsState()
    val cursor by DesktopController.cursor.collectAsState()

    val repository = remember { AppRepository(context) }
    val apps by remember { repository.observeLaunchableApps() }.collectAsState(initial = remember { repository.launchableApps() })
    val pinned = remember(apps, pinsVersion) { repository.pinnedApps(apps) }

    val screenDensity = LocalDensity.current.density
    SideEffect { DesktopController.density = screenDensity }

    var dynamicColors by remember { mutableStateOf(dynamicWallpaperColors()) }
    LaunchedEffect(wallpaper) {
        if (wallpaper < 0) {
            while (true) {
                dynamicColors = dynamicWallpaperColors()
                delay(60_000)
            }
        }
    }

    val bgColors = if (wallpaper < 0) dynamicColors else WALLPAPERS[wallpaper % WALLPAPERS.size]

    fun togglePin(pkg: String, label: String) {
        val wasPinned = repository.isPinned(pkg)
        repository.togglePin(pkg, apps)
        DesktopController.pinsVersion.value += 1
        DesktopController.showToast(if (wasPinned) "Đã bỏ ghim $label" else "Đã ghim $label lên Desktop & Thanh tác vụ")
    }

    BackHandler {
        DesktopController.drawerOpen.value = false
        DesktopController.quickPanelOpen.value = false
        DesktopController.contextMenu.value = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(bgColors, start = Offset.Zero, end = Offset(2200f, 1200f)))
            .onSizeChanged {
                DesktopController.desktopWidth = it.width
                DesktopController.desktopHeight = it.height
            }
            .pointerInput(wallpaper) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press) {
                            val pos = e.changes.first().position
                            if (e.buttons.isSecondaryPressed) {
                                DesktopController.quickPanelOpen.value = false
                                DesktopController.contextMenu.value = DesktopContextMenu(
                                    x = pos.x.toInt(),
                                    y = pos.y.toInt(),
                                    items = listOf(
                                        context.getString(R.string.desk_menu_apps) to { DesktopController.drawerOpen.value = true },
                                        context.getString(R.string.desk_menu_next_wallpaper) to {
                                            val next = if (wallpaper < 0) 0 else (wallpaper + 1) % WALLPAPERS.size
                                            DesktopController.setWallpaper(next)
                                        },
                                        context.getString(R.string.desk_menu_dynamic_wallpaper) to { DesktopController.setWallpaper(-1) },
                                        if (DesktopController.nightLight.value) context.getString(R.string.desk_menu_night_off) to { DesktopController.toggleNightLight() }
                                        else context.getString(R.string.desk_menu_night_on) to { DesktopController.toggleNightLight() },
                                        context.getString(R.string.desk_menu_minimize_all) to { DesktopController.minimizeAll() },
                                        context.getString(R.string.desk_menu_close_all) to { DesktopController.closeAll() },
                                    ),
                                )
                            } else {
                                DesktopController.contextMenu.value = null
                                DesktopController.quickPanelOpen.value = false
                            }
                        }
                    }
                }
            },
    ) {
        // Ánh sáng ambient + watermark tinh tế kiểu iDesk
        Box(
            Modifier.fillMaxSize().padding(bottom = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Olyvexis",
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = Color(0x0DFFFFFF),
                letterSpacing = (-1.5).sp,
            )
        }

        // Icon lối tắt ngoài màn hình nền (chia cột tự động)
        DesktopShortcuts(
            apps = pinned,
            onOpen = { DesktopController.open(it) },
            onContextMenu = { app, gx, gy ->
                DesktopController.contextMenu.value = DesktopContextMenu(
                    x = gx, y = gy,
                    items = listOf(
                        context.getString(R.string.desk_open_app, app.label) to { DesktopController.open(app) },
                        context.getString(R.string.desk_unpin_desktop) to { togglePin(app.component.packageName, app.label) },
                    ),
                )
            },
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, bottom = 68.dp),
        )

        // Xem trước vùng bám mép khi kéo cửa sổ (giống iDesk snapPreview)
        snapPreview?.let { SnapPreviewOverlay(it) }

        // Các cửa sổ sắp xếp theo ghim trên cùng -> thứ tự Z
        windows
            .filterNot { it.minimized }
            .sortedWith(compareBy<AppWindow> { it.keepOnTop }.thenBy { it.z })
            .forEach { window ->
                key(window.id) { WindowFrame(window) }
            }

        // Ngăn ứng dụng nổi
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(150)) + scaleIn(tween(160), initialScale = 0.96f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f),
        ) {
            AppDrawer(
                apps = apps,
                recentPackages = recentPkgs,
                isPinned = { repository.isPinned(it) },
                onLaunch = {
                    DesktopController.drawerOpen.value = false
                    DesktopController.open(it)
                },
                onTogglePin = { togglePin(it.component.packageName, it.label) },
                onDismiss = { DesktopController.drawerOpen.value = false },
            )
        }

        // Bảng cài đặt nhanh & cửa sổ đang mở ở góc phải
        AnimatedVisibility(
            visible = quickPanelOpen,
            enter = fadeIn(tween(140)) + scaleIn(tween(150), initialScale = 0.95f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = with(LocalDensity.current) { DesktopController.TASKBAR.toDp() } + 10.dp),
        ) {
            QuickPanel(
                windows = windows,
                nightLight = nightLight,
                wallpaper = wallpaper,
                brightness = brightness,
                onToggleNightLight = { DesktopController.toggleNightLight() },
                onCycleWallpaper = {
                    val next = if (wallpaper < 0) 0 else (wallpaper + 1) % WALLPAPERS.size
                    DesktopController.setWallpaper(next)
                },
                onDynamicWallpaper = { DesktopController.setWallpaper(-1) },
                onMinimizeAll = { DesktopController.minimizeAll() },
                onBrightnessChange = { DesktopController.setBrightness(it) },
                onFocusWindow = {
                    DesktopController.focus(it)
                    DesktopController.quickPanelOpen.value = false
                },
                onCloseWindow = { DesktopController.close(it) },
            )
        }

        // Thanh tác vụ đáy màn hình
        Taskbar(
            pinned = pinned,
            windows = windows,
            drawerOpen = drawerOpen,
            quickPanelOpen = quickPanelOpen,
            onApps = {
                DesktopController.quickPanelOpen.value = false
                DesktopController.drawerOpen.value = !drawerOpen
            },
            onCycleWindows = { DesktopController.cycle() },
            onShowDesktop = { DesktopController.minimizeAll() },
            onToggleQuickPanel = {
                DesktopController.drawerOpen.value = false
                DesktopController.quickPanelOpen.value = !quickPanelOpen
            },
            onItemContextMenu = { app, w, gx, gy ->
                val pkg = app.component.packageName
                val pinnedNow = repository.isPinned(pkg)
                val items = buildList {
                    if (w != null) {
                        add(context.getString(if (w.minimized) R.string.desk_win_show else R.string.desk_win_focus) to { DesktopController.focus(w.id) })
                        if (!w.minimized) add(context.getString(R.string.desk_win_minimize) to { DesktopController.minimize(w.id) })
                        add(context.getString(R.string.desk_win_snap_left) to { DesktopController.snap(w.id, DesktopController.Snap.Left) })
                        add(context.getString(R.string.desk_win_snap_right) to { DesktopController.snap(w.id, DesktopController.Snap.Right) })
                    } else {
                        add(context.getString(R.string.desk_open_app, app.label) to { DesktopController.open(app) })
                    }
                    add(context.getString(if (pinnedNow) R.string.desk_unpin_taskbar else R.string.desk_pin_taskbar) to { togglePin(pkg, app.label) })
                    if (w != null) add(context.getString(R.string.desk_win_close) to { DesktopController.close(w.id) })
                }
                DesktopController.contextMenu.value = DesktopContextMenu(gx, gy, items)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Thanh chuyển cửa sổ Alt+Tab
        AnimatedVisibility(
            visible = switcherActive && windows.isNotEmpty(),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            WindowSwitcher(windows = windows, topId = DesktopController.topWindow()?.id)
        }

        // Menu chuột phải
        menu?.let { m -> DesktopMenu(m.x, m.y, m.items) }

        // Toast thông báo ngắn
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
        ) {
            toast?.let { msg ->
                Box(
                    Modifier
                        .shadow(14.dp, CircleShape)
                        .clip(CircleShape)
                        .background(DexTheme.panelGlass)
                        .border(1.dp, DexTheme.borderLight, CircleShape)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(msg, color = DexTheme.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Bộ lọc Ánh sáng đêm (Night Light) + Điều chỉnh độ sáng màn hình
        if (nightLight) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x38FF9D3B)),
            )
        }
        if (brightness < 0.99f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.7f))),
            )
        }

        // Con trỏ chuột khi dùng trackpad điện thoại / cáp
        cursor?.let { (cx, cy) -> MouseCursor(cx, cy) }
    }
}

@Composable
private fun SnapPreviewOverlay(snap: DesktopController.Snap) {
    val density = LocalDensity.current
    val w = DesktopController.desktopWidth
    val h = DesktopController.desktopHeight - DesktopController.TASKBAR
    val (xPx, widthPx) = when (snap) {
        DesktopController.Snap.Left -> 8 to (w / 2 - 12)
        DesktopController.Snap.Right -> (w / 2 + 4) to (w / 2 - 12)
        DesktopController.Snap.Top -> 8 to (w - 16)
    }
    val heightPx = (h - 16).coerceAtLeast(100)
    Box(
        Modifier
            .offset { IntOffset(xPx, 8) }
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x283B82F6))
            .border(2.dp, Color(0x9960A5FA), RoundedCornerShape(14.dp)),
    )
}

@Composable
private fun MouseCursor(x: Float, y: Float) {
    Canvas(
        Modifier
            .offset { IntOffset(x.toInt(), y.toInt()) }
            .size(18.dp, 24.dp),
    ) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, size.height * 0.85f)
            lineTo(size.width * 0.28f, size.height * 0.64f)
            lineTo(size.width * 0.48f, size.height)
            lineTo(size.width * 0.62f, size.height * 0.94f)
            lineTo(size.width * 0.44f, size.height * 0.6f)
            lineTo(size.width * 0.8f, size.height * 0.6f)
            close()
        }
        drawPath(path, Color.White)
        drawPath(path, Color.Black, style = Stroke(1.1.dp.toPx()))
    }
}

@Composable
private fun DesktopMenu(x: Int, y: Int, items: List<Pair<String, () -> Unit>>) {
    val density = LocalDensity.current
    val menuW = with(density) { 250.dp.roundToPx() }
    val menuH = with(density) { (items.size * 32 + 12).dp.roundToPx() }
    val maxX = (DesktopController.desktopWidth - menuW - 8).coerceAtLeast(0)
    val maxY = (DesktopController.desktopHeight - DesktopController.TASKBAR - menuH - 8).coerceAtLeast(0)
    Column(
        Modifier
            .offset { IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY)) }
            .width(250.dp.coerceAtMost(with(density) { DesktopController.desktopWidth.toDp() }))
            .shadow(16.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(DexTheme.panelGlass)
            .border(1.dp, DexTheme.borderLight, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        items.forEach { (label, action) ->
            HoverRow(onClick = {
                DesktopController.contextMenu.value = null
                action()
            }) {
                Box(Modifier.height(32.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text(label, color = DexTheme.text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HoverRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) DexTheme.accent.copy(alpha = 0.6f) else Color.Transparent)
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) { content() }
}

// ---------------------------------------------------------------- icon app

@Composable
private fun AppIcon(app: LaunchableApp, size: Dp, modifier: Modifier = Modifier) {
    val bmp = rememberAppIcon(app)
    if (bmp != null) {
        Image(
            bmp,
            contentDescription = app.label,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f)),
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f))
                .background(Color(0x28FFFFFF)),
        )
    }
}

// ---------------------------------------------------------------- màn hình nền

@Composable
private fun DesktopShortcuts(
    apps: List<LaunchableApp>,
    onOpen: (LaunchableApp) -> Unit,
    onContextMenu: (LaunchableApp, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val rows = (maxHeight.value / 96f).toInt().coerceAtLeast(1)
        val columns = remember(apps, rows) { apps.chunked(rows) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            columns.forEachIndexed { colIndex, colApps ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colApps.forEachIndexed { rowIndex, app ->
                        val source = remember { MutableInteractionSource() }
                        val hovered by source.collectIsHoveredAsState()
                        Column(
                            Modifier
                                .width(86.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (hovered) Color(0x29FFFFFF) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (hovered) Color(0x2EFFFFFF) else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .hoverable(source)
                                .pointerInput(app.component) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val e = awaitPointerEvent()
                                            if (e.type == PointerEventType.Press) {
                                                if (e.buttons.isSecondaryPressed) {
                                                    val p = e.changes.first().position
                                                    onContextMenu(app, 20 + colIndex * 94 + p.x.toInt(), 20 + rowIndex * 96 + p.y.toInt())
                                                } else {
                                                    onOpen(app)
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, 46.dp, Modifier.shadow(6.dp, RoundedCornerShape(11.dp)))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                app.label,
                                color = DexTheme.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1.5f), 4f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- thanh tác vụ

@Composable
private fun Taskbar(
    pinned: List<LaunchableApp>,
    windows: List<AppWindow>,
    drawerOpen: Boolean,
    quickPanelOpen: Boolean,
    onApps: () -> Unit,
    onCycleWindows: () -> Unit,
    onShowDesktop: () -> Unit,
    onToggleQuickPanel: () -> Unit,
    onItemContextMenu: (LaunchableApp, AppWindow?, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val top = DesktopController.topWindow()
    val pinnedPackages = pinned.map { it.component.packageName }.toSet()
    val running = windows.map { it.app }.distinctBy { it.component.packageName }
        .filterNot { it.component.packageName in pinnedPackages }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DexTheme.borderLight))
        Row(
            Modifier
                .fillMaxWidth()
                .height(with(density) { DesktopController.TASKBAR.toDp() } - 1.dp)
                .background(DexTheme.taskbar)
                .padding(start = 10.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Như iDesk: nút ứng dụng, rồi Tìm, Gần đây, Home, Quay lại.
            TaskbarIconButton(active = drawerOpen, width = 44.dp, filled = true, onClick = onApps) {
                Canvas(Modifier.size(17.dp)) { drawAppsGlyph(Color.White) }
            }
            Spacer(Modifier.width(4.dp))
            TaskbarIconButton(active = false, onClick = onApps) {
                Icon(DeskIcons.Search, contentDescription = "Tìm", tint = DexTheme.text, modifier = Modifier.size(16.dp))
            }
            TaskbarIconButton(active = false, onClick = onCycleWindows) {
                Icon(DeskIcons.Recents, contentDescription = "Gần đây", tint = DexTheme.text, modifier = Modifier.size(16.dp))
            }
            TaskbarIconButton(active = false, onClick = onShowDesktop) {
                Icon(DeskIcons.Home, contentDescription = "Màn hình chính", tint = DexTheme.text, modifier = Modifier.size(16.dp))
            }
            TaskbarIconButton(active = false, onClick = { DesktopController.topWindow()?.let { DesktopController.sendBack(it.id) } }) {
                Icon(DeskIcons.ArrowBack, contentDescription = "Quay lại", tint = DexTheme.text, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))

            // App ghim + app đang chạy (dạng pill mượt giống iDesk TaskItem)
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (pinned + running).forEach { app ->
                    val appWindows = windows.filter { it.app.component.packageName == app.component.packageName }
                    val w = appWindows.maxByOrNull { it.z }
                    val focused = w != null && w.id == top?.id && !w.minimized
                    val isRunning = w != null
                    TaskbarAppPill(
                        app = app,
                        running = isRunning,
                        focused = focused,
                        minimized = w?.minimized == true,
                        onClick = {
                            if (w == null) DesktopController.open(app) else DesktopController.toggleFromTaskbar(w.id)
                        },
                        onRightClick = { gx, gy ->
                            onItemContextMenu(app, w, gx, gy)
                        },
                    )
                }
            }

            // Khay hệ thống bấm mở QuickPanel
            SystemTray(
                active = quickPanelOpen,
                onClick = onToggleQuickPanel,
            )

            // Góc phải cùng: Hiện desktop
            val showDeskSource = remember { MutableInteractionSource() }
            val showDeskHovered by showDeskSource.collectIsHoveredAsState()
            Box(
                Modifier
                    .width(10.dp)
                    .fillMaxHeight()
                    .background(if (showDeskHovered) Color(0x33FFFFFF) else Color.Transparent)
                    .hoverable(showDeskSource)
                    .clickable(interactionSource = showDeskSource, indication = null, onClick = onShowDesktop),
            ) {
                Box(Modifier.align(Alignment.CenterStart).width(1.dp).fillMaxHeight().background(Color(0x40FFFFFF)))
            }
        }
    }
}

@Composable
private fun TaskbarIconButton(
    active: Boolean,
    width: Dp = 40.dp,
    filled: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(width = width, height = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    active -> DexTheme.accent.copy(alpha = 0.5f)
                    hovered -> Color(if (filled) 0x29FFFFFF else 0x24FFFFFF)
                    filled -> Color(0x14FFFFFF)
                    else -> Color.Transparent
                },
            )
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TaskbarAppPill(
    app: LaunchableApp,
    running: Boolean,
    focused: Boolean,
    minimized: Boolean,
    onClick: () -> Unit,
    onRightClick: (Int, Int) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        Modifier
            .height(40.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                menuPosition = IntOffset(position.x.toInt(), position.y.toInt())
            }
            .let { if (running) it.widthIn(min = 120.dp, max = 200.dp) else it.width(44.dp) }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Color(0x2EFFFFFF)
                    hovered -> Color(0x1FFFFFFF)
                    running -> Color(0x0FFFFFFF)
                    else -> Color.Transparent
                },
            )
            .hoverable(source)
            .pointerInput(app.component, running) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press) {
                            if (e.buttons.isSecondaryPressed) onRightClick(menuPosition.x, menuPosition.y) else onClick()
                        }
                    }
                }
            }
            .padding(horizontal = if (running) 10.dp else 0.dp),
        contentAlignment = if (running) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.alpha(if (minimized) 0.65f else 1f),
        ) {
            AppIcon(app, if (running) 18.dp else 26.dp)
            if (running) {
                Text(
                    app.label,
                    color = DexTheme.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (running) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(width = if (focused) 20.dp else 8.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(if (focused) DexTheme.accent else Color(0x80FFFFFF)),
            )
        }
    }
}

@Composable
private fun SystemTray(active: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableStateOf(readBattery(context)) }
    var wifi by remember { mutableStateOf(hasWifi(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            battery = readBattery(context)
            wifi = hasWifi(context)
            delay(15_000)
        }
    }
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    active -> Color(0x2BFFFFFF)
                    hovered -> Color(0x18FFFFFF)
                    else -> Color.Transparent
                },
            )
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(Modifier.size(16.dp)) { drawWifiGlyph(if (wifi) DexTheme.text else DexTheme.textDim.copy(alpha = 0.4f)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
                drawBatteryGlyph(DexTheme.text, battery.first / 100f, battery.second)
            }
            Spacer(Modifier.width(4.dp))
            Text("${battery.first}%", color = DexTheme.text, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = DexTheme.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now), color = DexTheme.textDim, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------- Quick Panel (cài đặt nhanh & cửa sổ)

@Composable
private fun QuickPanel(
    windows: List<AppWindow>,
    nightLight: Boolean,
    wallpaper: Int,
    brightness: Float,
    onToggleNightLight: () -> Unit,
    onCycleWallpaper: () -> Unit,
    onDynamicWallpaper: () -> Unit,
    onMinimizeAll: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFocusWindow: (Int) -> Unit,
    onCloseWindow: (Int) -> Unit,
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Column(
        Modifier
            .width(340.dp)
            .shadow(24.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(DexTheme.panelGlass)
            .border(1.dp, DexTheme.borderLight, RoundedCornerShape(18.dp))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = DexTheme.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(now),
                color = DexTheme.textDim,
                fontSize = 12.sp,
            )
        }

        // Lưới 2x2 các nút nhanh
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(
                    icon = DeskIcons.NightLight,
                    label = stringResource(R.string.desk_quick_night),
                    active = nightLight,
                    onClick = onToggleNightLight,
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = DeskIcons.Wallpaper,
                    label = stringResource(if (wallpaper < 0) R.string.desk_quick_dynamic_on else R.string.desk_quick_dynamic_off),
                    active = wallpaper < 0,
                    onClick = onDynamicWallpaper,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(
                    icon = DeskIcons.SwapHoriz,
                    label = stringResource(R.string.desk_quick_change_bg),
                    active = false,
                    onClick = onCycleWallpaper,
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = DeskIcons.Monitor,
                    label = stringResource(R.string.desk_quick_show_desktop),
                    active = false,
                    onClick = onMinimizeAll,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Thanh độ sáng màn hình
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x16FFFFFF))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(DeskIcons.Brightness, contentDescription = null, tint = DexTheme.text, modifier = Modifier.size(18.dp))
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.35f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = DexTheme.accent),
            )
            Text("${(brightness * 100).toInt()}%", color = DexTheme.textDim, fontSize = 11.sp)
        }

        // Danh sách cửa sổ đang mở
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (windows.isEmpty()) stringResource(R.string.desk_quick_no_windows)
                else stringResource(R.string.desk_quick_open_windows, windows.size),
                color = DexTheme.textDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (windows.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().height((windows.size.coerceAtMost(4) * 44).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(windows, key = { it.id }) { w ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x12FFFFFF))
                                .clickable { onFocusWindow(w.id) }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppIcon(w.app, 20.dp)
                            Text(w.app.label, color = DexTheme.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1EFFFFFF))
                                    .clickable { onCloseWindow(w.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(DeskIcons.Close, contentDescription = "Đóng", tint = DexTheme.textDim, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    active -> DexTheme.accent.copy(alpha = 0.78f)
                    hovered -> Color(0x26FFFFFF)
                    else -> Color(0x16FFFFFF)
                },
            )
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- Alt+Tab Switcher

@Composable
private fun WindowSwitcher(windows: List<AppWindow>, topId: Int?) {
    Row(
        Modifier
            .shadow(24.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(DexTheme.panelGlass)
            .border(1.dp, DexTheme.borderLight, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        windows.sortedByDescending { it.z }.take(7).forEach { w ->
            val selected = w.id == topId
            Column(
                Modifier
                    .width(116.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) DexTheme.accent.copy(alpha = 0.42f) else Color.Transparent)
                    .border(1.dp, if (selected) DexTheme.accentBright else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(w.app, 48.dp)
                Text(
                    w.app.label,
                    color = DexTheme.text,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun readBattery(context: Context): Pair<Int, Boolean> {
    val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val plugged = (status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    return (if (level >= 0) level * 100 / scale else 0) to plugged
}

@Suppress("DEPRECATION")
private fun hasWifi(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
}

// ---------------------------------------------------------------- ngăn ứng dụng

/**
 * Menu ứng dụng như LauncherView của iDesk: bảng nổi kính mờ ở góc trái dưới, ngay trên nút ứng
 * dụng; tiêu đề, ô "Gõ để tìm" (gõ bàn phím là tìm ngay), mục Gần đây và Tất cả ứng dụng, lưới 3
 * cột. Nhấp phải vào app để ghim/bỏ ghim lên desktop và thanh tác vụ.
 */
@Composable
private fun AppDrawer(
    apps: List<LaunchableApp>,
    recentPackages: List<String>,
    isPinned: (String) -> Boolean,
    onLaunch: (LaunchableApp) -> Unit,
    onTogglePin: (LaunchableApp) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = foldAccents(query.trim())
        if (q.isEmpty()) apps else apps.filter { foldAccents(it.label).contains(q) }
    }
    val recentApps = remember(apps, recentPackages) {
        val byPkg = apps.associateBy { it.component.packageName }
        recentPackages.mapNotNull { byPkg[it] }.take(LAUNCHER_COLUMNS)
    }
    val focus = remember { FocusRequester() }
    val softKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        softKeyboard?.hide()
    }
    val density = LocalDensity.current
    val taskbar = with(density) { DesktopController.TASKBAR.toDp() }
    val maxHeight = with(density) { (DesktopController.desktopHeight - DesktopController.TASKBAR).toDp() } - 16.dp

    // Bấm ra ngoài bảng thì đóng (không làm tối desktop, như iDesk).
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(bottom = taskbar)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .width(LAUNCHER_WIDTH.coerceAtMost((maxWidth - 16.dp).coerceAtLeast(1.dp)))
                .heightIn(max = maxHeight.coerceAtLeast(1.dp))
                .shadow(20.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(DexTheme.panelGlass)
                .border(1.dp, DexTheme.borderLight, RoundedCornerShape(16.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(14.dp),
        ) {
            Text(stringResource(R.string.desk_apps), color = DexTheme.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(DeskIcons.Search, contentDescription = null, tint = DexTheme.textDim, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.desk_search_apps), color = DexTheme.textDim, fontSize = 13.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = DexTheme.text, fontSize = 13.sp),
                        cursorBrush = SolidColor(DexTheme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onLaunch) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { e ->
                                when {
                                    e.type == KeyEventType.KeyDown && e.key == Key.Enter -> {
                                        shown.firstOrNull()?.let(onLaunch); true
                                    }
                                    e.type == KeyEventType.KeyDown && e.key == Key.Escape -> {
                                        onDismiss(); true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(LAUNCHER_COLUMNS),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (query.isBlank() && recentApps.isNotEmpty()) {
                    item(span = { GridItemSpan(LAUNCHER_COLUMNS) }) { LauncherSection(stringResource(R.string.desk_recent)) }
                    items(recentApps, key = { "recent:" + it.component.flattenToString() }) { app ->
                        LauncherTile(app, isPinned(app.component.packageName), onLaunch, onTogglePin)
                    }
                    item(span = { GridItemSpan(LAUNCHER_COLUMNS) }) { LauncherSection(stringResource(R.string.desk_all_apps)) }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(LAUNCHER_COLUMNS) }) {
                        Text("Không có app nào tên \"$query\"", color = DexTheme.textDim, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    }
                }
                items(shown, key = { it.component.flattenToString() }) { app ->
                    LauncherTile(app, isPinned(app.component.packageName), onLaunch, onTogglePin)
                }
            }
        }
    }
}

private const val LAUNCHER_COLUMNS = 3
private val LAUNCHER_WIDTH = 14.dp * 2 + 104.dp * 3 + 6.dp * 2

@Composable
private fun LauncherSection(title: String) {
    Text(title, color = DexTheme.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
private fun LauncherTile(app: LaunchableApp, pinned: Boolean, onLaunch: (LaunchableApp) -> Unit, onTogglePin: (LaunchableApp) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(
        Modifier
            .height(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color(0x24FFFFFF) else Color.Transparent)
            .hoverable(source)
            .pointerInput(app.component) {
                var pressedSecondary = false
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Release) {
                            // Nhả nút trái: mở. Nút phải: ghim/bỏ ghim.
                            if (e.changes.any { it.previousPressed }) {
                                if (pressedSecondary) onTogglePin(app) else onLaunch(app)
                            }
                        }
                        if (e.type == PointerEventType.Press) pressedSecondary = e.buttons.isSecondaryPressed
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(app, 40.dp)
            if (pinned) {
                Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(DexTheme.accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(app.label, color = DexTheme.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private fun foldAccents(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

// ---------------------------------------------------------------- cửa sổ

@Composable
private fun WindowFrame(window: AppWindow) {
    val density = LocalDensity.current
    fun px(value: Int): Dp = with(density) { value.toDp() }
    val focused = DesktopController.topWindow()?.id == window.id
    val maximized = window.restoreBounds != null
    // Như iDesk: bo 10, phóng to thì bo 6.
    val shape = if (maximized) RoundedCornerShape(6.dp) else RoundedCornerShape(10.dp)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(160), label = "appear")
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    val edge = 12

    Column(
        Modifier
            .offset { IntOffset(window.x, window.y) }
            .graphicsLayer {
                alpha = appear
                scaleX = 0.94f + 0.06f * appear
                scaleY = 0.94f + 0.06f * appear
            }
            .width(px(window.width))
            .shadow(if (focused) 24.dp else 10.dp, shape)
            .clip(shape)
            .border(1.dp, if (focused) DexTheme.borderAccent else DexTheme.borderLight, shape)
            .background(DexTheme.windowBackground),
    ) {
        // Thanh tiêu đề kiểu iDesk kết hợp điều khiển cửa sổ trực quan
        Row(
            Modifier
                .fillMaxWidth()
                .height(px(DesktopController.TITLE_BAR))
                .background(if (focused) DexTheme.titleActive else DexTheme.titleInactive)
                .pointerInput(window.id) {
                    detectTapGestures(
                        onPress = { DesktopController.focus(window.id) },
                        onDoubleTap = { DesktopController.toggleMaximize(window.id) },
                    )
                }
                .pointerInput(window.id) {
                    detectDragGestures(
                        onDragStart = { DesktopController.focus(window.id) },
                        onDragEnd = {
                            val snap = DesktopController.snapPreview.value
                            DesktopController.snapPreview.value = null
                            dragPointer = null
                            if (snap != null) {
                                DesktopController.snap(window.id, snap)
                            } else {
                                DesktopController.commitMove(window.id)
                            }
                        },
                        onDragCancel = {
                            DesktopController.snapPreview.value = null
                            dragPointer = null
                        },
                    ) { change, drag ->
                        change.consume()
                        val current = DesktopController.windows.value.firstOrNull { it.id == window.id } ?: window
                        val p = Offset(current.x + change.position.x, current.y + change.position.y)
                        dragPointer = p
                        DesktopController.snapPreview.value = when {
                            p.x <= edge -> DesktopController.Snap.Left
                            p.x >= DesktopController.desktopWidth - edge -> DesktopController.Snap.Right
                            p.y <= edge -> DesktopController.Snap.Top
                            else -> null
                        }
                        DesktopController.move(window.id, drag.x.toInt(), drag.y.toInt())
                    }
                }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ba nút bên trái như iDesk: đóng (đỏ), thu nhỏ (vàng), phóng to (xanh). Ký hiệu chỉ hiện
            // khi rê chuột vào cụm nút; cửa sổ không được chọn thì nút xám.
            val dotsSource = remember { MutableInteractionSource() }
            val dotsHovered by dotsSource.collectIsHoveredAsState()
            Row(Modifier.hoverable(dotsSource), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TitleTrafficDot(DexTheme.close, focused, dotsHovered, onClick = { DesktopController.close(window.id) }) { drawCloseGlyph(it) }
                TitleTrafficDot(DexTheme.minimize, focused, dotsHovered, onClick = { DesktopController.minimize(window.id) }) { drawMinimizeGlyph(it) }
                TitleTrafficDot(DexTheme.maximize, focused, dotsHovered, onClick = { DesktopController.toggleMaximize(window.id) }) {
                    if (maximized) drawRestoreGlyph(it) else drawMaximizeGlyph(it)
                }
            }
            // Tên cửa sổ ở giữa thanh tiêu đề.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    window.app.label,
                    color = if (focused) DexTheme.text else DexTheme.textDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 58.dp), // cân với cụm nút bên trái
                )
            }
            // Ghim trên cùng: chỉ hiện chấm vàng khi đang ghim, rê chuột thì hiện nút để bật/tắt.
            TitleRoundButton(
                active = window.keepOnTop,
                activeColor = Color(0xFFFFD60A),
                onClick = { DesktopController.toggleKeepOnTop(window.id) },
            ) {
                Icon(
                    DeskIcons.Pin,
                    contentDescription = "Ghim trên cùng",
                    tint = if (window.keepOnTop) Color.Black else DexTheme.textDim.copy(alpha = 0.5f),
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(px(window.height))) {
            AppSurface(window)

            if (window.displayId == null && window.error == null) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF0B0E15)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppIcon(window.app, 52.dp)
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = DexTheme.accentBright,
                        strokeWidth = 2.dp,
                    )
                }
            }

            window.error?.let {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xE6111520))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.desk_win_error, window.app.label),
                        color = Color(0xFFFF8A80),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = DexTheme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 4)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DexTheme.accent)
                            .clickable { DesktopController.retryLaunch(window.id) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(stringResource(R.string.action_retry), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (!maximized) {
                val handle = 7.dp
                ResizeHandle(window, Modifier.align(Alignment.CenterEnd).width(handle).fillMaxHeight()) { d ->
                    DesktopController.resizeFrame(window.id, d.x.toInt(), 0)
                }
                ResizeHandle(window, Modifier.align(Alignment.BottomCenter).height(handle).fillMaxWidth()) { d ->
                    DesktopController.resizeFrame(window.id, 0, d.y.toInt())
                }
                ResizeHandle(window, Modifier.align(Alignment.CenterStart).width(handle).fillMaxHeight()) { d ->
                    DesktopController.resizeFromLeft(window.id, d.x.toInt(), 0)
                }
                ResizeHandle(window, Modifier.align(Alignment.BottomStart).size(18.dp)) { d ->
                    DesktopController.resizeFromLeft(window.id, d.x.toInt(), d.y.toInt())
                }
                ResizeHandle(window, Modifier.align(Alignment.BottomEnd).size(20.dp)) { d ->
                    DesktopController.resizeFrame(window.id, d.x.toInt(), d.y.toInt())
                }
                Canvas(Modifier.align(Alignment.BottomEnd).size(12.dp).padding(2.dp)) { drawResizeGlyph(Color(0x66FFFFFF)) }
            }
        }
    }
}

@Composable
private fun TitleRoundButton(
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
                when {
                    active -> activeColor.copy(alpha = 0.85f)
                    hovered -> Color(0x28FFFFFF)
                    else -> Color.Transparent
                },
            )
            .hoverable(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TitleTrafficDot(
    color: Color,
    focused: Boolean,
    groupHovered: Boolean,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit,
) {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (focused || groupHovered) color else DexTheme.dotInactive)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (groupHovered) {
            Canvas(Modifier.size(8.dp)) { glyph(Color(0x99000000)) }
        }
    }
}

@Composable
private fun ResizeHandle(window: AppWindow, modifier: Modifier, onDrag: (Offset) -> Unit) {
    Box(
        modifier.pointerInput(window.id) {
            detectDragGestures(
                onDragStart = { DesktopController.focus(window.id) },
                onDragEnd = { DesktopController.commitResize(window.id) },
            ) { change, drag ->
                change.consume()
                onDrag(drag)
            }
        },
    )
}

// ---------------------------------------------------------------- hình vẽ icon Canvas

private fun DrawScope.drawAppsGlyph(color: Color) {
    val r = size.minDimension / 10
    for (i in 0..2) for (j in 0..2) {
        drawCircle(color, r, Offset(size.width * (0.17f + 0.33f * i), size.height * (0.17f + 0.33f * j)))
    }
}

private fun DrawScope.drawMinimizeGlyph(color: Color) {
    drawLine(color, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.55f), 1.6.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawMaximizeGlyph(color: Color) {
    drawRect(color, style = Stroke(1.4.dp.toPx()))
}

private fun DrawScope.drawRestoreGlyph(color: Color) {
    val s = size.minDimension * 0.72f
    val stroke = Stroke(1.3.dp.toPx())
    drawRect(color, Offset(size.width - s, 0f), Size(s, s), style = stroke)
    drawRect(color, Offset(0f, size.height - s), Size(s, s), style = stroke)
}

private fun DrawScope.drawCloseGlyph(color: Color) {
    val w = 1.6.dp.toPx()
    drawLine(color, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Round)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
}

private fun DrawScope.drawResizeGlyph(color: Color) {
    val w = 1.2.dp.toPx()
    drawLine(color, Offset(size.width, size.height * 0.2f), Offset(size.width * 0.2f, size.height), w)
    drawLine(color, Offset(size.width, size.height * 0.6f), Offset(size.width * 0.6f, size.height), w)
}

private fun DrawScope.drawSearchGlyph(color: Color) {
    val w = 1.8.dp.toPx()
    val r = size.minDimension * 0.33f
    val c = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(color, r, c, style = Stroke(w))
    drawLine(color, Offset(c.x + r * 0.7f, c.y + r * 0.7f), Offset(size.width, size.height), w, StrokeCap.Round)
}

private fun DrawScope.drawWifiGlyph(color: Color) {
    val w = 1.6.dp.toPx()
    val center = Offset(size.width / 2, size.height * 0.9f)
    for (k in 1..3) {
        val r = size.minDimension * 0.28f * k
        drawArc(
            color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round),
        )
    }
    drawCircle(color, w, center)
}

private fun DrawScope.drawBatteryGlyph(color: Color, level: Float, charging: Boolean) {
    val w = 1.3.dp.toPx()
    val body = Size(size.width * 0.88f, size.height)
    drawRoundRect(color, size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(w))
    drawRect(color, Offset(body.width, size.height * 0.3f), Size(size.width * 0.12f, size.height * 0.4f))
    val fill = if (level < 0.2f && !charging) Color(0xFFFF6B6B) else if (charging) Color(0xFF4ADE80) else color
    drawRoundRect(
        fill,
        topLeft = Offset(w * 1.5f, w * 1.5f),
        size = Size((body.width - w * 3) * level.coerceIn(0f, 1f), body.height - w * 3),
        cornerRadius = CornerRadius(1.5.dp.toPx()),
    )
}
