package com.phonehunger.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phonehunger.app.audio.*
import com.phonehunger.app.core.UserSettings
import com.phonehunger.app.battery.BatteryEvent
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable fun StudioScreen(settings: UserSettings) {
    val context = LocalContext.current
    val library = remember { AudioLibrary(context) }
    val assignments = remember { Assignments(context) }
    val player = remember { ClipPlayer(context) }
    val editor = remember { AudioEditor(context, library) }
    val synthesizer = remember { OfflineSynthesizer(context, library) }
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf(library.list()) }
    var error by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<Recorder?>(null) }
    var recordFile by remember { mutableStateOf<java.io.File?>(null) }
    var selected by remember { mutableStateOf<Clip?>(null) }
    var randomAssignment by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Clip?>(null) }
    var segments by remember { mutableStateOf(listOf<AudioSegment>()) }
    var exporting by remember { mutableStateOf(false) }
    var previewFile by remember { mutableStateOf<java.io.File?>(null) }
    val waveforms = remember { mutableStateMapOf<String, List<Float>>() }
    var textInput by remember { mutableStateOf("") }
    var pitch by remember { mutableFloatStateOf(1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var editPitch by remember { mutableFloatStateOf(1f) }
    var editSpeed by remember { mutableFloatStateOf(1f) }
    var editGain by remember { mutableFloatStateOf(1f) }
    var fadeIn by remember { mutableFloatStateOf(0f) }
    var fadeOut by remember { mutableFloatStateOf(0f) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { try { withContext(Dispatchers.IO) { library.import(uri) }; clips = library.list() } catch (e: Exception) { error = e.message } }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) try {
            val file = library.recordingFile()
            recording = Recorder(context, file).also { it.start() }
            recordFile = file
        } catch (e: Exception) { error = e.message }
        else error = context.getString(R.string.permission_needed)
    }
    LaunchedEffect(recording) {
        val active = recording ?: return@LaunchedEffect
        delay(60_000)
        if (recording === active) {
            try { recordFile?.let { library.addRecording(it, active.stop()) }; clips = library.list() }
            catch (e: Exception) { recordFile?.delete(); error = e.message }
            recording = null; recordFile = null
        }
    }
    DisposableEffect(Unit) { onDispose { player.stop(); previewFile?.delete(); recording?.cancel(); editor.cancel(); synthesizer.close() } }
    Text(stringResource(R.string.studio), style = MaterialTheme.typography.headlineMedium)
    TextButton(onClick = { importFile.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.import_audio)) }
    Text(stringResource(R.string.text_to_speech))
    OutlinedTextField(value = textInput, onValueChange = { textInput = it.take(200) }, label = { Text(stringResource(R.string.text_to_speech)) }, modifier = Modifier.fillMaxWidth())
    Text("${stringResource(R.string.pitch)} ${"%.1f".format(pitch)}")
    Slider(pitch, { pitch = it }, valueRange = .5f..2f)
    Text("${stringResource(R.string.speed)} ${"%.1f".format(speed)}")
    Slider(speed, { speed = it }, valueRange = .5f..2f)
    Button(onClick = { synthesizer.synthesize(textInput, settings.voiceLanguage, settings.voiceName, pitch, speed) { result -> result.onSuccess { clips = library.list() }.onFailure { error = it.message } } }, enabled = textInput.isNotBlank()) { Text(stringResource(R.string.save)) }
    if (recording == null) Button(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) microphone.launch(Manifest.permission.RECORD_AUDIO)
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }) { Text(stringResource(R.string.record)) }
    else Button(onClick = {
        try { val duration = recording!!.stop(); recordFile?.let { library.addRecording(it, duration) }; clips = library.list() }
        catch (e: Exception) { recordFile?.delete(); error = e.message }
        recording = null; recordFile = null
    }) { Text(stringResource(R.string.stop)) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    clips.forEach { clip ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(clip.name)
                Text("${clip.durationMs / 1000}s")
                waveforms[clip.id]?.let { bars ->
                    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                        val width = size.width / bars.size
                        bars.forEachIndexed { index, amplitude ->
                            val x = (index + .5f) * width
                            val half = amplitude * size.height / 2
                            drawLine(Color(0xff72e2a0), Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), strokeWidth = (width * .6f).coerceAtLeast(1f))
                        }
                    }
                }
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            try { waveforms[clip.id] = withContext(Dispatchers.IO) { Waveform.read(library.file(clip), clip.durationMs) } }
                            catch (e: Exception) { error = e.message }
                        }
                    }) { Text(stringResource(R.string.waveform)) }
                    TextButton(onClick = { try { player.play(library.file(clip)) } catch (e: Exception) { error = e.message } }) { Text(stringResource(R.string.preview)) }
                    TextButton(onClick = { segments = segments + AudioSegment(clip) }) { Text(stringResource(R.string.add_to_project)) }
                    TextButton(onClick = { selected = clip }) { Text(stringResource(R.string.assign_event)) }
                    TextButton(onClick = { deleting = clip }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
    if (segments.isNotEmpty()) {
        Text(stringResource(R.string.project_duration, segments.sumOf { it.endMs - it.startMs } / 1000), style = MaterialTheme.typography.titleMedium)
        Text("${stringResource(R.string.pitch)} ${"%.1f".format(editPitch)}")
        Slider(editPitch, { editPitch = it }, valueRange = .5f..2f)
        Text("${stringResource(R.string.speed)} ${"%.1f".format(editSpeed)}")
        Slider(editSpeed, { editSpeed = it }, valueRange = .5f..2f)
        Text("${stringResource(R.string.gain)} ${(editGain * 100).toInt()}%")
        Slider(editGain, { editGain = it })
        Text(stringResource(R.string.fade_in, fadeIn.toInt()))
        Slider(fadeIn, { fadeIn = it }, valueRange = 0f..3000f)
        Text(stringResource(R.string.fade_out, fadeOut.toInt()))
        Slider(fadeOut, { fadeOut = it }, valueRange = 0f..3000f)
        segments.forEachIndexed { index, segment ->
            Text(segment.clip.name)
            Text(stringResource(R.string.trim_start, segment.startMs / 1000))
            Slider(segment.startMs.toFloat(), { value -> segments = segments.toMutableList().also { list -> list[index] = segment.copy(startMs = value.toLong().coerceAtMost(segment.endMs - 1)) } }, valueRange = 0f..(segment.clip.durationMs - 1).coerceAtLeast(1).toFloat())
            Text(stringResource(R.string.trim_end, segment.endMs / 1000))
            Slider(segment.endMs.toFloat(), { value -> segments = segments.toMutableList().also { list -> list[index] = segment.copy(endMs = value.toLong().coerceAtLeast(segment.startMs + 1)) } }, valueRange = 1f..segment.clip.durationMs.coerceAtLeast(1).toFloat())
            Row {
                TextButton(onClick = { if (index > 0) segments = segments.toMutableList().also { it[index] = it[index - 1].also { prior -> it[index - 1] = it[index] } } }) { Text("↑") }
                TextButton(onClick = { if (index < segments.lastIndex) segments = segments.toMutableList().also { it[index] = it[index + 1].also { next -> it[index + 1] = it[index] } } }) { Text("↓") }
                TextButton(onClick = { segments = segments.toMutableList().also { it.removeAt(index) } }) { Text("×") }
            }
        }
        Button(enabled = !exporting, onClick = {
            previewFile?.delete(); previewFile = null; player.stop(); exporting = true
            editor.preview(segments, editSpeed, editPitch, editGain, fadeIn.toLong(), fadeOut.toLong()) { result ->
                exporting = false
                result.onSuccess { file ->
                    previewFile = file
                    try { player.play(file, after = { file.delete(); previewFile = null }) }
                    catch (e: Exception) { file.delete(); previewFile = null; error = e.message }
                }.onFailure { error = it.message }
            }
        }) { Text(stringResource(R.string.preview)) }
        Button(enabled = !exporting, onClick = {
            exporting = true
            editor.export(segments, editSpeed, editPitch, editGain, fadeIn.toLong(), fadeOut.toLong()) { result ->
                exporting = false
                result.onSuccess { clips = library.list(); selected = it; segments = emptyList() }
                    .onFailure { error = it.message }
            }
        }) { Text(stringResource(if (exporting) R.string.exporting else R.string.export_m4a)) }
        if (exporting) TextButton(onClick = { editor.cancel(); exporting = false }) { Text(stringResource(R.string.stop)) }
    }
    selected?.let { clip ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(clip.name) }, text = {
            Column {
                Row { Checkbox(randomAssignment, { randomAssignment = it }); Text(stringResource(R.string.add_random)) }
                BatteryEvent.entries.forEach { event -> TextButton(onClick = {
                    if (clip.durationMs <= 15_000) {
                        if (randomAssignment) assignments.addRandom(event, clip.id) else assignments.set(event, clip.id)
                    } else error = context.getString(R.string.clip_too_long)
                    selected = null
                }) { Text(event.name.replace('_', ' ')) } }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) } })
    }
    deleting?.let { clip ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.delete_clip_question)) },
            confirmButton = { TextButton(onClick = { assignments.clearClip(clip.id); library.delete(clip); clips = library.list(); segments = segments.filterNot { it.clip.id == clip.id }; deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
