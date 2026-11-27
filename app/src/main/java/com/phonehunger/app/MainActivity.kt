package com.phonehunger.app

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phonehunger.app.audio.Speech
import com.phonehunger.app.audio.SfxPlayer
import com.phonehunger.app.audio.OfflineVoice
import com.phonehunger.app.audio.AudioLibrary
import com.phonehunger.app.audio.Assignments
import com.phonehunger.app.battery.*
import com.phonehunger.app.core.*
import com.phonehunger.app.monitor.MonitorService
import com.phonehunger.app.monitor.MonitorRuntime
import com.phonehunger.app.billing.BillingRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: Settings
    private lateinit var speech: Speech
    private lateinit var sfx: SfxPlayer
    private lateinit var billing: BillingRepository
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SystemSettings.canDrawOverlays(this)) lifecycleScope.launch { settingsStore.overlay(true) }
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { BatteryState.fromIntent(intent)?.let { BatteryState.snapshot.value = it } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = Settings(this)
        speech = Speech(this)
        sfx = SfxPlayer(this)
        billing = BillingRepository(this)
        BatteryState.snapshot.value = BatteryState.initial(this)
        BatteryState.register(this, batteryReceiver)
        setContent {
            val settings by settingsStore.flow.collectAsState(initial = UserSettings())
            val snapshot by BatteryState.snapshot.collectAsState()
            val pro by billing.state.collectAsState()
            val offlineVoices by speech.available.collectAsState()
            val monitorRunning by MonitorRuntime.running.collectAsState()
            LaunchedEffect(pro.owned) {
                if (!pro.owned) {
                    settingsStore.personality(0)
                    settingsStore.color(0xff72e2a0.toInt())
                    settingsStore.voiceName(null)
                    settingsStore.skinSize(220)
                    settingsStore.overlayLeft(false)
                }
            }
            var page by remember { mutableStateOf(0) }
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xff72e2a0), background = Color(0xff121915))
                else lightColorScheme(primary = Color(0xff116d42), background = Color(0xfff5faf6))
            MaterialTheme(colorScheme = colors) {
                Scaffold(topBar = { if (page != 0) TextButton(onClick = { page = 0 }) { Text("‹ ${stringResource(R.string.back)}") } }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        when (page) {
                            0 -> Home(snapshot, settings, pro.owned, monitorRunning, onEnable = { value ->
                                if (value && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                lifecycleScope.launch {
                                    settingsStore.enabled(value)
                                    if (value) try { MonitorService.start(this@MainActivity) }
                                    catch (_: Exception) { settingsStore.enabled(false) }
                                    else stopService(Intent(this@MainActivity, MonitorService::class.java))
                                }
                            }, onTry = { sfx.play(BatteryEvent.POWER_CONNECTED, settings.volume) { speech.say(BatteryEvent.POWER_CONNECTED, settings, true) } }, onPage = { page = it })
                            1 -> Voices(settings, pro.owned, offlineVoices,
                                { lifecycleScope.launch { settingsStore.language(it); settingsStore.voiceName(null) } },
                                { lifecycleScope.launch { settingsStore.personality(it) } },
                                { lifecycleScope.launch { settingsStore.voiceName(it) } },
                                { speech.say(it, settings, true) })
                            2 -> if (pro.owned) StudioScreen(settings) else ProScreen(pro, { billing.buy(this@MainActivity) }, { billing.refresh() })
                            3 -> Skins(settings, pro.owned, { lifecycleScope.launch { settingsStore.color(it) } },
                                { lifecycleScope.launch { settingsStore.skinSize(it) } },
                                { lifecycleScope.launch { settingsStore.overlayLeft(it) } }, { enabled ->
                                if (!enabled) lifecycleScope.launch { settingsStore.overlay(false) }
                                else if (SystemSettings.canDrawOverlays(this@MainActivity)) lifecycleScope.launch { settingsStore.overlay(true) }
                                else overlayPermission.launch(Intent(SystemSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                            })
                            4 -> SettingsScreen(settings, settingsStore, lifecycleScope, { page = it }, { billing.refresh() })
                            5 -> ProScreen(pro, { billing.buy(this@MainActivity) }, { billing.refresh() })
                            6 -> InfoScreen(R.string.privacy, R.string.privacy_body)
                            7 -> InfoScreen(R.string.terms, R.string.terms_body)
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (::billing.isInitialized) billing.refresh()
        if (::settingsStore.isInitialized) lifecycleScope.launch { if (settingsStore.flow.first().enabled && !MonitorRuntime.running.value) try { MonitorService.start(this@MainActivity) } catch (_: Exception) {} }
    }
    override fun onDestroy() { unregisterReceiver(batteryReceiver); sfx.stop(); speech.close(); billing.close(); super.onDestroy() }
}

@Composable private fun Home(snapshot: BatterySnapshot?, settings: UserSettings, pro: Boolean, running: Boolean, onEnable: (Boolean) -> Unit, onTry: () -> Unit, onPage: (Int) -> Unit) {
    Text("PhoneHunger", style = MaterialTheme.typography.headlineLarge)
    val level = snapshot?.level ?: 0
    val mood = if (snapshot?.plugged == true) "⚡" else if (level <= 5) "◕︵◕" else if (level <= 20) "•︵•" else "•ᴗ•"
    val pulse = rememberInfiniteTransition(label = "charging pulse")
    val scale by pulse.animateFloat(1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "charging scale")
    Box(Modifier.fillMaxWidth().height(settings.skinSize.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(settings.skinSize.dp).graphicsLayer { scaleX = if (snapshot?.plugged == true) scale else 1f; scaleY = if (snapshot?.plugged == true) scale else 1f }.background(Color(settings.skinColor), CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(mood, color = Color.Black, style = MaterialTheme.typography.displayLarge); Text("$level%", Modifier.testTag("battery_level"), color = Color.Black, style = MaterialTheme.typography.headlineMedium) }
        }
    }
    Text(stringResource(if (snapshot?.plugged == true) R.string.charging else if (level <= 20) R.string.hungry else if (level == 100) R.string.full else R.string.healthy))
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.enable_voice), Modifier.weight(1f)); Switch(settings.enabled, onEnable) }
    Text(stringResource(if (running) R.string.monitor_running else R.string.monitor_stopped), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = onTry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_sound)) }
    listOf(R.string.voices, R.string.studio, R.string.skins, R.string.settings).forEachIndexed { index, id ->
        OutlinedButton(onClick = { onPage(index + 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(id)) }
    }
    if (!pro) OutlinedButton(onClick = { onPage(5) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro)) }
    DebugPanel(settings.enabled)
}

@Composable private fun Voices(settings: UserSettings, pro: Boolean, offlineVoices: List<OfflineVoice>, onLanguage: (String) -> Unit, onPersonality: (Int) -> Unit, onVoice: (String?) -> Unit, onPreview: (BatteryEvent) -> Unit) {
    Text(stringResource(R.string.voices), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.language))
    Row { FilterChip(settings.voiceLanguage == "en-US", { onLanguage("en-US") }, label = { Text(stringResource(R.string.english)) }); Spacer(Modifier.width(8.dp)); FilterChip(settings.voiceLanguage == "vi-VN", { onLanguage("vi-VN") }, label = { Text(stringResource(R.string.vietnamese)) }) }
    Text(stringResource(R.string.personality))
    listOf(R.string.personality_cute, R.string.personality_grumpy, R.string.personality_robot, R.string.personality_lazy).forEachIndexed { index, label ->
        FilterChip(settings.personality == index, { onPersonality(index) }, enabled = pro || index == 0, label = { Text(stringResource(label)) })
    }
    Text(stringResource(R.string.offline_voices))
    val language = settings.voiceLanguage.substringBefore('-')
    val matching = offlineVoices.filter { it.language == language }
    if (matching.isEmpty()) Text(stringResource(R.string.offline_voice_unavailable))
    else {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.default_voice), Modifier.weight(1f)); RadioButton(settings.voiceName == null, { onVoice(null) }) }
        matching.forEach { voice -> Row(verticalAlignment = Alignment.CenterVertically) { Text(voice.name, Modifier.weight(1f)); RadioButton(settings.voiceName == voice.name, { onVoice(voice.name) }, enabled = pro) } }
    }
    BatteryEvent.entries.forEach { event ->
        Row(verticalAlignment = Alignment.CenterVertically) { Text(event.name.replace('_', ' '), Modifier.weight(1f)); TextButton(onClick = { onPreview(event) }) { Text(stringResource(R.string.preview)) } }
    }
}

@Composable private fun Skins(settings: UserSettings, pro: Boolean, onColor: (Int) -> Unit, onSize: (Int) -> Unit, onLeft: (Boolean) -> Unit, onOverlay: (Boolean) -> Unit) {
    Text(stringResource(R.string.skins), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.overlay_note))
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.overlay), Modifier.weight(1f)); Switch(settings.overlay, onOverlay) }
    Text(stringResource(R.string.skin_size))
    Slider(settings.skinSize.toFloat(), { onSize(it.toInt()) }, enabled = pro, valueRange = 160f..280f)
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.overlay_left), Modifier.weight(1f)); Switch(settings.overlayLeft, onLeft, enabled = pro) }
    listOf(0xff72e2a0, 0xfffacf67, 0xffe889a6, 0xff8fbefa).forEachIndexed { index, color ->
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Color(color), CircleShape)); Spacer(Modifier.width(16.dp)); RadioButton(settings.skinColor == color.toInt(), { onColor(color.toInt()) }, enabled = pro || index == 0) }
    }
}

@Composable private fun SettingsScreen(settings: UserSettings, store: Settings, scope: kotlinx.coroutines.CoroutineScope, onPage: (Int) -> Unit, onRestore: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.volume)); Slider(settings.volume, { scope.launch { store.volume(it) } })
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.quiet_hours), Modifier.weight(1f)); Switch(settings.quietEnabled, { scope.launch { store.quiet(it) } }) }
    if (settings.quietEnabled) {
        Text("${stringResource(R.string.quiet_start)}: ${settings.quietStart}:00")
        Slider(settings.quietStart.toFloat(), { scope.launch { store.quietHours(it.toInt(), settings.quietEnd) } }, valueRange = 0f..23f, steps = 22)
        Text("${stringResource(R.string.quiet_end)}: ${settings.quietEnd}:00")
        Slider(settings.quietEnd.toFloat(), { scope.launch { store.quietHours(settings.quietStart, it.toInt()) } }, valueRange = 0f..23f, steps = 22)
    }
    Text(stringResource(R.string.thresholds))
    Text(stringResource(R.string.cooldown, settings.cooldownSeconds))
    Slider(settings.cooldownSeconds.toFloat(), { scope.launch { store.cooldown(it.toInt()) } }, valueRange = 0f..300f)
    listOf(50, 30, 20, 10, 5, 1).forEach { level -> Row(verticalAlignment = Alignment.CenterVertically) { Text("$level%", Modifier.weight(1f)); Switch(level in settings.thresholds, { scope.launch { store.threshold(level, it) } }) } }
    Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.reboot), Modifier.weight(1f)); Switch(settings.reboot, { scope.launch { store.reboot(it) } }) }
    TextButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
    TextButton(onClick = { onPage(6) }) { Text(stringResource(R.string.privacy)) }
    TextButton(onClick = { onPage(7) }) { Text(stringResource(R.string.terms)) }
    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_all_data)) }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_all_data)) },
        text = { Text(stringResource(R.string.delete_all_data_question)) },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                store.reset()
                withContext(Dispatchers.IO) { AudioLibrary(context).clear(); Assignments(context).clearAll() }
                confirmDelete = false
            }
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun InfoScreen(title: Int, body: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(body))
}
