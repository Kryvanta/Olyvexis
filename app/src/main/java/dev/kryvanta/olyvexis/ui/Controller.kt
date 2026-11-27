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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kryvanta.olyvexis.R
import dev.kryvanta.olyvexis.input.InputEvent
import dev.kryvanta.olyvexis.input.MouseButton
import kotlin.math.max

/*
 * Màn hình chính trên điện thoại, theo iDesk: điện thoại chỉ là trackpad và bàn phím, mọi thứ khác
 * nằm trên desktop. Nền đen; dòng trạng thái; trackpad xám lớn với một hàng nút tròn có chữ ở mép
 * trên và gợi ý cử chỉ ở giữa; ô gõ chữ ở dưới. Xoay ngang thì trackpad chiếm cả màn hình.
 */

internal object Phone {
    val background = Color.Black
    val pad = Color(0xFF212121)
    val padLandscape = Color.Black
    val buttonFill = Color(0x80000000)
    val buttonFillLandscape = Color(0xFF292929)
    val icon = Color.White.copy(alpha = 0.75f)
    val caption = Color.White.copy(alpha = 0.55f)
    val hint = Color.White.copy(alpha = 0.3f)
    val hintFaint = Color.White.copy(alpha = 0.22f)
    val field = Color(0xFF292929)
    val text = Color.White
    val secondary = Color.White.copy(alpha = 0.6f)
    val green = Color(0xFF30D158)
    val orange = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
    val accent = Color(0xFF0A84FF)
}

/** Dòng trạng thái ở đầu màn hình. */
data class ControllerStatus(
    val connected: Boolean,
    val title: String,
    val detail: String? = null,
    val windows: Int = 0,
)

/** Một nút tròn trên mép trackpad. */
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

/** Dải thông báo nhỏ dưới dòng trạng thái (giống UpdateBanner của iDesk). */
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
    Box(
        modifier
            .background(if (landscape) Phone.padLandscape else Phone.pad)
            .pointerInput(Unit) { trackpadGestures(onInput) },
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
            .padding(4.dp)
            .alpha(if (button.enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .size(44.dp)
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
                tint = if (button.active) Color.White else Phone.icon,
                modifier = Modifier.size(20.dp),
            )
        }
        // Dọc: mỗi nút ghi rõ là gì; ngang: nhường chỗ cho trackpad.
        if (!landscape) {
            Text(
                button.caption,
                color = Phone.caption,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(58.dp),
                style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
    }
}

/** Giữa trackpad: các cử chỉ (đã nối) hoặc lời nhắc nối màn hình. Chữ mờ, không nhận chạm. */
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

/** Ô gõ chữ: gõ bằng bộ gõ của máy (tiếng Việt Telex/VNI...), Gửi để đưa chữ lên desktop. */
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
 * Cử chỉ trackpad: một ngón di chuột; chạm để nhấp; giữ lâu để nhấp phải; hai ngón để cuộn; chạm
 * hai ngón để nhấp phải.
 * TODO: giữ rồi kéo để kéo thả / đổi cỡ cửa sổ; ba ngón (ứng dụng gần đây, desktop, quay lại) như iDesk.
 */
private suspend fun PointerInputScope.trackpadGestures(onInput: (InputEvent) -> Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.uptimeMillis
        var travelled = 0f
        var maxPointers = 1
        var end = start
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            maxPointers = max(maxPointers, pressed.size)
            end = event.changes.maxOf { it.uptimeMillis }
            if (pressed.isEmpty()) break
            if (pressed.size == 1) {
                val d = pressed[0].positionChange()
                travelled += d.getDistance()
                if (d != Offset.Zero && maxPointers == 1) onInput(InputEvent.PointerMove(d.x, d.y))
            } else {
                // Cuộn tự nhiên: hai ngón đẩy lên thì nội dung đi lên.
                val d = pressed.fold(Offset.Zero) { acc, c -> acc + c.positionChange() } / pressed.size.toFloat()
                travelled += d.getDistance()
                if (d != Offset.Zero) onInput(InputEvent.Scroll(-d.x * 3f, -d.y * 3f))
            }
            event.changes.forEach { it.consume() }
        }
        if (travelled < slop) {
            val button = if (maxPointers >= 2 || end - start > 500) MouseButton.Secondary else MouseButton.Primary
            onInput(InputEvent.Button(button, pressed = true))
            onInput(InputEvent.Button(button, pressed = false))
        }
    }
}

/** Phím Enter (gửi kèm sau chữ khi bấm Gửi trên bàn phím). */
internal fun enterKey(onInput: (InputEvent) -> Unit) {
    onInput(InputEvent.Key(KeyEvent.KEYCODE_ENTER, true, 0))
    onInput(InputEvent.Key(KeyEvent.KEYCODE_ENTER, false, 0))
}
