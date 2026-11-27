package dev.kryvanta.olyvexis.stream

import android.content.res.AssetManager
import android.util.Log
import dev.kryvanta.olyvexis.stream.WebSocket.writeFrame
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import kotlin.concurrent.thread

/**
 * Server HTTP/WebSocket + HTTPS/WSS trong mạng nội bộ:
 * - HTTP/WS ở [port] (mặc định 8765): dùng cho `http://localhost` (`adb forward`) và dự phòng MSE
 *   trên trình duyệt TV / `http://<ip-lan>`.
 * - HTTPS/WSS ở [httpsPort] (khi có [sslContext]): dùng chứng chỉ tự ký cho `https://<ip-lan>`,
 *   tạo Secure Context để WebCodecs chạy trực tiếp qua Wi-Fi sau khi xác nhận cảnh báo một lần.
 */
class StreamServer(
    private val assets: AssetManager,
    val pin: String,
    private var width: Int,
    private var height: Int,
    /** Cổng muốn dùng; nếu bận thì thử các cổng kế tiếp. Cổng thật nằm ở [port] sau [start]. */
    private val preferredPort: Int = DEFAULT_PORT,
    private val sslContext: SSLContext? = null,
    private val onKeyFrameNeeded: () -> Unit,
    private val onClientsChanged: (Int) -> Unit,
    private val onClientMessage: (String) -> Unit = {},
) {
    companion object {
        const val DEFAULT_PORT = 8765
        private const val TAG = "Olyvexis/Server"
        private const val FLAG_CONFIG = 1
        private const val FLAG_KEY = 2

        /** Số gói tối đa xếp hàng cho mỗi client; đầy thì bỏ tới keyframe kế tiếp. */
        private const val CLIENT_QUEUE = 60

        /** Giới hạn bộ nhớ cho nhóm khung (GOP) giữ lại cho client mới. */
        private const val GOP_CACHE_BYTES = 8 * 1024 * 1024
    }

    private var serverSocket: ServerSocket? = null
    private var tlsServerSocket: SSLServerSocket? = null

    var port: Int = preferredPort
        private set
    var httpsPort: Int? = null
        private set
    private val clients = CopyOnWriteArrayList<Client>()

    /** SPS/PPS gần nhất, ghép vào trước mỗi keyframe để client mới giải mã được ngay. */
    @Volatile private var config: ByteArray? = null

    private val gop = ArrayList<ByteArray>()
    private var gopBytes = 0
    private val lock = Any()

    fun start() {
        val socket = bindFirstFree(preferredPort, attempts = 10) { ServerSocket() }
        port = socket.localPort
        serverSocket = socket
        acceptLoop("http-accept", socket)

        sslContext?.let { ssl ->
            runCatching {
                val tls = bindFirstFree(port + 1, attempts = 10) {
                    ssl.serverSocketFactory.createServerSocket() as SSLServerSocket
                }
                httpsPort = tls.localPort
                tlsServerSocket = tls
                acceptLoop("https-accept", tls)
            }.onFailure { Log.w(TAG, "Không mở được cổng HTTPS", it) }
        }
        Log.i(TAG, "Đang nghe HTTP :$port" + (httpsPort?.let { ", HTTPS :$it" } ?: ""))
    }

    private fun acceptLoop(name: String, socket: ServerSocket) {
        thread(name = name) {
            while (!socket.isClosed) {
                val conn = try {
                    socket.accept()
                } catch (_: SocketException) {
                    break
                }
                thread(name = "$name-conn") { handle(conn) }
            }
        }
    }

    /** Mở cổng đầu tiên còn trống trong [start]..[start]+attempts-1 (cổng có thể bị app khác chiếm). */
    private fun <T : ServerSocket> bindFirstFree(start: Int, attempts: Int, factory: () -> T): T {
        var lastError: Exception? = null
        for (p in start until start + attempts) {
            val socket = factory()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(p))
                return socket
            } catch (e: java.io.IOException) {
                runCatching { socket.close() }
                lastError = e
            }
        }
        throw IllegalStateException("Không mở được cổng $start–${start + attempts - 1}: $lastError")
    }

    fun stop() {
        synchronized(lock) {
            gop.clear()
            gopBytes = 0
        }
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { tlsServerSocket?.close() }
        tlsServerSocket = null
        httpsPort = null
        clients.forEach { it.close(WebSocket.CLOSE_SERVER_STOPPED) }
        clients.clear()
        onClientsChanged(0)
    }

    val clientCount get() = clients.size

    private fun helloJson(): ByteArray {
        val httpsPart = httpsPort?.let { ""","httpsPort":$it""" } ?: ""
        return """{"type":"hello","width":$width,"height":$height$httpsPart}""".toByteArray()
    }

    /** Cập nhật kích thước desktop mới cho các client hiện tại và client mới sau này. */
    fun notifyResized(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        synchronized(lock) {
            gop.clear()
            gopBytes = 0
        }
        val frame = WebSocket.frame(WebSocket.OP_TEXT, helloJson())
        for (client in clients) {
            client.waitingForKey = true
            client.queue.offer(frame)
        }
        onKeyFrameNeeded()
    }

    /** Gửi một gói video tới mọi client. Gọi từ luồng encoder. */
    fun broadcast(packet: EncodedPacket) {
        if (packet.isConfig) {
            config = packet.data
            return
        }
        val cfg = config
        val payload = if (packet.isKeyFrame && cfg != null) cfg + packet.data else packet.data
        val flags = (if (packet.isKeyFrame) FLAG_KEY else 0) or (if (packet.isKeyFrame && cfg != null) FLAG_CONFIG else 0)
        val message = ByteBuffer.allocate(9 + payload.size).put(flags.toByte()).putLong(packet.ptsUs).put(payload).array()
        val frame = WebSocket.frame(WebSocket.OP_BINARY, message)

        var needKey = false
        synchronized(lock) {
            if (packet.isKeyFrame) {
                gop.clear()
                gopBytes = 0
            }
            if (packet.isKeyFrame || gop.isNotEmpty()) {
                if (gopBytes + frame.size <= GOP_CACHE_BYTES) {
                    gop += frame
                    gopBytes += frame.size
                } else {
                    // Quá lớn: bỏ, client mới sẽ phải chờ keyframe kế tiếp.
                    gop.clear()
                    gopBytes = 0
                }
            }
            for (client in clients) {
                if (client.waitingForKey && !packet.isKeyFrame) continue
                if (client.queue.offer(frame)) {
                    if (packet.isKeyFrame) client.waitingForKey = false
                } else {
                    // Client không theo kịp: bỏ các gói đang chờ, đợi keyframe mới.
                    client.queue.clear()
                    client.waitingForKey = true
                    needKey = true
                }
            }
        }
        if (needKey) onKeyFrameNeeded()
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            val request = readRequest(input) ?: return socket.close()
            if (request.headers["upgrade"]?.equals("websocket", ignoreCase = true) == true && request.path == "/ws") {
                handleWebSocket(socket, input, output, request)
            } else {
                serveFile(output, request)
                socket.close()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kết nối lỗi: $e")
            runCatching { socket.close() }
        }
    }

    private fun handleWebSocket(socket: Socket, input: InputStream, output: OutputStream, request: Request) {
        val key = request.headers["sec-websocket-key"] ?: return socket.close()
        output.write(WebSocket.handshakeResponse(key).toByteArray())
        output.flush()

        if (request.query["pin"] != pin) {
            Log.i(TAG, "Sai PIN từ ${socket.inetAddress.hostAddress}")
            output.writeFrame(WebSocket.closeFrame(WebSocket.CLOSE_WRONG_PIN, "wrong pin"))
            socket.close()
            return
        }

        val hello = WebSocket.frame(WebSocket.OP_TEXT, helloJson())
        val client = synchronized(lock) {
            // Gửi trước nhóm khung đang giữ, rồi mới nhận gói mới: không sót và không trùng gói.
            Client(socket, output, listOf(hello) + gop).also {
                it.waitingForKey = gop.isEmpty()
                clients += it
            }
        }
        onClientsChanged(clients.size)
        Log.i(TAG, "Client mới: ${socket.inetAddress.hostAddress}")
        client.startWriter()
        if (client.waitingForKey) onKeyFrameNeeded()

        try {
            while (true) {
                val msg = WebSocket.read(input)
                when (msg.opcode) {
                    WebSocket.OP_TEXT -> {
                        val text = msg.text
                        when {
                            text.contains("\"keyframe\"") -> {
                                client.waitingForKey = true
                                onKeyFrameNeeded()
                            }
                            text.contains("\"ping\"") -> {
                                val ts = Regex(""""ts"\s*:\s*(\d+)""").find(text)?.groupValues?.get(1) ?: "0"
                                client.queue.offer(WebSocket.frame(WebSocket.OP_TEXT, """{"type":"pong","ts":$ts}""".toByteArray()))
                            }
                            else -> onClientMessage(text)
                        }
                    }
                    WebSocket.OP_PING -> client.queue.offer(WebSocket.frame(WebSocket.OP_PONG, msg.payload))
                    WebSocket.OP_CLOSE -> break
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client ngắt: $e")
        } finally {
            client.close(null)
            clients -= client
            onClientsChanged(clients.size)
        }
    }

    private fun serveFile(output: OutputStream, request: Request) {
        val name = request.path.trimStart('/').ifEmpty { "index.html" }
        // Chặn đường dẫn kiểu ../ ra ngoài thư mục web.
        val body = if (name.contains("..")) null else runCatching { assets.open("web/$name").use { it.readBytes() } }.getOrNull()
        val header = if (body == null) {
            "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        } else {
            "HTTP/1.1 200 OK\r\nContent-Type: ${contentType(name)}\r\nContent-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        }
        output.write(header.toByteArray())
        if (body != null && request.method != "HEAD") output.write(body)
        output.flush()
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.')) {
        "html" -> "text/html; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
    )

    /** Đọc dòng yêu cầu và header. Đọc từng byte để không ăn mất frame WebSocket theo sau. */
    private fun readRequest(input: InputStream): Request? {
        val lines = mutableListOf<String>()
        val line = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (++total > 16 * 1024) return null
            if (b == '\n'.code) {
                val s = line.toString(Charsets.UTF_8.name()).trimEnd('\r')
                line.reset()
                if (s.isEmpty()) break
                lines += s
            } else {
                line.write(b)
            }
        }
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 2) return null
        val target = parts[1]
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val headers = lines.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        return Request(parts[0], path, query, headers)
    }

    private inner class Client(
        private val socket: Socket,
        private val output: OutputStream,
        /** Các frame gửi đầu tiên (hello + nhóm khung đang giữ), trước hàng đợi. */
        private val initial: List<ByteArray>,
    ) {
        val queue = ArrayBlockingQueue<ByteArray>(CLIENT_QUEUE)
        @Volatile var waitingForKey = true
        @Volatile private var open = true

        fun startWriter() {
            thread(name = "ws-writer") {
                try {
                    for (frame in initial) output.write(frame)
                    output.flush()
                    while (open) {
                        val frame = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                        output.write(frame)
                        output.flush()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Ghi lỗi: $e")
                    close(null)
                }
            }
        }

        fun close(code: Int?) {
            if (!open) return
            open = false
            if (code != null) runCatching { output.writeFrame(WebSocket.closeFrame(code)) }
            runCatching { socket.close() }
        }
    }
}
