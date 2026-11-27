package dev.kryvanta.olyvexis.shizuku

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku UserService: chạy trong một tiến trình riêng do Shizuku tạo (`dev.kryvanta.olyvexis:shell`),
 * với uid shell.
 *
 * Shizuku truyền vào một [Context] (từ API 13). Không dùng nó trực tiếp để gọi dịch vụ hệ thống
 * mà bọc trong [ShellContext], vì hệ thống đòi tên gói khớp với uid shell.
 */
class ShellService(context: Context) : IShellService.Stub() {
    private val shellContext = ShellContext(context)
    private val displays = ConcurrentHashMap<Int, VirtualDisplay>()

    /** DisplayManager dùng [ShellContext]. Constructor DisplayManager(Context) bị ẩn nên gọi qua reflection. */
    private val displayManager: DisplayManager by lazy {
        val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
        ctor.isAccessible = true
        ctor.newInstance(shellContext)
    }

    /**
     * InputManager ẩn để tiêm sự kiện (shell có quyền INJECT_EVENTS). Từ Android 14 hàm nằm ở
     * InputManagerGlobal; trước đó ở InputManager.getInstance().
     */
    private val injector: Pair<Any, Method> by lazy {
        val manager = runCatching {
            Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)
        }.getOrNull() ?: InputManager::class.java.getMethod("getInstance").invoke(null)!!
        val inject = manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        manager to inject
    }

    private val setDisplayId: Method by lazy {
        InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
    }

    private fun inject(displayId: Int, event: InputEvent): Boolean = try {
        setDisplayId.invoke(event, displayId)
        val (manager, method) = injector
        method.invoke(manager, event, INJECT_MODE_ASYNC) as Boolean
    } catch (e: Exception) {
        Log.w(TAG, "Không tiêm được sự kiện vào màn hình $displayId", e)
        false
    }

    override fun injectMotionEvent(displayId: Int, event: MotionEvent): Boolean = inject(displayId, event)

    override fun injectKeyEvent(displayId: Int, event: KeyEvent): Boolean = inject(displayId, event)

    /** MotionEvent.setActionButton bị ẩn; gọi được trong tiến trình shell. */
    private val setActionButton: Method? by lazy {
        runCatching { MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType) }.getOrNull()
    }

    override fun injectMouse(
        displayId: Int,
        action: Int,
        x: Float,
        y: Float,
        buttons: Int,
        actionButton: Int,
        downTime: Long,
        vscroll: Float,
        hscroll: Float,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = if (buttons != 0) 1f else 0f
            size = 1f
            if (action == MotionEvent.ACTION_SCROLL) {
                setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll)
                setAxisValue(MotionEvent.AXIS_HSCROLL, hscroll)
            }
        }
        val event = MotionEvent.obtain(
            if (downTime > 0) downTime else now, now, action, 1, arrayOf(props), arrayOf(coords),
            0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        if (actionButton != 0) runCatching { setActionButton?.invoke(event, actionButton) }
        return inject(displayId, event).also { event.recycle() }
    }

    override fun injectKey(displayId: Int, action: Int, keyCode: Int, metaState: Int, downTime: Long, repeat: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            if (downTime > 0) downTime else now, now, action, keyCode, repeat, metaState,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD,
        )
        return inject(displayId, event)
    }

    /**
     * ClipboardManager gắn với ShellContext (tên gói com.android.shell). Không dùng
     * getSystemService: nó tạo manager theo context gốc (tên gói của app) và hệ thống từ chối vì
     * không khớp uid shell. Constructor ClipboardManager(Context, Handler) bị ẩn.
     */
    private val clipboard: ClipboardManager? by lazy {
        runCatching {
            val looper = Looper.myLooper() ?: Looper.getMainLooper() ?: run { Looper.prepare(); Looper.myLooper()!! }
            ClipboardManager::class.java
                .getDeclaredConstructor(Context::class.java, Handler::class.java)
                .apply { isAccessible = true }
                .newInstance(shellContext, Handler(looper))
        }.onFailure { Log.w(TAG, "Không tạo được ClipboardManager", it) }.getOrNull()
    }

    override fun injectText(displayId: Int, text: String): Boolean {
        // Ký tự ASCII gõ bằng phím thật (app nào cũng hiểu).
        val map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = map.getEvents(text.toCharArray())
        if (events != null) return events.all { inject(displayId, it) }
        // Chữ khác (tiếng Việt có dấu...): Android không cho tiêm KeyEvent ACTION_MULTIPLE nữa, nên
        // đặt chữ vào clipboard rồi gửi phím PASTE, giống scrcpy. Clipboard của máy bị ghi đè.
        val cm = clipboard ?: return false
        return try {
            cm.setPrimaryClip(ClipData.newPlainText("Olyvexis", text))
            val now = SystemClock.uptimeMillis()
            listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).all { action ->
                inject(
                    displayId,
                    KeyEvent(now, now, action, KeyEvent.KEYCODE_PASTE, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không dán được chữ", e)
            false
        }
    }

    override fun destroy() {
        Log.i(TAG, "destroy")
        displays.values.forEach { runCatching { it.release() } }
        displays.clear()
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun exec(command: String, timeoutMs: Long): ShellResult = runShell(command, timeoutMs)

    override fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface?,
        systemDecorations: Boolean,
    ): Int {
        // Thử bộ cờ đầy đủ trước; nếu bị từ chối (Android cũ, ROM lạ, hoặc thiếu quyền
        // ADD_TRUSTED_DISPLAY) thì bỏ dần các cờ ẩn. Màn hình không TRUSTED có thể không mở được
        // app khác lên, nhưng vẫn hơn là không tạo được gì.
        val attempts = listOf(
            virtualDisplayFlags(systemDecorations),
            FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or FLAG_DESTROY_CONTENT_ON_REMOVAL or FLAG_TRUSTED,
            FLAG_PUBLIC or FLAG_OWN_CONTENT_ONLY,
        ).distinct()
        var display: VirtualDisplay? = null
        var lastError: Exception? = null
        for (flags in attempts) {
            Log.i(TAG, "createVirtualDisplay $name ${width}x$height@$densityDpi flags=0x${flags.toString(16)}")
            try {
                display = displayManager.createVirtualDisplay(name, width, height, densityDpi, surface, flags)
                if (display != null) break
            } catch (e: Exception) {
                Log.w(TAG, "Bị từ chối với flags=0x${flags.toString(16)}", e)
                lastError = e
            }
        }
        if (display == null) {
            throw IllegalStateException("Không tạo được màn hình ảo: ${lastError ?: "createVirtualDisplay trả null"}")
        }
        val id = display.display.displayId
        displays[id] = display
        return id
    }

    override fun resizeVirtualDisplay(displayId: Int, width: Int, height: Int, densityDpi: Int) {
        displays[displayId]?.resize(width, height, densityDpi)
    }

    override fun setVirtualDisplaySurface(displayId: Int, surface: Surface?) {
        displays[displayId]?.surface = surface
    }

    override fun releaseVirtualDisplay(displayId: Int) {
        displays.remove(displayId)?.release()
    }

    override fun startActivityOnDisplay(component: String, displayId: Int, freeform: Boolean): ShellResult {
        // Dùng `am start` với quyền shell để tương thích ổn định qua mọi phiên bản Android 11–17.
        val mode = if (freeform) " --windowingMode $WINDOWING_MODE_FREEFORM" else ""
        return runShell("am start --display $displayId$mode -n $component", 10_000)
    }

    override fun launchAppInWindow(component: String, displayId: Int): ShellResult {
        val pkg = component.substringBefore('/')
        // App đã chạy trên điện thoại: `am start --display` chỉ đưa task cũ lên ở màn hình cũ
        // (vd. Chrome), nên chuyển task sang màn hình cửa sổ trước.
        moveTasksToDisplay(pkg, displayId)
        // Toàn màn hình trong màn hình ảo của cửa sổ (không để hệ thống mở thành cửa sổ nổi).
        val result = runShell("am start --display $displayId --windowingMode 1 -n $component", 10_000)
        // App có màn hình chờ (splash) tự mở activity chính, activity đó có thể nhảy về task cũ
        // trên điện thoại (vd. App Market). Kiểm tra vài lần và kéo về cửa sổ.
        repeat(4) {
            Thread.sleep(350)
            moveTasksToDisplay(pkg, displayId)
        }
        return result
    }

    /** Chuyển mọi task của [pkg] đang nằm trên màn hình điện thoại (display 0) sang [displayId]. */
    private fun moveTasksToDisplay(pkg: String, displayId: Int) {
        val list = runShell("am stack list", 5_000).stdout
        var rootId = -1
        var rootDisplay = -1
        val toMove = linkedSetOf<Int>()
        for (line in list.lineSequence()) {
            val root = ROOT_TASK.find(line)
            if (root != null) {
                rootId = root.groupValues[1].toInt()
                rootDisplay = root.groupValues[2].toInt()
                continue
            }
            if (rootDisplay == 0 && line.trimStart().startsWith("taskId=") && line.contains(": $pkg/")) {
                toMove += rootId
            }
        }
        for (id in toMove) {
            Log.i(TAG, "Chuyển task $id của $pkg sang màn hình $displayId")
            runShell("am display move-stack $id $displayId", 5_000)
        }
    }

    private fun virtualDisplayFlags(systemDecorations: Boolean): Int {
        // Không có FLAG_PUBLIC: màn hình riêng (private) của shell. Khi bật
        // force_desktop_mode_on_external_displays (cách qua cáp), Android áp chế độ desktop cho mọi
        // màn hình phụ công khai: gắn launcher + thanh điều hướng, mở app thành cửa sổ nổi bên trong
        // cửa sổ của Olyvexis. Màn hình riêng thì không bị; shell là chủ nên vẫn mở app lên được.
        var flags = FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or
            FLAG_DESTROY_CONTENT_ON_REMOVAL or FLAG_TRUSTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or FLAG_OWN_DISPLAY_GROUP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags = flags or FLAG_ALWAYS_UNLOCKED or FLAG_TOUCH_FEEDBACK_DISABLED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            flags = flags or FLAG_OWN_FOCUS or FLAG_DEVICE_DISPLAY_GROUP
        }
        if (systemDecorations) flags = flags or FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
        return flags
    }

    private fun runShell(command: String, timeoutMs: Long): ShellResult = try {
        val process = ProcessBuilder("sh", "-c", command).start()
        process.outputStream.close()

        // Đọc stdout và stderr song song để tiến trình không bị nghẽn khi bộ đệm đầy.
        var stdout = ""
        var stderr = ""
        val outReader = thread(name = "shell-stdout") { stdout = process.inputStream.bufferedReader().readText() }
        val errReader = thread(name = "shell-stderr") { stderr = process.errorStream.bufferedReader().readText() }

        if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            outReader.join()
            errReader.join()
            ShellResult(process.exitValue(), stdout.trimEnd(), stderr.trimEnd())
        } else {
            process.destroyForcibly()
            outReader.join(500)
            errReader.join(500)
            ShellResult(ShellResult.EXIT_TIMEOUT, stdout.trimEnd(), "Quá thời gian ${timeoutMs} ms")
        }
    } catch (e: Exception) {
        Log.w(TAG, "exec lỗi: $command", e)
        ShellResult(ShellResult.EXIT_ERROR, "", e.toString())
    }

    private companion object {
        const val TAG = "Olyvexis/Shell"

        // Cờ VIRTUAL_DISPLAY_FLAG_* của DisplayManager (nhiều cờ bị ẩn khỏi SDK).
        const val FLAG_PUBLIC = 1 shl 0
        const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
        const val FLAG_SUPPORTS_TOUCH = 1 shl 6
        const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        const val FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 9
        const val FLAG_TRUSTED = 1 shl 10
        const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11
        const val FLAG_ALWAYS_UNLOCKED = 1 shl 12
        const val FLAG_TOUCH_FEEDBACK_DISABLED = 1 shl 13
        const val FLAG_OWN_FOCUS = 1 shl 14
        const val FLAG_DEVICE_DISPLAY_GROUP = 1 shl 15

        val ROOT_TASK = Regex("""RootTask id=(\d+) .*displayId=(\d+)""")

        // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC (ẩn).
        const val INJECT_MODE_ASYNC = 0

        // WindowConfiguration.WINDOWING_MODE_FREEFORM (ẩn).
        const val WINDOWING_MODE_FREEFORM = 5
    }
}
