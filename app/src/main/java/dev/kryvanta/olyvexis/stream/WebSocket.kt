package dev.kryvanta.olyvexis.stream

import android.util.Base64
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Minimal WebSocket (RFC 6455) server helper: handshake, frame write, and browser frame read. */
object WebSocket {
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    /** Olyvexis private close code (4000-4999 reserved for applications). */
    const val CLOSE_WRONG_PIN = 4001
    const val CLOSE_SERVER_STOPPED = 4002

    fun acceptKey(clientKey: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1").digest((clientKey.trim() + GUID).toByteArray())
        return Base64.encodeToString(sha1, Base64.NO_WRAP)
    }

    fun handshakeResponse(clientKey: String) =
        "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${acceptKey(clientKey)}\r\n\r\n"

    /** Build a complete WebSocket frame (server-to-client frames are unmasked). */
    fun frame(opcode: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val headerSize = when {
            len < 126 -> 2
            len <= 0xFFFF -> 4
            else -> 10
        }
        val out = ByteArray(headerSize + len)
        out[0] = (0x80 or opcode).toByte()
        when (headerSize) {
            2 -> out[1] = len.toByte()
            4 -> {
                out[1] = 126
                out[2] = (len shr 8).toByte()
                out[3] = len.toByte()
            }
            else -> {
                out[1] = 127
                for (i in 0 until 8) out[2 + i] = (len.toLong() shr (56 - 8 * i)).toByte()
            }
        }
        System.arraycopy(payload, 0, out, headerSize, len)
        return out
    }

    fun closeFrame(code: Int, reason: String = ""): ByteArray {
        val r = reason.toByteArray()
        val payload = ByteArray(2 + r.size)
        payload[0] = (code shr 8).toByte()
        payload[1] = code.toByte()
        System.arraycopy(r, 0, payload, 2, r.size)
        return frame(OP_CLOSE, payload)
    }

    class Message(val opcode: Int, val payload: ByteArray) {
        val text get() = String(payload)
    }

    /** Read a masked frame from the browser. Fragmented frames are not supported. */
    fun read(input: InputStream, maxSize: Int = 1 shl 20): Message {
        val b0 = input.readByteOrThrow()
        val b1 = input.readByteOrThrow()
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            len = ((input.readByteOrThrow() shl 8) or input.readByteOrThrow()).toLong()
        } else if (len == 127L) {
            len = 0
            repeat(8) { len = (len shl 8) or input.readByteOrThrow().toLong() }
        }
        require(len <= maxSize) { "Frame quá lớn: $len" }
        val mask = if (masked) ByteArray(4).also { input.readFully(it) } else null
        val payload = ByteArray(len.toInt())
        input.readFully(payload)
        if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
        return Message(opcode, payload)
    }

    private fun InputStream.readByteOrThrow(): Int {
        val b = read()
        if (b < 0) throw EOFException()
        return b
    }

    private fun InputStream.readFully(buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = read(buf, off, buf.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    fun OutputStream.writeFrame(bytes: ByteArray) {
        write(bytes)
        flush()
    }
}
