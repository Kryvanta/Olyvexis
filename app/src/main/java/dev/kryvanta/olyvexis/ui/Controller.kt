package dev.kryvanta.olyvexis.ui

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.input.InputEvent
import dev.kryvanta.olyvexis.input.InputSettings
import dev.kryvanta.olyvexis.input.MouseButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Main phone screen: the phone acts as a trackpad and keyboard while everything else runs on
 * the desktop. Dark background, status bar, large trackpad with an action pill row along the top
 * edge, gesture hints in the center, and a text compose bar at the bottom. Landscape expands the
 * trackpad to fill the screen.
 */

internal object Phone {
    val background = DeskColors.Background
    val pad = DeskColors.SurfaceElevated
    val padLandscape = Color.Black
    val buttonFill = Color(0x80000000)
    val buttonFillLandscape = DeskColors.SurfaceInteractive
    val icon = Color.White.copy(alpha = 0.75f)
    val caption = Color.White.copy(alpha = 0.6f)
    val hint = Color.White.copy(alpha = 0.3f)
    val hintFaint = Color.White.copy(alpha = 0.22f)
    val field = DeskColors.SurfaceInteractive
    val text = Color.White
    val secondary = Color.White.copy(alpha = 0.6f)
    val green = DeskColors.Success
    val orange = DeskColors.Warning
    val red = DeskColors.Error
    val accent = DeskColors.Accent
}

/** Top status bar. */
data class ControllerStatus(
    val connected: Boolean,
    val title: String,
    val detail: String? = null,
    val windows: Int = 0,
)

/** Pill button along the top edge of the trackpad. */
data class PadButton(
    val icon: ImageVector,
    val caption: String,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ControllerScreen(
    status: ControllerStatus,
    banners: List<@Composable () -> Unit>,
    buttons: List<PadButton>,
    inputReady: Boolean,
    onInput: (InputEvent) -> Unit,
    onSendText: (text: String, enter: Boolean) -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Box(
        Modifier
            .fillMaxSize()
            .background(Phone.background),
    ) {
        if (landscape) {
            TrackpadArea(status, buttons, inputReady, landscape = true, onInput = onInput, modifier = Modifier.fillMaxSize().safeDrawingPadding())
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Header(status)
                banners.forEach { it() }
                TrackpadArea(
                    status, buttons, inputReady, landscape = false, onInput = onInput,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)),
                )
                ComposeBar(enabled = inputReady, onSend = onSendText)
            }
        }
    }
}

@Composable
private fun Header(status: ControllerStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                DeskIcons.Monitor,
                contentDescription = null,
                tint = if (status.connected) Phone.green else Phone.orange,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                status.title,
                color = Phone.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (status.windows == 1) stringResource(R.string.windows_singular)
                else stringResource(R.string.windows_plural, status.windows),
                color = Phone.secondary,
                fontSize = 12.sp,
            )
        }
        status.detail?.let {
            Text(it, color = Phone.secondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Compact notification banner below the status bar. */
@Composable
fun ControllerBanner(text: String, color: Color = Phone.orange, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Phone.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                action,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TrackpadArea(
    status: ControllerStatus,
    buttons: List<PadButton>,
    inputReady: Boolean,
    landscape: Boolean,
    onInput: (InputEvent) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .background(if (landscape) Phone.padLandscape else Phone.pad)
            .pointerInput(Unit) { trackpadGestures(scope, haptics, onInput) },
    ) {
        if (!landscape) {
            TrackpadHint(inputReady, Modifier.align(Alignment.Center))
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            buttons.forEach { RoundButton(it, landscape) }
        }
    }
}

@Composable
private fun RoundButton(button: PadButton, landscape: Boolean) {
    Column(
        Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .alpha(if (button.enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        button.active -> Phone.accent
                        landscape -> Phone.buttonFillLandscape
                        else -> Phone.buttonFill
                    },
                )
                .clickable(enabled = button.enabled, onClick = button.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                button.icon,
                contentDescription = button.caption,
                tint = if (button.active) DeskColors.OnAccent else Phone.icon,
                modifier = Modifier.size(20.dp),
            )
        }
        // Portrait shows labels; landscape hides them to leave room for the trackpad.
        if (!landscape) {
            Text(
                button.caption,
                color = Phone.caption,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(50.dp),
                style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
    }
}

/** Center trackpad hint: gesture summary when connected, or connection prompt when idle. Non-interactive. */
@Composable
private fun TrackpadHint(inputReady: Boolean, modifier: Modifier) {
    if (inputReady) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Gesture(DeskIcons.Touchpad, stringResource(R.string.gesture_1f_title), stringResource(R.string.gesture_1f_action))
                Gesture(DeskIcons.CheckCircle, stringResource(R.string.gesture_tap_title), stringResource(R.string.gesture_tap_action))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Gesture(DeskIcons.SwapHoriz, stringResource(R.string.gesture_2f_title), stringResource(R.string.gesture_2f_action))
                Gesture(DeskIcons.AppsGrid, stringResource(R.string.gesture_2f_tap_title), stringResource(R.string.gesture_2f_tap_action))
            }
        }
    } else {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(DeskIcons.Monitor, contentDescription = null, tint = Phone.hint, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.trackpad_connect_hint), color = Phone.hint, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Gesture(icon: ImageVector, title: String, action: String) {
    Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Phone.hint, modifier = Modifier.size(24.dp))
        }
        Text(title, color = Phone.hint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(action, color = Phone.hintFaint, fontSize = 11.sp)
    }
}

/** Text compose bar: supports system IME input (e.g. Telex/VNI) and sends text to the desktop. */
@Composable
private fun ComposeBar(enabled: Boolean, onSend: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Phone.field)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(stringResource(R.string.compose_hint), color = Phone.secondary, fontSize = 15.sp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = Phone.text, fontSize = 15.sp),
                cursorBrush = SolidColor(Phone.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(text, true)
                    text = ""
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clickable(enabled = enabled && text.isNotEmpty()) {
                    onSend(text, false)
                    text = ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                DeskIcons.Send,
                contentDescription = stringResource(R.string.compose_send),
                tint = if (text.isNotEmpty()) Phone.accent else Phone.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Full trackpad gestures:
 * - 1 finger: pointer movement with acceleration; tap: left click; hold 260ms or tap-drag: window drag
 * - 2 fingers: smooth scroll with momentum; 2-finger tap: right click
 * - 3 fingers: swipe left/right to switch windows, down to show desktop, up for app drawer, tap for Back
 */
private suspend fun PointerInputScope.trackpadGestures(
    scope: CoroutineScope,
    haptics: HapticFeedback,
    onInput: (InputEvent) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    var lastTapEnd = 0L
    var momentumJob: Job? = null

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        momentumJob?.cancel()
        momentumJob = null

        val start = down.uptimeMillis
        var travelled = 0f
        var maxPointers = 1
        var end = start
        var dragging = false
        val tapDragArmed = (start - lastTapEnd) < 280L

        var multiDx = 0f
        var multiDy = 0f
        var multiTriggered = false
        var scrollVx = 0f
        var scrollVy = 0f
        var lastScrollTime = start

        val holdJob = scope.launch {
            delay(260)
            if (travelled < slop && maxPointers == 1 && !dragging) {
                dragging = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onInput(InputEvent.Button(MouseButton.Primary, pressed = true))
            }
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                maxPointers = max(maxPointers, pressed.size)
                end = event.changes.maxOf { it.uptimeMillis }
                if (pressed.isEmpty()) break

                if (pressed.size == 1 && maxPointers == 1) {
                    val d = pressed[0].positionChange()
                    val dist = d.getDistance()
                    travelled += dist
                    if (tapDragArmed && !dragging && travelled > slop) {
                        holdJob.cancel()
                        dragging = true
                        onInput(InputEvent.Button(MouseButton.Primary, pressed = true))
                    }
                    if (d != Offset.Zero) {
                        val accel = if (InputSettings.pointerAcceleration.value) 1f + min(dist, 42f) / 18f else 1f
                        onInput(InputEvent.PointerMove(d.x * accel, d.y * accel))
                    }
                } else if (pressed.size == 2 && maxPointers == 2) {
                    holdJob.cancel()
                    val d = pressed.fold(Offset.Zero) { acc, c -> acc + c.positionChange() } / 2f
                    travelled += d.getDistance()
                    if (d != Offset.Zero) {
                        val sx = -d.x * 2.4f
                        val sy = -d.y * 2.4f
                        onInput(InputEvent.Scroll(sx, sy))
                        val dt = max(1L, end - lastScrollTime)
                        scrollVx = scrollVx * 0.35f + (sx / dt * 16f) * 0.65f
                        scrollVy = scrollVy * 0.35f + (sy / dt * 16f) * 0.65f
                        lastScrollTime = end
                    }
                } else if (pressed.size >= 3) {
                    holdJob.cancel()
                    val d = pressed.fold(Offset.Zero) { acc, c -> acc + c.positionChange() } / pressed.size.toFloat()
                    multiDx += d.x
                    multiDy += d.y
                    travelled += d.getDistance()
                    if (!multiTriggered && max(abs(multiDx), abs(multiDy)) > 90f) {
                        multiTriggered = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        when {
                            abs(multiDx) > abs(multiDy) -> DesktopController.cycle()
                            multiDy > 0 -> DesktopController.minimizeAll()
                            else -> DesktopController.drawerOpen.value = !DesktopController.drawerOpen.value
                        }
                    }
                }
                event.changes.forEach { it.consume() }
            }
        } finally {
            holdJob.cancel()
            if (dragging) onInput(InputEvent.Button(MouseButton.Primary, pressed = false))
        }

        if (!dragging) {
            if (maxPointers == 2 && travelled >= slop && (end - lastScrollTime) < 80L) {
                if (hypot(scrollVx, scrollVy) > 2.5f) {
                    var vx = scrollVx
                    var vy = scrollVy
                    momentumJob = scope.launch {
                        repeat(45) {
                            delay(16)
                            onInput(InputEvent.Scroll(vx, vy))
                            vx *= 0.90f
                            vy *= 0.90f
                            if (hypot(vx, vy) < 0.4f) return@launch
                        }
                    }
                }
            } else if (travelled < slop && !multiTriggered) {
                if (maxPointers >= 3) {
                    onInput(InputEvent.Key(KeyEvent.KEYCODE_BACK, true, 0))
                    onInput(InputEvent.Key(KeyEvent.KEYCODE_BACK, false, 0))
                } else {
                    val button = if (maxPointers == 2 || end - start > 500) MouseButton.Secondary else MouseButton.Primary
                    onInput(InputEvent.Button(button, pressed = true))
                    onInput(InputEvent.Button(button, pressed = false))
                    if (button == MouseButton.Primary) lastTapEnd = end
                }
            }
        }
    }
}

/** Enter key sent after text when pressing Send on the keyboard. */
internal fun enterKey(onInput: (InputEvent) -> Unit) {
    onInput(InputEvent.Key(KeyEvent.KEYCODE_ENTER, true, 0))
    onInput(InputEvent.Key(KeyEvent.KEYCODE_ENTER, false, 0))
}
