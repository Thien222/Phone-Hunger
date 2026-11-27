package com.phonehunger.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Decode a bounded clip and downsample actual PCM amplitudes into display bars. */
object Waveform {
    fun read(file: File, durationMs: Long, count: Int = 64): List<Float> {
        require(durationMs in 1..120_000 && count in 1..256)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("No audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: throw IllegalArgumentException("Unknown audio format"))
            codec.configure(format, null, null, 0)
            codec.start()
            val sums = DoubleArray(count)
            val samples = IntArray(count)
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var attempts = 0
            while (!outputEnded && attempts++ < 20_000) {
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("Decoder input unavailable")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                        else { codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val outputFormat = codec.outputFormat
                        val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        val bar = ((info.presentationTimeUs / 1000 * count) / durationMs).toInt().coerceIn(0, count - 1)
                        if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                            val floats = buffer.asFloatBuffer()
                            val step = max(1, floats.remaining() / 128)
                            var i = 0
                            while (i < floats.limit()) { sums[bar] += abs(floats.get(i)).toDouble().coerceAtMost(1.0); samples[bar]++; i += step }
                        } else if (encoding == AudioFormat.ENCODING_PCM_16BIT) {
                            val shorts = buffer.asShortBuffer()
                            val step = max(1, shorts.remaining() / 128)
                            var i = 0
                            while (i < shorts.limit()) { sums[bar] += abs(shorts.get(i).toInt()) / 32768.0; samples[bar]++; i += step }
                        }
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
            if (!outputEnded) throw IllegalStateException("Audio decode timed out")
            val values = List(count) { index -> if (samples[index] == 0) 0f else (sums[index] / samples[index]).toFloat().coerceIn(0f, 1f) }
            val peak = values.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
            return values.map { it / peak }
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            codec?.release()
            extractor.release()
        }
    }
}
