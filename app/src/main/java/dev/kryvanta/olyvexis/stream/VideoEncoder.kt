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
 * H.264 encoder using MediaCodec. The virtual display renders directly into [inputSurface].
 *
 * Output packets are Annex B (00 00 00 01 start codes). SPS/PPS is emitted once in
 * [EncodedPacket.isConfig]; StreamServer prepends it before each keyframe.
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
            // Some encoders reject KEY_PROFILE/KEY_LOW_LATENCY: fall back to minimal config.
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
            // Long periodic keyframe interval; on-demand keyframes use requestKeyFrame().
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            // Repeat last frame after 100 ms when idle so the browser decoder does not stall.
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000)
            if (withProfile) {
                // Baseline profile: no B-frames, low latency, universally supported by WebCodecs.
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
                setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
        }

    /** Runs the encoding loop on a dedicated thread; [onPacket] is invoked on that thread. */
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
                    break // codec stopped
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

    /** Request an immediate keyframe (for new clients or packet loss recovery). */
    fun requestKeyFrame() {
        runCatching {
            codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        }
    }

    /** Dynamically adjust bitrate when clients experience congestion or recovery. */
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
         * Highest frame rate (60 or 30) supported by an H.264 encoder at this resolution, or null
         * if unsupported. Budget devices often cap at 1280x720@30.
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
            // Encoder exists but does not advertise capabilities (common on custom ROMs): try 30 fps.
            return if (encoders.isNotEmpty()) 30 else null
        }
    }
}

class EncodedPacket(val data: ByteArray, val ptsUs: Long, val isKeyFrame: Boolean, val isConfig: Boolean)
