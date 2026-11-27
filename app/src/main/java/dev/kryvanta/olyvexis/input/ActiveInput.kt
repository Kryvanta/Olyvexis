package dev.kryvanta.olyvexis.input

import android.content.Context
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cài đặt trackpad được lưu lại (giống InputSettings của iDesk).
 */
object InputSettings {
    private const val PREFS = "input_settings"
    const val DEFAULT_POINTER_SPEED = 1.6f
    const val DEFAULT_SCROLL_SPEED = 1.4f

    private val _pointerSpeed = MutableStateFlow(DEFAULT_POINTER_SPEED)
    val pointerSpeed = _pointerSpeed.asStateFlow()

    private val _scrollSpeed = MutableStateFlow(DEFAULT_SCROLL_SPEED)
    val scrollSpeed = _scrollSpeed.asStateFlow()

    private val _naturalScrolling = MutableStateFlow(true)
    val naturalScrolling = _naturalScrolling.asStateFlow()

    private val _pointerAcceleration = MutableStateFlow(true)
    val pointerAcceleration = _pointerAcceleration.asStateFlow()

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _pointerSpeed.value = p.getFloat("pointer_speed", DEFAULT_POINTER_SPEED)
        _scrollSpeed.value = p.getFloat("scroll_speed", DEFAULT_SCROLL_SPEED)
        _naturalScrolling.value = p.getBoolean("natural_scrolling", true)
        _pointerAcceleration.value = p.getBoolean("pointer_accel", true)
    }

    fun setPointerSpeed(context: Context, value: Float) {
        val v = value.coerceIn(0.6f, 4.0f)
        _pointerSpeed.value = v
        prefs(context).edit().putFloat("pointer_speed", v).apply()
    }

    fun setScrollSpeed(context: Context, value: Float) {
        val v = value.coerceIn(0.5f, 4.0f)
        _scrollSpeed.value = v
        prefs(context).edit().putFloat("scroll_speed", v).apply()
    }

    fun setNaturalScrolling(context: Context, natural: Boolean) {
        _naturalScrolling.value = natural
        prefs(context).edit().putBoolean("natural_scrolling", natural).apply()
    }

    fun setPointerAcceleration(context: Context, enabled: Boolean) {
        _pointerAcceleration.value = enabled
        prefs(context).edit().putBoolean("pointer_accel", enabled).apply()
    }

    fun reset(context: Context) {
        setPointerSpeed(context, DEFAULT_POINTER_SPEED)
        setScrollSpeed(context, DEFAULT_SCROLL_SPEED)
        setNaturalScrolling(context, true)
        setPointerAcceleration(context, true)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Đầu vào của phiên đang chạy (Wi-Fi hoặc qua cáp), để màn hình trackpad trên điện thoại biết
 * gửi thao tác vào đâu.
 */
object ActiveInput {
    @Volatile var current: BrowserInput? = null

    fun handle(event: InputEvent) {
        val input = current ?: return
        val speed = InputSettings.pointerSpeed.value
        val scrollGain = InputSettings.scrollSpeed.value * (if (InputSettings.naturalScrolling.value) 1f else -1f)
        when (event) {
            is InputEvent.PointerMove -> input.moveBy(event.dx * speed, event.dy * speed)
            is InputEvent.PointerAt -> input.moveTo(event.x, event.y)
            is InputEvent.Button -> input.press(
                when (event.button) {
                    MouseButton.Primary -> MotionEvent.BUTTON_PRIMARY
                    MouseButton.Secondary -> MotionEvent.BUTTON_SECONDARY
                    MouseButton.Middle -> MotionEvent.BUTTON_TERTIARY
                },
                event.pressed,
            )
            is InputEvent.Scroll -> input.scrollBy(event.dx * scrollGain, event.dy * scrollGain)
            is InputEvent.Key -> input.injectKey(event.keyCode, event.pressed, event.metaState)
            is InputEvent.Text -> input.injectText(event.text)
        }
    }
}
