package dev.kryvanta.olyvexis.input

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Browser mouse and keyboard -> desktop display.
 *
 * JSON messages from the web client (see assets/web/index.html):
 * - `{"t":"m","a":"move|down|up","x":0..1,"y":0..1,"b":buttonsMask,"btn":changedButton}`
 *   (`b` matches MouseEvent.buttons: 1 left, 2 right, 4 middle, same as MotionEvent.BUTTON_*)
 * - `{"t":"w","x":..,"y":..,"dx":..,"dy":..}` wheel scroll in browser pixels
 * - `{"t":"k","a":"down|up","code":"KeyA","m":modifiers (1 Shift, 2 Ctrl, 4 Alt, 8 Meta),"r":repeat}`
 * - `{"t":"txt","s":"..."}` composed text from client IME
 *
 * Events target the desktop display; DesktopActivity forwards them to the app window under the
 * cursor or the top-most window (for keyboard events).
 */
class BrowserInput(
    private val displayId: Int,
    @Volatile private var width: Int,
    @Volatile private var height: Int,
    private val onResizeRequested: ((Int, Int) -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var buttons = 0
    private var downTime = 0L
    private val keyDownTimes = HashMap<Int, Long>()

    init {
        scope.launch {
            for (task in queue) runCatching { task() }.onFailure { Log.w(TAG, "Không tiêm được input", it) }
        }
    }

    fun updateSize(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        cursorX = cursorX.coerceIn(0f, (newWidth - 1).coerceAtLeast(0).toFloat())
        cursorY = cursorY.coerceIn(0f, (newHeight - 1).coerceAtLeast(0).toFloat())
    }

    fun onMessage(text: String) {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (msg.optString("t")) {
            "m" -> mouse(msg)
            "w" -> wheel(msg)
            "k" -> key(msg)
            "txt" -> msg.optString("s").takeIf { it.isNotEmpty() }?.let { s ->
                send { it.injectText(displayId, s) }
            }
            "resize" -> {
                val w = msg.optInt("w")
                val h = msg.optInt("h")
                if (w > 0 && h > 0) onResizeRequested?.invoke(w, h)
            }
        }
    }

    private fun mouse(msg: JSONObject) {
        val x = (msg.optDouble("x") * width).toFloat().coerceIn(0f, width - 1f)
        val y = (msg.optDouble("y") * height).toFloat().coerceIn(0f, height - 1f)
        val newButtons = msg.optInt("b") and (BUTTON_PRIMARY or BUTTON_SECONDARY or BUTTON_TERTIARY)
        val changed = when (msg.optInt("btn")) {
            0 -> BUTTON_PRIMARY
            1 -> BUTTON_TERTIARY
            2 -> BUTTON_SECONDARY
            else -> 0
        }
        when (msg.optString("a")) {
            "move" -> {
                val action = if (buttons == 0) MotionEvent.ACTION_HOVER_MOVE else MotionEvent.ACTION_MOVE
                val b = buttons
                val dt = downTime
                send { it.injectMouse(displayId, action, x, y, b, 0, dt, 0f, 0f) }
            }
            "down" -> {
                val first = buttons == 0
                buttons = buttons or changed
                if (first) downTime = SystemClock.uptimeMillis()
                val b = buttons
                val dt = downTime
                send {
                    if (first) {
                        it.injectMouse(displayId, MotionEvent.ACTION_HOVER_EXIT, x, y, 0, 0, 0, 0f, 0f)
                        it.injectMouse(displayId, MotionEvent.ACTION_DOWN, x, y, b, 0, dt, 0f, 0f)
                    }
                    it.injectMouse(displayId, MotionEvent.ACTION_BUTTON_PRESS, x, y, b, changed, dt, 0f, 0f)
                }
            }
            "up" -> {
                if (buttons == 0) return
                // Trust the button state reported by the browser (more reliable than tracking locally).
                buttons = newButtons and changed.inv()
                val b = buttons
                val dt = downTime
                send {
                    it.injectMouse(displayId, MotionEvent.ACTION_BUTTON_RELEASE, x, y, b, changed, dt, 0f, 0f)
                    if (b == 0) {
                        it.injectMouse(displayId, MotionEvent.ACTION_UP, x, y, 0, 0, dt, 0f, 0f)
                        it.injectMouse(displayId, MotionEvent.ACTION_HOVER_ENTER, x, y, 0, 0, 0, 0f, 0f)
                    }
                }
            }
        }
    }

    private fun wheel(msg: JSONObject) {
        val x = (msg.optDouble("x") * width).toFloat()
        val y = (msg.optDouble("y") * height).toFloat()
        // Browser: dy > 0 scrolls down (~100 px per detent). Android: VSCROLL > 0 scrolls up.
        val v = (-msg.optDouble("dy") / 100.0).toFloat().coerceIn(-10f, 10f)
        val h = (msg.optDouble("dx") / 100.0).toFloat().coerceIn(-10f, 10f)
        if (v == 0f && h == 0f) return
        send { it.injectMouse(displayId, MotionEvent.ACTION_SCROLL, x, y, 0, 0, 0, v, h) }
    }

    private var metaDown = false
    private var metaUsed = false

    /**
     * Desktop shortcuts handled directly before the system intercepts them: Android (especially 14+)
     * consumes Meta for phone shortcuts, so Meta would otherwise never reach the desktop.
     * Returns true if handled.
     */
    private fun shortcut(keyCode: Int, down: Boolean, mods: Int): Boolean {
        val isMeta = keyCode == KeyEvent.KEYCODE_META_LEFT || keyCode == KeyEvent.KEYCODE_META_RIGHT
        if (isMeta) {
            if (down) {
                if (!metaDown) metaUsed = false
                metaDown = true
            } else {
                // Only toggle the drawer if Meta was actually pressed (browsers send keyup for all
                // modifiers on blur, which must not count as a standalone Meta tap).
                if (metaDown && !metaUsed) DesktopController.drawerOpen.value = !DesktopController.drawerOpen.value
                metaDown = false
            }
            return true
        }
        if (DesktopController.lockOpen.value) {
            if (down && (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE)) {
                DesktopController.lockOpen.value = false
            }
            return true
        }
        val top = DesktopController.topWindow()
        if (metaDown || mods and 8 != 0) {
            if (down) {
                metaUsed = true
                when (keyCode) {
                    KeyEvent.KEYCODE_SPACE -> {
                        val next = !DesktopController.spotlightOpen.value
                        DesktopController.closeOverlays()
                        DesktopController.spotlightOpen.value = next
                    }
                    KeyEvent.KEYCODE_SLASH -> {
                        val next = !DesktopController.shortcutsOpen.value
                        DesktopController.closeOverlays()
                        DesktopController.shortcutsOpen.value = next
                    }
                    KeyEvent.KEYCODE_V -> if (mods and 1 != 0) {
                        val next = !DesktopController.clipboardOpen.value
                        DesktopController.closeOverlays()
                        DesktopController.clipboardOpen.value = next
                    }
                    KeyEvent.KEYCODE_L -> DesktopController.lockDesktop()
                    KeyEvent.KEYCODE_1 -> DesktopController.switchWorkspace(1)
                    KeyEvent.KEYCODE_2 -> DesktopController.switchWorkspace(2)
                    KeyEvent.KEYCODE_3 -> DesktopController.switchWorkspace(3)
                    KeyEvent.KEYCODE_4 -> DesktopController.switchWorkspace(4)
                    KeyEvent.KEYCODE_D -> DesktopController.minimizeAll()
                    KeyEvent.KEYCODE_DPAD_UP -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Top) }
                    KeyEvent.KEYCODE_DPAD_DOWN -> top?.let { DesktopController.minimize(it.id) }
                    KeyEvent.KEYCODE_DPAD_LEFT -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Left) }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> top?.let { DesktopController.snap(it.id, DesktopController.Snap.Right) }
                    KeyEvent.KEYCODE_TAB -> DesktopController.cycle()
                }
            }
            return true
        }
        if (mods and 4 != 0 && (keyCode == KeyEvent.KEYCODE_F4 || keyCode == KeyEvent.KEYCODE_TAB)) {
            if (down) {
                if (keyCode == KeyEvent.KEYCODE_F4) top?.let { DesktopController.close(it.id) } else DesktopController.cycle()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && (DesktopController.drawerOpen.value || DesktopController.spotlightOpen.value || DesktopController.shortcutsOpen.value || DesktopController.settingsOpen.value || DesktopController.clipboardOpen.value || DesktopController.contextMenu.value != null || DesktopController.quickPanelOpen.value)) {
            if (down) DesktopController.closeOverlays()
            return true
        }
        return false
    }

    private fun key(msg: JSONObject) {
        val keyCode = KeyMap.fromDomCode(msg.optString("code")) ?: return
        val mods = msg.optInt("m")
        if (shortcut(keyCode, msg.optString("a") == "down", mods)) return
        var meta = 0
        if (mods and 1 != 0) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (mods and 2 != 0) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (mods and 4 != 0) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (mods and 8 != 0) meta = meta or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        val down = msg.optString("a") == "down"
        val now = SystemClock.uptimeMillis()
        val dt = if (down) keyDownTimes.getOrPut(keyCode) { now } else keyDownTimes.remove(keyCode) ?: now
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        val repeat = msg.optInt("r")
        send { it.injectKey(displayId, action, keyCode, meta, dt, repeat) }
    }

    private fun send(block: (dev.kryvanta.olyvexis.shizuku.IShellService) -> Unit) {
        queue.trySend { ShizukuManager.withShell(block) }
    }

    // ------------------------------------------------------------ phone trackpad

    /** Relative cursor (trackpad): current position on desktop in pixels. */
    private var cursorX = width / 2f
    private var cursorY = height / 2f
    private var trackpadButtons = 0

    /** Show desktop-drawn cursor (trackpad/wired); browsers draw their own cursor. */
    var drawCursor = false

    private fun mouseAt(action: String, newButtons: Int, changedIndex: Int) {
        mouse(
            JSONObject()
                .put("a", action)
                .put("x", (cursorX / width).toDouble())
                .put("y", (cursorY / height).toDouble())
                .put("b", newButtons)
                .put("btn", changedIndex),
        )
        if (drawCursor) DesktopController.cursor.value = cursorX to cursorY
    }

    fun moveBy(dx: Float, dy: Float) {
        cursorX = (cursorX + dx).coerceIn(0f, width - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, height - 1f)
        mouseAt("move", trackpadButtons, -1)
    }

    fun moveTo(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, width - 1f)
        cursorY = y.coerceIn(0f, height - 1f)
        mouseAt("move", trackpadButtons, -1)
    }

    fun injectKey(keyCode: Int, pressed: Boolean, metaState: Int = 0) {
        val now = SystemClock.uptimeMillis()
        val dt = if (pressed) keyDownTimes.getOrPut(keyCode) { now } else keyDownTimes.remove(keyCode) ?: now
        val action = if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        send { it.injectKey(displayId, action, keyCode, metaState, dt, 0) }
    }

    fun injectText(text: String) {
        if (text.isEmpty()) return
        send { it.injectText(displayId, text) }
    }

    /** [button]: MotionEvent.BUTTON_PRIMARY / SECONDARY / TERTIARY. */
    fun press(button: Int, pressed: Boolean) {
        val index = when (button) {
            BUTTON_SECONDARY -> 2
            BUTTON_TERTIARY -> 1
            else -> 0
        }
        trackpadButtons = if (pressed) trackpadButtons or button else trackpadButtons and button.inv()
        mouseAt(if (pressed) "down" else "up", trackpadButtons, index)
    }

    fun scrollBy(dx: Float, dy: Float) {
        wheel(
            JSONObject()
                .put("x", (cursorX / width).toDouble())
                .put("y", (cursorY / height).toDouble())
                .put("dx", dx.toDouble())
                .put("dy", dy.toDouble()),
        )
    }

    fun close() {
        if (drawCursor) DesktopController.cursor.value = null
        queue.close()
        scope.cancel()
    }

    private companion object {
        const val TAG = "Olyvexis/Input"
        const val BUTTON_PRIMARY = MotionEvent.BUTTON_PRIMARY
        const val BUTTON_SECONDARY = MotionEvent.BUTTON_SECONDARY
        const val BUTTON_TERTIARY = MotionEvent.BUTTON_TERTIARY
    }
}

/** Browser KeyboardEvent.code -> Android KeyEvent.KEYCODE_* (physical layout, layout-independent). */
object KeyMap {
    private val map: Map<String, Int> = buildMap {
        for (c in 'A'..'Z') put("Key$c", KeyEvent.KEYCODE_A + (c - 'A'))
        for (d in 0..9) put("Digit$d", KeyEvent.KEYCODE_0 + d)
        for (d in 0..9) put("Numpad$d", KeyEvent.KEYCODE_NUMPAD_0 + d)
        for (f in 1..12) put("F$f", KeyEvent.KEYCODE_F1 + f - 1)
        put("Enter", KeyEvent.KEYCODE_ENTER); put("NumpadEnter", KeyEvent.KEYCODE_NUMPAD_ENTER)
        put("Backspace", KeyEvent.KEYCODE_DEL); put("Delete", KeyEvent.KEYCODE_FORWARD_DEL)
        put("Tab", KeyEvent.KEYCODE_TAB); put("Space", KeyEvent.KEYCODE_SPACE); put("Escape", KeyEvent.KEYCODE_ESCAPE)
        put("ArrowUp", KeyEvent.KEYCODE_DPAD_UP); put("ArrowDown", KeyEvent.KEYCODE_DPAD_DOWN)
        put("ArrowLeft", KeyEvent.KEYCODE_DPAD_LEFT); put("ArrowRight", KeyEvent.KEYCODE_DPAD_RIGHT)
        put("Home", KeyEvent.KEYCODE_MOVE_HOME); put("End", KeyEvent.KEYCODE_MOVE_END)
        put("PageUp", KeyEvent.KEYCODE_PAGE_UP); put("PageDown", KeyEvent.KEYCODE_PAGE_DOWN); put("Insert", KeyEvent.KEYCODE_INSERT)
        put("ShiftLeft", KeyEvent.KEYCODE_SHIFT_LEFT); put("ShiftRight", KeyEvent.KEYCODE_SHIFT_RIGHT)
        put("ControlLeft", KeyEvent.KEYCODE_CTRL_LEFT); put("ControlRight", KeyEvent.KEYCODE_CTRL_RIGHT)
        put("AltLeft", KeyEvent.KEYCODE_ALT_LEFT); put("AltRight", KeyEvent.KEYCODE_ALT_RIGHT)
        put("MetaLeft", KeyEvent.KEYCODE_META_LEFT); put("MetaRight", KeyEvent.KEYCODE_META_RIGHT)
        put("CapsLock", KeyEvent.KEYCODE_CAPS_LOCK)
        put("Minus", KeyEvent.KEYCODE_MINUS); put("Equal", KeyEvent.KEYCODE_EQUALS)
        put("BracketLeft", KeyEvent.KEYCODE_LEFT_BRACKET); put("BracketRight", KeyEvent.KEYCODE_RIGHT_BRACKET)
        put("Backslash", KeyEvent.KEYCODE_BACKSLASH); put("Semicolon", KeyEvent.KEYCODE_SEMICOLON)
        put("Quote", KeyEvent.KEYCODE_APOSTROPHE); put("Slash", KeyEvent.KEYCODE_SLASH)
        put("Comma", KeyEvent.KEYCODE_COMMA); put("Period", KeyEvent.KEYCODE_PERIOD); put("Backquote", KeyEvent.KEYCODE_GRAVE)
        put("NumpadAdd", KeyEvent.KEYCODE_NUMPAD_ADD); put("NumpadSubtract", KeyEvent.KEYCODE_NUMPAD_SUBTRACT)
        put("NumpadMultiply", KeyEvent.KEYCODE_NUMPAD_MULTIPLY); put("NumpadDivide", KeyEvent.KEYCODE_NUMPAD_DIVIDE)
        put("NumpadDecimal", KeyEvent.KEYCODE_NUMPAD_DOT)
    }

    fun fromDomCode(code: String): Int? = map[code]
}
