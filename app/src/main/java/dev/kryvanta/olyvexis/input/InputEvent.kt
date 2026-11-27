package dev.kryvanta.olyvexis.input

/** Thao tác chuột/bàn phím, chung cho trackpad trên điện thoại và trình duyệt bên kia. */
sealed interface InputEvent {
    /** Di chuột tương đối (trackpad). */
    data class PointerMove(val dx: Float, val dy: Float) : InputEvent

    /** Đặt chuột tuyệt đối (trình duyệt), toạ độ theo màn hình đích. */
    data class PointerAt(val x: Float, val y: Float) : InputEvent

    data class Button(val button: MouseButton, val pressed: Boolean) : InputEvent

    data class Scroll(val dx: Float, val dy: Float) : InputEvent

    /** [keyCode] là KeyEvent.KEYCODE_*; [metaState] gồm Ctrl, Alt, Shift, Meta. */
    data class Key(val keyCode: Int, val pressed: Boolean, val metaState: Int) : InputEvent

    data class Text(val text: String) : InputEvent
}

enum class MouseButton { Primary, Secondary, Middle }
