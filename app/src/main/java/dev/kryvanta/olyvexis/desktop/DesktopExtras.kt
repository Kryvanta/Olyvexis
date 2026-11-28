package dev.kryvanta.olyvexis.desktop

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.BuildConfig
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.ui.DeskIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun foldAccents(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

// ---------------------------------------------------------------- Desktop Widgets (Clock + Calendar)

@Composable
internal fun DesktopWidgetsView(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Column(
        modifier = modifier.width(232.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Clock widget
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xC71C1C1E))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(now),
                color = Color(0x99EBEBF5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Month calendar widget
        MonthCalendarWidget(now)

        FocusTimerWidget()
    }
}

@Composable
private fun FocusTimerWidget() {
    var remaining by remember { mutableIntStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1_000)
            remaining--
        }
        if (remaining == 0) running = false
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xC71C1C1E))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.desk_widget_focus), color = Color(0x99EBEBF5), fontSize = 11.sp)
            Text(
                "%02d:%02d".format(remaining / 60, remaining % 60),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            if (running) "❚❚" else stringResource(R.string.desk_widget_start),
            color = Color(0xFF409CFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (remaining == 0) remaining = 25 * 60; running = !running }
                .padding(8.dp),
        )
        Text(
            stringResource(R.string.desk_widget_reset),
            color = Color(0xFF8E8E93),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { running = false; remaining = 25 * 60 }
                .padding(8.dp),
        )
    }
}

@Composable
private fun MonthCalendarWidget(now: Date) {
    val cal = remember(now) {
        Calendar.getInstance().apply { time = now }
    }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now)
    val firstDayOffset = remember(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)) {
        val c = cal.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, 1)
        (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
    val cells = List(firstDayOffset) { 0 } + (1..daysInMonth).toList()
    val rows = cells.chunked(7)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xC71C1C1E))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            monthTitle.replaceFirstChar { it.uppercase() },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            weekdays.forEach { d ->
                Text(
                    d,
                    color = Color(0xFF8E8E93),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val day = week.getOrElse(i) { 0 }
                    val isToday = day == today
                    Box(
                        Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(CircleShape)
                            .background(if (isToday) Color(0xFF0A84FF) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day > 0) {
                            Text(
                                "$day",
                                color = if (isToday) Color.White else Color(0xFFE5E5EA),
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Spotlight (Meta + Space)

private data class SpotlightItem(
    val category: String,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val app: LaunchableApp? = null,
    val action: () -> Unit,
)

@Composable
internal fun SpotlightOverlay(
    apps: List<LaunchableApp>,
    windows: List<AppWindow>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val softKeyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        softKeyboard?.hide()
    }

    val results = buildSpotlightResults(context, query, apps, windows) { value ->
        clipboard.setText(AnnotatedString(value))
        DesktopController.showToast(context.getString(R.string.desk_copied, value))
    }

    LaunchedEffect(results.size) {
        if (selectedIndex >= results.size) selectedIndex = 0
    }

    val runSelected: () -> Unit = {
        results.getOrNull(selectedIndex)?.let {
            onDismiss()
            it.action()
        }
    }

    val taskbar = with(LocalDensity.current) { DesktopController.TASKBAR.toDp() }
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = taskbar)
            .background(Color(0x55000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 84.dp)
                .width(580.dp)
                .shadow(28.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21C1C1E))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(18.dp))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(DeskIcons.Search, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.desk_spotlight_hint), color = Color(0xFF8E8E93), fontSize = 15.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedIndex = 0
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color(0xFF0A84FF)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { runSelected() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.Enter -> { runSelected(); true }
                                    Key.Escape -> { onDismiss(); true }
                                    Key.DirectionDown -> {
                                        if (results.isNotEmpty()) selectedIndex = (selectedIndex + 1) % results.size
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (results.isNotEmpty()) selectedIndex = (selectedIndex - 1 + results.size) % results.size
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }

            if (results.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x1CFFFFFF)))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .height((results.size.coerceAtMost(8) * 46).dp),
                ) {
                    itemsIndexed(results) { index, item ->
                        val source = remember { MutableInteractionSource() }
                        val hovered by source.collectIsHoveredAsState()
                        val selected = index == selectedIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        selected -> Color(0x550A84FF)
                                        hovered -> Color(0x1EFFFFFF)
                                        else -> Color.Transparent
                                    },
                                )
                                .hoverable(source)
                                .clickable(interactionSource = source, indication = null) {
                                    onDismiss()
                                    item.action()
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (item.app != null) {
                                val bmp = dev.kryvanta.olyvexis.desktop.rememberAppIcon(item.app)
                                if (bmp != null) {
                                    Image(bmp, contentDescription = null, modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)))
                                } else {
                                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x28FFFFFF)))
                                }
                            } else if (item.icon != null) {
                                Icon(item.icon, contentDescription = null, tint = Color(0xFF409CFF), modifier = Modifier.size(20.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (item.subtitle.isNotEmpty()) {
                                    Text(item.subtitle, color = Color(0x99EBEBF5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(item.category, color = Color(0xFF8E8E93), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun buildSpotlightResults(
    context: Context,
    query: String,
    apps: List<LaunchableApp>,
    windows: List<AppWindow>,
    onCopyAnswer: (String) -> Unit,
): List<SpotlightItem> {
    val q = foldAccents(query.trim())
    if (q.isEmpty()) {
        return windows.sortedByDescending { it.z }.take(5).map { w ->
            SpotlightItem(
                category = context.getString(R.string.desk_spotlight_window),
                title = w.app.label,
                app = w.app,
                action = { DesktopController.focus(w.id) },
            )
        }
    }

    val out = mutableListOf<SpotlightItem>()

    evalExpression(query.trim())?.let { result ->
        out += SpotlightItem(
            category = context.getString(R.string.desk_spotlight_answer),
            title = result,
            subtitle = context.getString(R.string.desk_spotlight_copy_hint),
            icon = DeskIcons.CheckCircle,
            action = { onCopyAnswer(result) },
        )
    }

    windows
        .filter { foldAccents(it.app.label).contains(q) }
        .sortedByDescending { it.z }
        .take(3)
        .forEach { w ->
            out += SpotlightItem(
                category = context.getString(R.string.desk_spotlight_window),
                title = w.app.label,
                app = w.app,
                action = { DesktopController.focus(w.id) },
            )
        }

    apps
        .filter { foldAccents(it.label).contains(q) }
        .take(6)
        .forEach { app ->
            out += SpotlightItem(
                category = context.getString(R.string.desk_spotlight_app),
                title = app.label,
                app = app,
                action = { DesktopController.open(app) },
            )
        }

    val actions = listOf(
        Triple(context.getString(R.string.desk_quick_settings), DeskIcons.Settings) { DesktopController.settingsOpen.value = true },
        Triple(context.getString(R.string.desk_settings_android), DeskIcons.Settings) { DesktopController.openAndroidSettings(context) },
        Triple(context.getString(R.string.desk_clipboard_title), DeskIcons.AppsGrid) { DesktopController.clipboardOpen.value = true },
        Triple(context.getString(R.string.desk_note_title), DeskIcons.AppsGrid) { DesktopController.toggleStickyNote() },
        Triple(context.getString(R.string.desk_lock_action), DeskIcons.Monitor) { DesktopController.lockDesktop() },
        Triple(context.getString(R.string.desk_quick_night), DeskIcons.NightLight) { DesktopController.toggleNightLight() },
        Triple(context.getString(R.string.desk_quick_widgets), DeskIcons.AppsGrid) { DesktopController.toggleWidgets() },
        Triple(context.getString(R.string.desk_quick_show_desktop), DeskIcons.Monitor) { DesktopController.minimizeAll() },
        Triple(context.getString(R.string.desk_menu_shortcuts), DeskIcons.Keyboard) { DesktopController.shortcutsOpen.value = true },
    )
    actions.filter { foldAccents(it.first).contains(q) }.forEach { (label, icon, act) ->
        out += SpotlightItem(
            category = context.getString(R.string.desk_spotlight_action),
            title = label,
            icon = icon,
            action = act,
        )
    }

    return out.take(8)
}

private class ExprParser(private val expr: String) {
    private var idx = 0

    fun parse(): Double {
        val v = parseExpr()
        require(idx == expr.length && v.isFinite())
        return v
    }

    private fun parseExpr(): Double {
        var v = parseTerm()
        while (idx < expr.length && (expr[idx] == '+' || expr[idx] == '-')) {
            val op = expr[idx++]
            val r = parseTerm()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun parseTerm(): Double {
        var v = parseFactor()
        while (idx < expr.length && (expr[idx] == '*' || expr[idx] == '/')) {
            val op = expr[idx++]
            val r = parseFactor()
            v = if (op == '*') v * r else v / r
        }
        return v
    }

    private fun parseFactor(): Double {
        if (idx < expr.length && expr[idx] == '-') {
            idx++
            return -parseFactor()
        }
        if (idx < expr.length && expr[idx] == '(') {
            idx++
            val v = parseExpr()
            require(idx < expr.length && expr[idx] == ')')
            idx++
            return v
        }
        val start = idx
        while (idx < expr.length && (expr[idx].isDigit() || expr[idx] == '.')) idx++
        require(idx > start)
        return expr.substring(start, idx).toDouble()
    }
}

/** Simple arithmetic expression evaluator (+ - * / parentheses) for Spotlight. */
private fun evalExpression(raw: String): String? {
    val expr = raw.replace(" ", "")
    if (expr.isEmpty() || !expr.any { it in "+-*/" }) return null
    if (!expr.all { it in "0123456789.+-*/()" }) return null
    return runCatching {
        val res = ExprParser(expr).parse()
        if (res == res.toLong().toDouble()) res.toLong().toString() else "%.4f".format(Locale.US, res).trimEnd('0').trimEnd('.')
    }.getOrNull()
}

// ---------------------------------------------------------------- Shortcuts Cheat-Sheet (Meta + /)

@Composable
internal fun ShortcutsOverlay(onDismiss: () -> Unit) {
    val rows = listOf(
        "Meta" to stringResource(R.string.desk_shortcut_drawer),
        "Meta + Space" to stringResource(R.string.desk_shortcut_search),
        "Meta + 1-4" to stringResource(R.string.desk_shortcut_workspace),
        "Meta + Shift + V" to stringResource(R.string.desk_shortcut_clipboard),
        "Meta + L" to stringResource(R.string.desk_shortcut_lock),
        "Meta + ← / →" to stringResource(R.string.desk_shortcut_snap),
        "Meta + ↑ / ↓" to stringResource(R.string.desk_shortcut_maxmin),
        "Meta + D" to stringResource(R.string.desk_shortcut_desktop),
        "Alt + Tab" to stringResource(R.string.desk_shortcut_switch),
        "Alt + F4" to stringResource(R.string.desk_shortcut_close),
        "Meta + /" to stringResource(R.string.desk_shortcut_help),
        "Esc" to stringResource(R.string.desk_shortcut_esc),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .shadow(28.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF21C1C1E))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.desk_shortcuts_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            rows.forEach { (key, desc) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        key,
                        color = Color(0xFF409CFF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x18FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(desc, color = Color(0xFFE5E5EA), fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Desktop Settings

private enum class SettingsPane(val labelRes: Int, val icon: ImageVector) {
    Display(R.string.desk_settings_display, DeskIcons.Monitor),
    Wallpaper(R.string.desk_settings_wallpaper, DeskIcons.Wallpaper),
    Desktop(R.string.desk_settings_desktop, DeskIcons.AppsGrid),
    Trackpad(R.string.desk_settings_input, DeskIcons.Touchpad),
    System(R.string.desk_settings_system, DeskIcons.Settings),
}

@Composable
internal fun DesktopSettingsOverlay(
    wallpapers: List<List<Color>>,
    onDismiss: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
) {
    val context = LocalContext.current
    val wallpaper by DesktopController.wallpaper.collectAsState()
    val nightLight by DesktopController.nightLight.collectAsState()
    val brightness by DesktopController.brightness.collectAsState()
    val showWidgets by DesktopController.showWidgets.collectAsState()
    val autoHide by DesktopController.autoHideTaskbar.collectAsState()

    val pointerSpeed by InputSettings.pointerSpeed.collectAsState()
    val scrollSpeed by InputSettings.scrollSpeed.collectAsState()
    val naturalScroll by InputSettings.naturalScrolling.collectAsState()
    val pointerAccel by InputSettings.pointerAcceleration.collectAsState()

    var selectedPane by remember { mutableStateOf(SettingsPane.Display) }

    val accent = Color(0xFF0A84FF)
    val windowBg = Color(0xF5161618)
    val sidebarBg = Color(0xFF1C1C1F)
    val dividerColor = Color(0xFF2A2A2E)
    val cardBg = Color(0xFF232326)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(660.dp)
                .height(460.dp)
                .shadow(28.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(windowBg)
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // Left sidebar
            Column(
                Modifier
                    .width(196.dp)
                    .fillMaxHeight()
                    .background(sidebarBg)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    stringResource(R.string.desk_settings_title),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                )
                SettingsPane.entries.forEach { pane ->
                    val active = selectedPane == pane
                    val hoverSource = remember { MutableInteractionSource() }
                    val hovered by hoverSource.collectIsHoveredAsState()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    active -> accent
                                    hovered -> Color(0xFF2A2A2E)
                                    else -> Color.Transparent
                                },
                            )
                            .hoverable(hoverSource)
                            .clickable { selectedPane = pane }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            pane.icon,
                            contentDescription = null,
                            tint = if (active) Color.White else Color(0xFFAEAEB2),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(pane.labelRes),
                            color = if (active) Color.White else Color(0xFFE5E5EA),
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Vertical divider
            Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))

            // Right content pane
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(selectedPane.labelRes),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x1EFFFFFF))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(DeskIcons.Close, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedPane) {
                        SettingsPane.Display -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SettingsSliderRow(
                                    label = stringResource(R.string.desk_settings_brightness),
                                    value = brightness,
                                    range = 0.35f..1f,
                                    valueText = "${(brightness * 100).toInt()}%",
                                    accent = accent,
                                    onChange = { DesktopController.setBrightness(it) },
                                )
                                SettingsToggleRow(stringResource(R.string.desk_quick_night), nightLight, accent) {
                                    DesktopController.toggleNightLight()
                                }
                            }
                        }

                        SettingsPane.Wallpaper -> {
                            val tiles = buildList<Pair<Int, List<Color>>> {
                                add(-1 to listOf(Color(0xFF1E3A8A), Color(0xFF4C1D95), Color(0xFF0E7490)))
                                wallpapers.forEachIndexed { idx, colors -> add(idx to colors) }
                            }
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                tiles.chunked(3).forEach { rowTiles ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        rowTiles.forEach { (idx, colors) ->
                                            val active = wallpaper == idx
                                            Box(
                                                Modifier
                                                    .weight(1f)
                                                    .height(64.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Brush.linearGradient(colors))
                                                    .border(
                                                        width = 2.dp,
                                                        color = if (active) accent else Color(0x33FFFFFF),
                                                        shape = RoundedCornerShape(10.dp),
                                                    )
                                                    .clickable { DesktopController.setWallpaper(idx) }
                                                    .padding(8.dp),
                                                contentAlignment = Alignment.BottomStart,
                                            ) {
                                                if (idx < 0) {
                                                    Text(
                                                        stringResource(R.string.desk_settings_wallpaper_dynamic),
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        }
                                        repeat(3 - rowTiles.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        SettingsPane.Desktop -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SettingsToggleRow(stringResource(R.string.desk_quick_widgets), showWidgets, accent) {
                                    DesktopController.toggleWidgets()
                                }
                                SettingsToggleRow(stringResource(R.string.desk_quick_autohide), autoHide, accent) {
                                    DesktopController.toggleAutoHideTaskbar()
                                }
                            }
                        }

                        SettingsPane.Trackpad -> {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SettingsSliderRow(
                                    label = stringResource(R.string.setting_pointer),
                                    value = pointerSpeed,
                                    range = 0.6f..4f,
                                    valueText = "%.1fx".format(Locale.US, pointerSpeed),
                                    accent = accent,
                                    onChange = { InputSettings.setPointerSpeed(context, it) },
                                )
                                SettingsSliderRow(
                                    label = stringResource(R.string.setting_scroll),
                                    value = scrollSpeed,
                                    range = 0.5f..4f,
                                    valueText = "%.1fx".format(Locale.US, scrollSpeed),
                                    accent = accent,
                                    onChange = { InputSettings.setScrollSpeed(context, it) },
                                )
                                SettingsToggleRow(stringResource(R.string.setting_natural_scroll), naturalScroll, accent) {
                                    InputSettings.setNaturalScrolling(context, it)
                                }
                                SettingsToggleRow(stringResource(R.string.setting_pointer_accel), pointerAccel, accent) {
                                    InputSettings.setPointerAcceleration(context, it)
                                }
                            }
                        }

                        SettingsPane.System -> {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .clickable {
                                        onDismiss()
                                        onOpenAndroidSettings()
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(DeskIcons.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.desk_settings_android), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.desk_settings_android_desc), color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                Text(
                                    stringResource(R.string.desk_settings_open),
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBg)
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            DesktopController.lockDesktop()
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.desk_lock_action), color = Color(0xFFE2E8F0), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Icon(DeskIcons.ChevronRight, contentDescription = null, tint = Color(0xFF8E8E93), modifier = Modifier.size(16.dp))
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            DesktopController.shortcutsOpen.value = true
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(stringResource(R.string.desk_menu_shortcuts), color = Color(0xFFE2E8F0), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Icon(DeskIcons.ChevronRight, contentDescription = null, tint = Color(0xFF8E8E93), modifier = Modifier.size(16.dp))
                                }
                            }

                            Text(
                                "Olyvexis ${BuildConfig.VERSION_NAME}",
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, accent: Color, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFE5E5EA), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFF8E8E93),
                uncheckedTrackColor = Color(0x28FFFFFF),
            ),
        )
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color(0xFFE5E5EA), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = Color(0xFF8E8E93), fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent),
        )
    }
}

// ---------------------------------------------------------------- Clipboard History (Meta + Shift + V)

internal object DesktopClipboard {
    private const val LIMIT = 20
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items = _items.asStateFlow()
    private var registered = false

    fun startListening(context: Context) {
        if (registered) return
        registered = true
        val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        readCurrent(cm)
        cm.addPrimaryClipChangedListener { readCurrent(cm) }
    }

    private fun readCurrent(cm: ClipboardManager) {
        val text = runCatching {
            cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }.getOrNull()?.trim() ?: return
        if (text.isEmpty() || text.length > 50_000) return
        _items.value = (listOf(text) + _items.value.filterNot { it == text }).take(LIMIT)
    }

    fun copy(context: Context, text: String) {
        val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("Olyvexis", text))
        _items.value = (listOf(text) + _items.value.filterNot { it == text }).take(LIMIT)
    }

    fun clear() {
        _items.value = emptyList()
    }
}

@Composable
internal fun ClipboardHistoryOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items by DesktopClipboard.items.collectAsState()
    val accent = Color(0xFF0A84FF)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .shadow(28.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF21C1C1E))
                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(20.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.desk_clipboard_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    Text(
                        stringResource(R.string.desk_clipboard_clear),
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { DesktopClipboard.clear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (items.isEmpty()) {
                Text(stringResource(R.string.desk_clipboard_empty), color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x14FFFFFF))
                                .clickable {
                                    DesktopClipboard.copy(context, entry)
                                    DesktopController.showToast(context.getString(R.string.desk_copied, entry.take(24)))
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry, color = Color(0xFFE5E5EA), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Sticky Note

@Composable
internal fun StickyNoteCard(
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(240.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEA2B2412))
            .border(1.dp, Color(0x44FBBF24), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.desk_note_title), color = Color(0xFFFDE68A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(DeskIcons.Close, contentDescription = null, tint = Color(0xFFFDE68A), modifier = Modifier.size(11.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(120.dp)) {
            if (text.isEmpty()) {
                Text(stringResource(R.string.desk_note_hint), color = Color(0x99FDE68A), fontSize = 12.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = TextStyle(color = Color(0xFFFEF3C7), fontSize = 12.sp, lineHeight = 17.sp),
                cursorBrush = SolidColor(Color(0xFFFBBF24)),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------- Desktop Lock Screen (Meta + L)

@Composable
internal fun DesktopLockOverlay(onUnlock: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xEB0E0E10))
            .pointerInput(Unit) { detectTapGestures { onUnlock() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
            )
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                color = Color(0x99EBEBF5),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color(0x1EFFFFFF))
                    .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                    .clickable { onUnlock() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.desk_lock_hint), color = Color(0xFFE5E5EA), fontSize = 13.sp)
            }
        }
    }
}
