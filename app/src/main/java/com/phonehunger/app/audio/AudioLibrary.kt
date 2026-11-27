package com.phonehunger.app.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Clip(val id: String, val name: String, val file: String, val durationMs: Long)

class AudioLibrary(private val context: Context) {
    private val folder = File(context.filesDir, "clips").apply { mkdirs() }
    private val index = File(folder, "index.json")
    fun list(): List<Clip> = try {
        val data = JSONArray(index.readText())
        (0 until data.length()).map { data.getJSONObject(it) }.map { Clip(it.getString("id"), it.getString("name"), it.getString("file"), it.getLong("duration")) }
    } catch (_: Exception) { emptyList() }
    private fun write(clips: List<Clip>) {
        val json = JSONArray()
        clips.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.file).put("duration", it.durationMs)) }
        val temp = File(folder, "index.tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(index)) { index.writeText(temp.readText()); temp.delete() }
    }
    fun import(uri: Uri): Clip {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) throw IllegalArgumentException("Invalid file")
            val size = c.getLong(1)
            if (size > 20_000_000L) throw IllegalArgumentException("File exceeds 20 MB")
            c.getString(0) ?: "Audio"
        } ?: "Audio"
        val mime = context.contentResolver.getType(uri) ?: ""
        if (!mime.startsWith("audio/")) throw IllegalArgumentException("Select an audio file")
        val file = File(folder, "${UUID.randomUUID()}.audio")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var count: Int; var total = 0L
                while (input.read(buffer).also { count = it } != -1) {
                    total += count; if (total > 20_000_000L) throw IllegalArgumentException("File exceeds 20 MB")
                    output.write(buffer, 0, count)
                }
            } } ?: throw IllegalArgumentException("Cannot open file")
            val retriever = MediaMetadataRetriever()
            val duration = try { retriever.setDataSource(file.path); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 } finally { retriever.release() }
            if (duration !in 1..120_000) throw IllegalArgumentException("Audio must be under 120 seconds")
            val clip = Clip(file.name, name, file.name, duration)
            write(list() + clip)
            return clip
        } catch (error: Exception) { file.delete(); throw error }
    }
    fun addRecording(file: File, durationMs: Long): Clip {
        val clip = Clip(file.name, "Recording", file.name, durationMs)
        write(list() + clip)
        return clip
    }
    fun addExport(file: File, durationMs: Long): Clip {
        val clip = Clip(file.name, "Edited audio", file.name, durationMs)
        write(list() + clip)
        return clip
    }
    fun file(clip: Clip): File = File(folder, clip.file)
    fun delete(clip: Clip) { write(list().filterNot { it.id == clip.id }); file(clip).delete() }
    fun clear() { folder.listFiles()?.filter { it != index }?.forEach { it.delete() }; write(emptyList()) }
    fun recordingFile(): File = File(folder, "${UUID.randomUUID()}.m4a")
    fun speechFile(): File = File(folder, "${UUID.randomUUID()}.wav")
}

class Recorder(context: Context, private val file: File) {
    private val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
    private var started = 0L
    fun start() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(96000)
        recorder.setAudioSamplingRate(44100)
        recorder.setOutputFile(file.path)
        recorder.prepare(); recorder.start(); started = System.currentTimeMillis()
    }
    fun stop(): Long { val duration = System.currentTimeMillis() - started; recorder.stop(); recorder.release(); return duration }
    fun cancel() { try { recorder.stop() } catch (_: Exception) {}; recorder.release(); file.delete() }
}

class ClipPlayer(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    fun play(file: File, volume: Float = 1f, after: () -> Unit = {}) {
        stop()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { if (it <= 0) stop() }.build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        focus = request
        try {
            player = MediaPlayer().apply { setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()); setDataSource(file.path); setVolume(volume, volume); setOnCompletionListener { this@ClipPlayer.stop(); after() }; prepare(); start() }
        } catch (e: Exception) { stop(); throw e }
    }
    fun stop() { player?.release(); player = null; focus?.let(manager::abandonAudioFocusRequest); focus = null }
}
