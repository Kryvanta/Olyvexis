package dev.kryvanta.olyvexis.stream

import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.kryvanta.olyvexis.desktop.DesktopActivity
import dev.kryvanta.olyvexis.desktop.DesktopController
import dev.kryvanta.olyvexis.desktop.LaunchableApp
import dev.kryvanta.olyvexis.display.DeviceCapabilities
import dev.kryvanta.olyvexis.display.VirtualDisplayController
import dev.kryvanta.olyvexis.input.ActiveInput
import dev.kryvanta.olyvexis.input.BrowserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

sealed interface StreamState {
    data object Idle : StreamState
    data object Starting : StreamState
    data class Running(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val port: Int,
        val pin: String,
        /** Các IPv4 trong mạng nội bộ (Wi-Fi, điểm phát Wi-Fi, chia sẻ qua USB...). Rỗng nếu không có. */
        val ips: List<String>,
        val clients: Int,
        /** Máy có freeform của hệ thống không (chỉ để biết; cửa sổ do Olyvexis tự vẽ nên không cần). */
        val freeform: Boolean,
        /** Khung hình/giây encoder đang dùng (60, hoặc 30 nếu encoder không chạy nổi 60). */
        val frameRate: Int,
        val httpsPort: Int?,
    ) : StreamState {
        val urls get() = ips.map { ip -> httpsPort?.let { "https://$ip:$it/" } ?: "http://$ip:$port/" }
        val httpUrls get() = ips.map { "http://$it:$port/" }
        val url get() = urls.firstOrNull()
        val localUrl get() = "http://localhost:$port/"
    }
    data class Failed(val message: String) : StreamState
}

/**
 * Một phiên Wi-Fi: màn hình ảo -> VideoEncoder (H.264) -> StreamServer -> trình duyệt.
 * Chỉ có một phiên mỗi lúc. StreamService giữ cho phiên chạy khi app vào nền.
 */
object StreamSession {
    private const val TAG = "Olyvexis/Session"

    /**
     * Kích thước desktop: Full HD như DeX nếu encoder của máy chạy được, không thì 1280x720.
     * Mật độ 160 dpi (1 dp = 1 px) cho giao diện dày như trên máy tính.
     */
    private val SIZES = listOf(1920 to 1080, 1280 to 720)
    const val DPI = 160

    private val _state = MutableStateFlow<StreamState>(StreamState.Idle)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var encoder: VideoEncoder? = null
    private var server: StreamServer? = null
    private var display: VirtualDisplayController? = null
    private var capabilities: DeviceCapabilities? = null
    private var input: BrowserInput? = null

    suspend fun start(context: Context) = mutex.withLock {
        if (_state.value is StreamState.Running) return@withLock
        _state.value = StreamState.Starting
        try {
            val pin = Random.nextInt(0, 10_000).toString().padStart(4, '0')
            val caps = DeviceCapabilities.detect(context).also { capabilities = it }
            // Ưu tiên Full HD nếu encoder chạy được 30 khung/giây trở lên.
            val (width, height, frameRate) = SIZES.firstNotNullOfOrNull { (w, h) ->
                VideoEncoder.supportedFrameRate(w, h)?.let { Triple(w, h, it) }
            } ?: error("Máy không có bộ mã hoá H.264 chạy được 1280x720")
            val bitRate = if (width >= 1920) 12_000_000 else 8_000_000
            val enc = VideoEncoder(width, height, bitRate = bitRate, frameRate = frameRate).also { encoder = it }
            val surface = enc.prepare()
            val ips = NetworkInfo.localIpv4s(context)
            val tls = runCatching { SelfSignedTls.create(ips) }.onFailure { Log.w(TAG, "Không tạo được TLS tự ký", it) }.getOrNull()
            val srv = StreamServer(
                assets = context.assets,
                pin = pin,
                width = width,
                height = height,
                sslContext = tls?.sslContext,
                onKeyFrameNeeded = { encoder?.requestKeyFrame() },
                onClientsChanged = { n ->
                    _state.update { if (it is StreamState.Running) it.copy(clients = n) else it }
                },
                // Chuột/bàn phím từ trình duyệt -> màn hình desktop.
                onClientMessage = { input?.onMessage(it) },
            ).also { server = it }
            srv.start()
            val vd = VirtualDisplayController().also { display = it }
            val id = vd.create(width, height, DPI, surface)
            input = BrowserInput(
                displayId = id,
                width = width,
                height = height,
                onResizeRequested = { w, h -> scope.launch { resize(w, h) } },
            ).also { ActiveInput.current = it }
            enc.start(srv::broadcast)
            // Mở giao diện desktop (thanh tác vụ, cửa sổ) lên màn hình vừa tạo.
            val desktop = vd.launch(ComponentName(context, DesktopActivity::class.java), freeform = false)
            if (!desktop.isSuccess || desktop.stdout.contains("Error")) {
                error("Không mở được desktop: ${desktop.stdout} ${desktop.stderr}".trim())
            }
            _state.value = StreamState.Running(
                displayId = id,
                width = width,
                height = height,
                port = srv.port,
                pin = pin,
                ips = ips,
                clients = 0,
                freeform = caps.freeform,
                frameRate = frameRate,
                httpsPort = srv.httpsPort,
            )
            Log.i(TAG, "Phiên chạy: màn hình #$id, cổng ${srv.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Không bắt đầu được phiên", e)
            stopLocked()
            _state.value = StreamState.Failed(e.message ?: e.toString())
        }
    }

    /** Đổi kích thước màn hình ảo theo cửa sổ trình duyệt (làm tròn chẵn và giới hạn an toàn). */
    suspend fun resize(requestedWidth: Int, requestedHeight: Int) = mutex.withLock {
        val cur = _state.value as? StreamState.Running ?: return@withLock
        val vd = display ?: return@withLock
        val srv = server ?: return@withLock
        val w = requestedWidth.coerceIn(800, 2560) and 1.inv()
        val h = requestedHeight.coerceIn(500, 1600) and 1.inv()
        if (w == cur.width && h == cur.height) return@withLock
        val fps = VideoEncoder.supportedFrameRate(w, h) ?: cur.frameRate
        val bitRate = if (w * h >= 1920 * 1080) 12_000_000 else 8_000_000
        try {
            val newEnc = VideoEncoder(w, h, bitRate = bitRate, frameRate = fps)
            val newSurface = newEnc.prepare()
            vd.setSurface(newSurface)
            vd.resize(w, h, DPI)
            encoder?.stop()
            encoder = newEnc
            input?.updateSize(w, h)
            newEnc.start(srv::broadcast)
            srv.notifyResized(w, h)
            _state.update { if (it is StreamState.Running) it.copy(width = w, height = h, frameRate = fps) else it }
            Log.i(TAG, "Đã đổi cỡ desktop sang ${w}x$h @ $fps fps")
        } catch (e: Exception) {
            Log.w(TAG, "Không đổi được cỡ sang ${w}x$h", e)
        }
    }

    fun refreshIps(context: Context) {
        val ips = NetworkInfo.localIpv4s(context)
        _state.update { if (it is StreamState.Running) it.copy(ips = ips) else it }
    }

    suspend fun stop() = mutex.withLock {
        stopLocked()
        _state.value = StreamState.Idle
    }

    private suspend fun stopLocked() {
        DesktopController.closeAll()
        if (ActiveInput.current === input) ActiveInput.current = null
        input?.close()
        input = null
        // Giải phóng màn hình ảo trước, để nó không còn vẽ vào surface của encoder.
        display?.release()
        display = null
        encoder?.stop()
        encoder = null
        server?.stop()
        server = null
    }

    /** Mở app trong một cửa sổ trên desktop. */
    fun openWindow(app: LaunchableApp) {
        check(_state.value is StreamState.Running) { "Phiên chưa chạy" }
        DesktopController.open(app)
    }
}
