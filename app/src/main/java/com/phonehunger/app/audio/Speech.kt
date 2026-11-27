package com.phonehunger.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.app.NotificationManager
import android.speech.tts.TextToSpeech
import com.phonehunger.app.battery.BatteryEvent
import com.phonehunger.app.core.UserSettings
import java.util.Locale
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow

data class OfflineVoice(val name: String, val language: String)

class Speech(context: Context) : TextToSpeech.OnInitListener {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    val available = MutableStateFlow<List<OfflineVoice>>(emptyList())
    private var focus: AudioFocusRequest? = null
    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) available.value = tts.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language in setOf("en", "vi") }
            ?.map { OfflineVoice(it.name, it.locale.language) }?.sortedBy { it.name } ?: emptyList()
    }

    fun say(event: BatteryEvent, settings: UserSettings, preview: Boolean = false): Boolean {
        if (!ready) return false
        if (!preview && (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)) return false
        val locale = if (settings.voiceLanguage == "vi-VN") Locale.forLanguageTag("vi-VN") else Locale.US
        val voices = tts.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
        val voice = voices?.firstOrNull { it.name == settings.voiceName } ?: voices?.firstOrNull()
        if (voice == null) return false
        tts.voice = voice
        val (pitch, speed) = when (settings.personality) {
            1 -> .85f to .95f
            2 -> 1.25f to .82f
            3 -> .75f to .75f
            else -> 1.15f to 1.05f
        }
        tts.setSpeechRate(speed)
        tts.setPitch(pitch)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { if (it <= 0) tts.stop() }
            .build()
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { audio.abandonAudioFocusRequest(request) }
            override fun onError(utteranceId: String?) { audio.abandonAudioFocusRequest(request) }
        })
        tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.volume) }
        tts.speak(line(event, locale.language), TextToSpeech.QUEUE_FLUSH, params, "phonehunger")
        return true
    }
    fun close() { tts.stop(); tts.shutdown(); focus?.let(audio::abandonAudioFocusRequest) }
}

fun line(event: BatteryEvent, language: String): String {
    val vi = listOf("Hơi đói rồi nha.", "Có gì ăn không?", "Cho tao ăn coi!", "Đói muốn xỉu rồi!", "Mày định bỏ đói tao à?", "Kiếp sau gặp lại...", "Aaa, ngon quá!", "Ơ, đang ăn mà!", "Ợ... no quá!")
    val en = listOf("I'm getting hungry.", "Got any food?", "Feed me, please!", "I'm starving!", "Are you starving me?", "See you in another life...", "Finally, some food!", "Hey! I'm still eating!", "Burp... I'm full!")
    return (if (language == "vi") vi else en)[event.ordinal]
}
