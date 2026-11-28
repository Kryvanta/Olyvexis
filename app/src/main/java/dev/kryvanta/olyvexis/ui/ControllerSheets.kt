package dev.kryvanta.olyvexis.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.BuildConfig
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.desktop.AppRepository
import dev.kryvanta.olyvexis.desktop.LaunchableApp
import dev.kryvanta.olyvexis.desktop.rememberAppIcon
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.onboarding.ShizukuStepContent
import dev.kryvanta.olyvexis.shizuku.ShizukuState
import dev.kryvanta.olyvexis.stream.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* Bottom sheets: dark surface, large title, minimal text. */

private val SheetColor = Color(0xFF1C1C1E)
private val RowColor = Color(0xFF2C2C2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = Phone.text,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, color = Phone.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color = Phone.accent, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RowColor)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Phone.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = Phone.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------- Apps

@Composable
fun AppsSheet(desktopRunning: Boolean, onOpen: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppRepository(context).launchableApps() }
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = fold(query.trim())
        if (q.isEmpty()) apps else apps.filter { fold(it.label).contains(q) }
    }
    val gridHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.48f).coerceAtMost(420.dp)
    PhoneSheet(stringResource(R.string.sheet_apps_title), onDismiss) {
        if (!desktopRunning) {
            Text(stringResource(R.string.sheet_apps_empty_hint), color = Phone.secondary, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RowColor)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(DeskIcons.Search, contentDescription = null, tint = Phone.secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.sheet_apps_search), color = Phone.secondary, fontSize = 15.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Phone.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(Phone.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(78.dp), modifier = Modifier.fillMaxWidth().height(gridHeight)) {
            items(shown, key = { it.component.flattenToString() }) { app ->
                Column(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = desktopRunning) { onOpen(app) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SheetAppIcon(app)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        app.label,
                        color = if (desktopRunning) Phone.text else Phone.secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetAppIcon(app: LaunchableApp) {
    val bmp = rememberAppIcon(app)
    if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(48.dp))
    else Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(RowColor))
}

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

// ---------------------------------------------------------------- Laptop (Wi-Fi)

@Composable
fun LaptopSheet(state: StreamState, onStart: () -> Unit, onStop: () -> Unit, onDismiss: () -> Unit) {
    PhoneSheet(stringResource(R.string.sheet_laptop_title), onDismiss) {
        when (state) {
            StreamState.Idle, is StreamState.Failed -> {
                Text(
                    stringResource(R.string.sheet_laptop_desc),
                    color = Phone.secondary,
                    fontSize = 14.sp,
                )
                if (state is StreamState.Failed) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, color = Phone.red, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.sheet_laptop_start), onClick = onStart)
            }
            StreamState.Starting -> {
                Text(stringResource(R.string.sheet_laptop_starting), color = Phone.secondary, fontSize = 14.sp)
            }
            is StreamState.Running -> {
                Text(stringResource(R.string.sheet_laptop_open_url), color = Phone.secondary, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                if (state.urls.isEmpty()) {
                    Text(stringResource(R.string.sheet_laptop_no_ip), color = Phone.orange, fontSize = 14.sp)
                }
                state.urls.forEach {
                    Text(it, color = Phone.text, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(RowColor).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sheet_laptop_pin), color = Phone.secondary, fontSize = 12.sp)
                        Text(state.pin, color = Phone.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
                    }
                    Text(
                        if (state.clients == 0) stringResource(R.string.status_no_viewers)
                        else stringResource(R.string.status_viewers_count, state.clients),
                        color = if (state.clients == 0) Phone.secondary else Phone.green,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton(stringResource(R.string.sheet_laptop_stop), color = Color(0xFF3A3A3C), onClick = onStop)
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsSheet(
    shizuku: ShizukuState,
    onShizuku: () -> Unit,
    onPreview: () -> Unit,
    onGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pointer by InputSettings.pointerSpeed.collectAsState()
    val scroll by InputSettings.scrollSpeed.collectAsState()
    val natural by InputSettings.naturalScrolling.collectAsState()
    val accel by InputSettings.pointerAcceleration.collectAsState()
    PhoneSheet(stringResource(R.string.sheet_settings_title), onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SliderRow(DeskIcons.Touchpad, stringResource(R.string.setting_pointer), pointer, 0.6f..4f) {
                InputSettings.setPointerSpeed(context, it)
            }
            SliderRow(DeskIcons.SwapHoriz, stringResource(R.string.setting_scroll), scroll, 0.5f..4f) {
                InputSettings.setScrollSpeed(context, it)
            }
            SettingRow(DeskIcons.SwapHoriz, stringResource(R.string.setting_natural_scroll)) {
                Switch(checked = natural, onCheckedChange = { InputSettings.setNaturalScrolling(context, it) }, colors = switchColors())
            }
            SettingRow(DeskIcons.Touchpad, stringResource(R.string.setting_pointer_accel)) {
                Switch(checked = accel, onCheckedChange = { InputSettings.setPointerAcceleration(context, it) }, colors = switchColors())
            }
            Spacer(Modifier.height(8.dp))
            SettingRow(DeskIcons.Usb, stringResource(R.string.setting_shizuku), onClick = onShizuku) {
                Text(
                    if (shizuku == ShizukuState.Ready) stringResource(R.string.setting_shizuku_ready)
                    else stringResource(R.string.setting_shizuku_not_ready),
                    color = if (shizuku == ShizukuState.Ready) Phone.green else Phone.orange,
                    fontSize = 13.sp,
                )
            }
            SettingRow(DeskIcons.Monitor, stringResource(R.string.setting_preview), onClick = onPreview) {
                Icon(DeskIcons.ChevronRight, contentDescription = null, tint = Phone.secondary, modifier = Modifier.size(18.dp))
            }
            SettingRow(DeskIcons.Help, stringResource(R.string.setting_guide), onClick = onGuide) {
                Icon(DeskIcons.ChevronRight, contentDescription = null, tint = Phone.secondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Olyvexis ${BuildConfig.VERSION_NAME} · Kryvanta",
                color = Phone.secondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SliderRow(icon: ImageVector, title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RowColor).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Phone.icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = Phone.text, fontSize = 15.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, colors = sliderColors(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Phone.accent, inactiveTrackColor = Color(0xFF48484A))

@Composable
private fun switchColors() = SwitchDefaults.colors(checkedTrackColor = Phone.green, checkedThumbColor = Color.White)

// ---------------------------------------------------------------- Shizuku

@Composable
fun ShizukuSheet(state: ShizukuState, onDismiss: () -> Unit) {
    PhoneSheet(stringResource(R.string.sheet_shizuku_title), onDismiss) {
        Text(
            stringResource(R.string.sheet_shizuku_desc),
            color = Phone.secondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxHeight(0.7f)) {
            ShizukuStepContent(state)
        }
    }
}
