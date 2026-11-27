package dev.kryvanta.olyvexis.stream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import kotlin.concurrent.thread

/**
 * Mã hoá H.264 bằng MediaCodec. Màn hình ảo vẽ thẳng vào [inputSurface].
 *
 * Gói ra ở dạng Annex B (có mã bắt đầu 00 00 00 01). SPS/PPS ra một lần trong gói
 * [EncodedPacket.isConfig]; StreamServer tự ghép nó vào trước mỗi keyframe.
 */
class VideoEncoder(
    val width: Int,
    val height: Int,
    private val bitRate: Int = 8_000_000,
    private val frameRate: Int = 60,
) {
    private var codec: MediaCodec? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    lateinit var inputSurface: Surface
        private set

    fun prepare(): Surface {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            codec.configure(format(withProfile = true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            // Một số encoder không nhận KEY_PROFILE/KEY_LOW_LATENCY: thử lại với cấu hình tối thiểu.
            Log.w(TAG, "Cấu hình đầy đủ bị từ chối, thử cấu hình tối thiểu", e)
            codec.reset()
            codec.configure(format(withProfile = false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        inputSurface = codec.createInputSurface()
        this.codec = codec
        return inputSurface
    }

    private fun format(withProfile: Boolean) =
        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            // Keyframe định kỳ dài; khi cần (client mới, mất gói) thì xin bằng requestKeyFrame().
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            // Màn hình đứng yên thì vẫn gửi lại khung hình sau 100 ms để trình duyệt không bị treo.
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
            if (withProfile) {
                // Baseline: không có B-frame, độ trễ thấp, WebCodecs nào cũng giải mã được.
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }

    /** Chạy vòng lặp mã hoá trên một luồng riêng; [onPacket] được gọi trên luồng đó. */
    fun start(onPacket: (EncodedPacket) -> Unit) {
        val codec = checkNotNull(codec) { "Chưa gọi prepare()" }
        codec.start()
        running = true
        worker = thread(name = "h264-encoder") {
            val info = MediaCodec.BufferInfo()
            while (running) {
                val index = try {
                    codec.dequeueOutputBuffer(info, 100_000)
                } catch (e: IllegalStateException) {
                    break // codec đã bị dừng
                }
                if (index < 0) continue
                val buffer = codec.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(data)
                    onPacket(
                        EncodedPacket(
                            data = data,
                            ptsUs = info.presentationTimeUs,
                            isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0,
                            isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0,
                        ),
                    )
                }
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    }

    /** Xin encoder ra keyframe ngay (khi có client mới hoặc client bị mất gói). */
    fun requestKeyFrame() {
        runCatching {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    /** Đổi bitrate động theo băng thông khi client bị nghẽn hoặc mạng phục hồi. */
    fun adjustBitrate(newBitrate: Int) {
        runCatching {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate) })
        }
    }

    fun stop() {
        running = false
        worker?.join(1_000)
        worker = null
        codec?.run {
            runCatching { stop() }
            release()
        }
        codec = null
        if (::inputSurface.isInitialized) inputSurface.release()
    }

    companion object {
        private const val TAG = "Olyvexis/Encoder"

        /**
         * Số khung/giây cao nhất (60 hoặc 30) mà một encoder H.264 phần cứng hoặc phần mềm trên
         * máy nhận được ở kích thước này; null nếu không encoder nào nhận. Máy giá rẻ thường
         * chỉ chạy 1280x720@30.
         */
        fun supportedFrameRate(width: Int, height: Int): Int? {
            val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
            }
            for (fps in listOf(60, 30)) {
                val ok = encoders.any { info ->
                    runCatching {
                        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                            .areSizeAndRateSupported(width, height, fps.toDouble())
                    }.getOrDefault(false)
                }
                if (ok) return fps
            }
            // Có encoder nhưng không khai báo khả năng (hay gặp trên ROM lạ): cứ thử 30.
            return if (encoders.isNotEmpty()) 30 else null
        }
    }
}

class EncodedPacket(val data: ByteArray, val ptsUs: Long, val isKeyFrame: Boolean, val isConfig: Boolean)
