package com.phonehunger.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import com.phonehunger.app.battery.BatteryEvent

class SfxPlayer(private val context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    fun play(event: BatteryEvent, volume: Float, after: () -> Unit = {}) {
        stop()
        val name = when (event) {
            BatteryEvent.POWER_CONNECTED -> "chew.wav"
            BatteryEvent.FULL -> "burp.wav"
            else -> "rumble.wav"
        }
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setOnAudioFocusChangeListener { if (it <= 0) stop() }.build()
            if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
            focus = request
            val descriptor = context.assets.openFd("audio/$name")
            player = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                setVolume(volume, volume)
                setOnCompletionListener { this@SfxPlayer.stop(); after() }
                prepare(); start()
            }
            descriptor.close()
        } catch (_: Exception) { stop(); after() }
    }
    fun stop() { player?.release(); player = null; focus?.let(manager::abandonAudioFocusRequest); focus = null }
}
