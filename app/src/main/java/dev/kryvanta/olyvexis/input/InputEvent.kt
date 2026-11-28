package dev.kryvanta.olyvexis.input

/** Mouse and keyboard input events shared between the phone trackpad and browser client. */
sealed interface InputEvent {
    /** Relative pointer movement (trackpad). */
    data class PointerMove(val dx: Float, val dy: Float) : InputEvent

    /** Absolute pointer position (browser), coordinates in target display pixels. */
    data class PointerAt(val x: Float, val y: Float) : InputEvent

    data class Button(val button: MouseButton, val pressed: Boolean) : InputEvent

    data class Scroll(val dx: Float, val dy: Float) : InputEvent

    /** [keyCode] is `KeyEvent.KEYCODE_*`; [metaState] combines Ctrl, Alt, Shift, and Meta. */
    data class Key(val keyCode: Int, val pressed: Boolean, val metaState: Int) : InputEvent

    data class Text(val text: String) : InputEvent
}

enum class MouseButton { Primary, Secondary, Middle }
