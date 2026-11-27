package com.phonehunger.app.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Effects
import java.io.File

data class AudioSegment(val clip: Clip, val startMs: Long = 0, val endMs: Long = clip.durationMs) {
    fun valid() = startMs >= 0 && endMs > startMs && endMs <= clip.durationMs
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class AudioEditor(private val context: Context, private val library: AudioLibrary) {
    private var transformer: Transformer? = null
    private var output: File? = null
    fun export(segments: List<AudioSegment>, speed: Float = 1f, pitch: Float = 1f, gain: Float = 1f, fadeInMs: Long = 0, fadeOutMs: Long = 0, done: (Result<Clip>) -> Unit) {
        render(segments, speed, pitch, gain, fadeInMs, fadeOutMs) { result ->
            result.onSuccess { file ->
                try { done(Result.success(library.addExport(file, (segments.sumOf { it.endMs - it.startMs } / speed).toLong()))) }
                catch (error: Exception) { file.delete(); done(Result.failure(error)) }
            }.onFailure { done(Result.failure(it)) }
        }
    }
    fun preview(segments: List<AudioSegment>, speed: Float = 1f, pitch: Float = 1f, gain: Float = 1f, fadeInMs: Long = 0, fadeOutMs: Long = 0, done: (Result<File>) -> Unit) {
        render(segments, speed, pitch, gain, fadeInMs, fadeOutMs, done)
    }
    private fun render(segments: List<AudioSegment>, speed: Float, pitch: Float, gain: Float, fadeInMs: Long, fadeOutMs: Long, done: (Result<File>) -> Unit) {
        if (segments.isEmpty() || segments.any { !it.valid() } || segments.sumOf { it.endMs - it.startMs } > 60_000) {
            done(Result.failure(IllegalArgumentException("Project must be 1–60 seconds with valid trims")))
            return
        }
        val items = segments.mapIndexed { index, segment ->
            val media = MediaItem.Builder().setUri(library.file(segment.clip).toURI().toString())
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(segment.startMs).setEndPositionMs(segment.endMs).build()).build()
            val sonic = SonicAudioProcessor().apply { setSpeed(speed.coerceIn(.5f, 2f)); setPitch(pitch.coerceIn(.5f, 2f)) }
            val duration = segment.endMs - segment.startMs
            val fadeIn = if (index == 0) fadeInMs.coerceIn(0, duration) else 0L
            val fadeOut = if (index == segments.lastIndex) fadeOutMs.coerceIn(0, duration) else 0L
            val provider = object : GainProcessor.GainProvider {
                override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float {
                    val ms = samplePosition * 1000L / sampleRate
                    val inFactor = if (fadeIn > 0) (ms.toFloat() / fadeIn).coerceIn(0f, 1f) else 1f
                    val outFactor = if (fadeOut > 0) ((duration - ms).toFloat() / fadeOut).coerceIn(0f, 1f) else 1f
                    return gain.coerceIn(0f, 1f) * inFactor * outFactor
                }
                override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
                    if (getGainFactorAtSamplePosition(samplePosition, sampleRate) == 1f) samplePosition + 1 else C.TIME_UNSET
            }
            EditedMediaItem.Builder(media).setEffects(Effects(listOf(GainProcessor(provider), sonic), emptyList())).build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.withAudioFrom(items)).build()
        val target = library.recordingFile()
        output = target
        transformer = Transformer.Builder(context).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                output = null; transformer = null; done(Result.success(target))
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                target.delete(); output = null; transformer = null; done(Result.failure(exportException))
            }
        }).build()
        try { transformer!!.start(composition, target.path) }
        catch (error: Exception) { target.delete(); output = null; transformer = null; done(Result.failure(error)) }
    }
    fun cancel() { transformer?.cancel(); transformer = null; output?.delete(); output = null }
}
