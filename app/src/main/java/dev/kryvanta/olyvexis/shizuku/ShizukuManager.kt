package dev.kryvanta.olyvexis.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import dev.kryvanta.olyvexis.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** Trạng thái kết nối Shizuku, theo thứ tự cần đi qua. */
enum class ShizukuState {
    /** Chưa cài hoặc chưa chạy dịch vụ Shizuku (chưa có binder). */
    NotRunning,
    /** Bản Shizuku quá cũ (trước API 11), cần cập nhật. */
    Outdated,
    /** Shizuku chạy nhưng app chưa được cấp quyền. */
    NoPermission,
    /** Người dùng đã từ chối và chọn không hỏi lại: phải cấp tay trong app Shizuku. */
    PermissionDenied,
    /** Đã cấp quyền, sẵn sàng chạy lệnh với quyền shell. */
    Ready,
}

class ShizukuException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Kết nối Shizuku, xin quyền và chạy lệnh shell qua [ShellService].
 *
 * Shizuku được kích hoạt bằng Gỡ lỗi không dây ngay trên máy, nên mỗi lần khởi động lại
 * máy người dùng phải bật lại. Binder có thể mất bất cứ lúc nào; khi đó [service] bị xoá
 * và lần gọi [runCommand] sau sẽ bind lại.
 */
object ShizukuManager {
    private const val TAG = "Olyvexis/Shizuku"
    const val PERMISSION_REQUEST_CODE = 1001
    private const val BIND_TIMEOUT_MS = 10_000L
    const val DEFAULT_COMMAND_TIMEOUT_MS = 15_000L

    private val _state = MutableStateFlow(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val lock = Any()
    private var service: IShellService? = null
    private var pendingService: CompletableDeferred<IShellService>? = null

    /** Tạo trong [init], vì [version][Shizuku.UserServiceArgs.version] cần biết thời điểm cài APK. */
    private lateinit var userServiceArgs: Shizuku.UserServiceArgs

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                Log.w(TAG, "ShellService trả binder không hợp lệ")
                failPending(ShizukuException("ShellService không phản hồi"))
                return
            }
            val shell = IShellService.Stub.asInterface(binder)
            synchronized(lock) {
                service = shell
                pendingService?.complete(shell)
                pendingService = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "ShellService ngắt kết nối")
            synchronized(lock) { service = null }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        Log.i(TAG, "Mất binder Shizuku")
        synchronized(lock) { service = null }
        failPending(ShizukuException("Shizuku đã dừng"))
        _state.value = ShizukuState.NotRunning
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) refresh()
        }

    fun init(context: Context) {
        // Shizuku dùng lại tiến trình ShellService cũ nếu version không đổi. Lấy theo thời điểm cài
        // APK để mỗi lần cài bản mới (kể cả bản debug cùng versionCode) đều chạy code mới.
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        userServiceArgs = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE * 31 + (installed / 1000).toInt())
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _state.value = try {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NotRunning
                Shizuku.isPreV11() -> ShizukuState.Outdated
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.Ready
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuState.PermissionDenied
                else -> ShizukuState.NoPermission
            }
        } catch (e: Exception) {
            // Binder có thể chết giữa chừng các lệnh gọi trên.
            Log.w(TAG, "Không kiểm tra được Shizuku", e)
            ShizukuState.NotRunning
        }
    }

    /**
     * uid mà Shizuku đang chạy: 2000 (shell, bật bằng Gỡ lỗi không dây/USB) hoặc 0 (root).
     * Olyvexis được làm và thử với shell; với root, ShellContext khai tên gói com.android.shell
     * không khớp uid 0 nên hệ thống có thể từ chối tạo màn hình ảo.
     */
    fun serverUid(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    fun requestPermission() {
        refresh()
        if (_state.value == ShizukuState.NoPermission) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Chạy một lệnh shell với quyền shell (uid 2000), qua `sh -c`.
     * Không ném lỗi khi lệnh trả mã khác 0; chỉ ném [ShizukuException] khi không gọi được Shizuku.
     */
    suspend fun runCommand(
        command: String,
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
    ): ShellResult = withShell { it.exec(command, timeoutMs) }

    /**
     * Gọi [block] với [IShellService] trên luồng IO. Lỗi binder (mất kết nối, hoặc service ném
     * lỗi như khi hệ thống từ chối tạo màn hình ảo) được gói thành [ShizukuException].
     */
    suspend fun <T> withShell(block: (IShellService) -> T): T = withContext(Dispatchers.IO) {
        val shell = shellService()
        try {
            block(shell)
        } catch (e: android.os.RemoteException) {
            synchronized(lock) { if (service === shell) service = null }
            throw ShizukuException("Mất kết nối ShellService", e)
        } catch (e: RuntimeException) {
            // Lỗi ném từ ShellService được gửi qua binder dưới dạng RuntimeException (vd. IllegalStateException).
            throw ShizukuException(e.message ?: "ShellService báo lỗi", e)
        }
    }

    /** Lấy [IShellService], bind nếu chưa có. */
    private suspend fun shellService(): IShellService {
        refresh()
        if (_state.value != ShizukuState.Ready) {
            throw ShizukuException("Shizuku chưa sẵn sàng: ${_state.value}")
        }
        val deferred = synchronized(lock) {
            service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
            service = null
            pendingService ?: CompletableDeferred<IShellService>().also {
                pendingService = it
                try {
                    Shizuku.bindUserService(userServiceArgs, connection)
                } catch (e: Exception) {
                    pendingService = null
                    throw ShizukuException("Không bind được ShellService", e)
                }
            }
        }
        return try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            failPending(ShizukuException("Hết thời gian chờ ShellService"))
            throw ShizukuException("Hết thời gian chờ ShellService", e)
        }
    }

    private fun failPending(error: Throwable) {
        synchronized(lock) {
            pendingService?.completeExceptionally(error)
            pendingService = null
        }
    }

    /** Dừng tiến trình ShellService (vd. khi người dùng tắt Olyvexis). */
    fun unbind() {
        synchronized(lock) { service = null }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
    }
}
