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

/** Shizuku connection state in progression order. */
enum class ShizukuState {
    /** Shizuku is not installed or its service is not running (no binder). */
    NotRunning,
    /** Shizuku version is too old (pre-API 11) and must be updated. */
    Outdated,
    /** Shizuku is running but the app has not been granted permission. */
    NoPermission,
    /** Permission was denied with "Don't ask again"; must be granted manually in Shizuku. */
    PermissionDenied,
    /** Permission granted and ready to execute shell commands. */
    Ready,
}

class ShizukuException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Connects to Shizuku, requests permission, and executes shell commands via [ShellService].
 *
 * When Shizuku is started via Wireless Debugging on-device, it must be re-enabled after every
 * reboot. The binder may drop at any time; when it does, [service] is cleared and the next
 * [runCommand] call rebinds.
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

    /** Initialized in [init] because [version][Shizuku.UserServiceArgs.version] uses APK install time. */
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
        // Shizuku reuses the old ShellService process if version is unchanged. Use APK install time
        // so every fresh install (including debug builds with the same versionCode) runs new code.
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
            // Binder can die between the calls above.
            Log.w(TAG, "Không kiểm tra được Shizuku", e)
            ShizukuState.NotRunning
        }
    }

    /**
     * UID Shizuku is running as: 2000 (shell, via Wireless Debugging/USB) or 0 (root).
     * Olyvexis targets shell; under root, ShellContext's `com.android.shell` package name does not
     * match UID 0, so the system may reject virtual display creation.
     */
    fun serverUid(): Int? = runCatching { Shizuku.getUid() }.getOrNull()

    fun requestPermission() {
        refresh()
        if (_state.value == ShizukuState.NoPermission) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Executes a shell command with shell privileges (UID 2000) via `sh -c`.
     * Does not throw on non-zero exit codes; throws [ShizukuException] only if Shizuku fails.
     */
    suspend fun runCommand(
        command: String,
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
    ): ShellResult = withShell { it.exec(command, timeoutMs) }

    /**
     * Invokes [block] with [IShellService] on the IO dispatcher. Binder errors or remote
     * exceptions from ShellService are wrapped in [ShizukuException].
     */
    suspend fun <T> withShell(block: (IShellService) -> T): T = withContext(Dispatchers.IO) {
        val shell = shellService()
        try {
            block(shell)
        } catch (e: android.os.RemoteException) {
            synchronized(lock) { if (service === shell) service = null }
            throw ShizukuException("Mất kết nối ShellService", e)
        } catch (e: RuntimeException) {
            // Exceptions thrown by ShellService arrive across Binder as RuntimeException (e.g. IllegalStateException).
            throw ShizukuException(e.message ?: "ShellService báo lỗi", e)
        }
    }

    /** Returns [IShellService], binding if not yet connected. */
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

    /** Stops the ShellService process (e.g. when the user shuts down Olyvexis). */
    fun unbind() {
        synchronized(lock) { service = null }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
    }
}
