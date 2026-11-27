package com.phonehunger.app.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class OfflineSynthesizer(context: Context, private val library: AudioLibrary) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    override fun onInit(status: Int) { ready = status == TextToSpeech.SUCCESS }
    fun synthesize(text: String, language: String, voiceName: String?, pitch: Float, speed: Float, done: (Result<Clip>) -> Unit) {
        if (!ready || text.isBlank() || text.length > 200) { done(Result.failure(IllegalStateException("Offline TTS unavailable or text invalid"))); return }
        val locale = Locale.forLanguageTag(language)
        val voices = engine.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
        val voice = voices?.firstOrNull { it.name == voiceName } ?: voices?.firstOrNull()
        if (voice == null) { done(Result.failure(IllegalStateException("Install an offline voice for $language"))); return }
        val file = library.speechFile()
        val id = UUID.randomUUID().toString()
        engine.voice = voice
        engine.setPitch(pitch.coerceIn(.5f, 2f))
        engine.setSpeechRate(speed.coerceIn(.5f, 2f))
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                try {
                    val retriever = MediaMetadataRetriever()
                    val duration = try { retriever.setDataSource(file.path); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { retriever.release() }
                    if (duration !in 1..15_000) throw IllegalArgumentException("Speech must be under 15 seconds")
                    val clip = library.addExport(file, duration)
                    main.post { done(Result.success(clip)) }
                } catch (e: Exception) { file.delete(); main.post { done(Result.failure(e)) } }
            }
            override fun onError(utteranceId: String?) { file.delete(); main.post { done(Result.failure(IllegalStateException("Synthesis failed"))) } }
        })
        val code = engine.synthesizeToFile(text, Bundle(), file, id)
        if (code != TextToSpeech.SUCCESS) { file.delete(); done(Result.failure(IllegalStateException("Synthesis could not start"))) }
    }
    fun close() = engine.shutdown()
}
