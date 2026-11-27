package com.phonehunger.app.monitor

import android.app.*
import android.content.*
import android.media.AudioManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.phonehunger.app.MainActivity
import com.phonehunger.app.BuildConfig
import com.phonehunger.app.R
import com.phonehunger.app.audio.Speech
import com.phonehunger.app.audio.AudioLibrary
import com.phonehunger.app.audio.Assignments
import com.phonehunger.app.audio.ClipPlayer
import com.phonehunger.app.audio.SfxPlayer
import com.phonehunger.app.battery.*
import com.phonehunger.app.core.*
import com.phonehunger.app.widget.BatteryWidget
import com.phonehunger.app.skin.BatteryOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar

class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var speech: Speech
    private val player by lazy { ClipPlayer(this) }
    private val sfx by lazy { SfxPlayer(this) }
    private val overlay by lazy { BatteryOverlay(this) }
    private val engine = BatteryEngine()
    private var settings = UserSettings()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val snapshot = BatteryState.fromIntent(intent) ?: return
            handle(snapshot)
        }
    }
    private fun handle(snapshot: BatterySnapshot) {
        BatteryState.snapshot.value = snapshot
        BatteryWidget.update(this, snapshot)
        if (settings.overlay) overlay.show(snapshot, settings.skinColor, settings.skinSize, settings.overlayLeft)
        play(engine.accept(snapshot, settings.thresholds, settings.cooldownSeconds * 1000L))
        scope.launch {
            delay(1_600)
            if (BatteryState.snapshot.value == snapshot) play(engine.accept(snapshot, settings.thresholds, settings.cooldownSeconds * 1000L))
        }
        updateNotification(snapshot)
    }
    private fun play(event: BatteryEvent?) {
        if (event == null || isQuiet(settings, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) return
        val audio = getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.mode != AudioManager.MODE_NORMAL) return
        if (getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        val pro = getSharedPreferences("play_entitlement_v1", MODE_PRIVATE).getBoolean("owned", false)
        val activeSettings = if (pro) settings else settings.copy(personality = 0, voiceName = null)
        val library = AudioLibrary(this)
        val assigned = if (pro) Assignments(this).pick(event)?.let { id -> library.list().firstOrNull { it.id == id } } else null
        if (assigned != null) try { player.play(library.file(assigned), settings.volume) } catch (_: Exception) { speech.say(event, activeSettings) }
        else sfx.play(event, settings.volume) { speech.say(event, activeSettings) }
    }
    override fun onCreate() {
        super.onCreate()
        MonitorRuntime.running.value = true
        speech = Speech(this)
        createChannel()
        BatteryState.initial(this)?.let { BatteryState.snapshot.value = it; engine.accept(it) }
        BatteryState.register(this, receiver)
        scope.launch { Settings(this@MonitorService).flow.collect { settings = it; if (!it.enabled) stopSelf() else if (it.overlay) overlay.show(BatteryState.snapshot.value, it.skinColor, it.skinSize, it.overlayLeft) else overlay.hide() } }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { Settings(this@MonitorService).enabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(42, notification(BatteryState.snapshot.value))
        if (BuildConfig.DEBUG && intent?.action == ACTION_DEBUG_BATTERY) {
            val level = intent.getIntExtra("level", -1)
            if (level in 0..100) handle(BatterySnapshot(level, intent.getBooleanExtra("plugged", false), level == 100 && intent.getBooleanExtra("plugged", false), System.currentTimeMillis()))
        }
        return START_STICKY
    }
    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("monitor", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(snapshot: BatterySnapshot?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "monitor").setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(getString(R.string.monitor_running)).setContentText(snapshot?.let { "${it.level}%" } ?: "—")
            .setContentIntent(open).addAction(android.R.drawable.ic_media_pause, getString(R.string.stop), stop)
            .setOngoing(true).build()
    }
    private fun updateNotification(snapshot: BatterySnapshot) {
        getSystemService(NotificationManager::class.java).notify(42, notification(snapshot))
    }
    override fun onDestroy() {
        MonitorRuntime.running.value = false
        unregisterReceiver(receiver)
        speech.close()
        player.stop()
        sfx.stop()
        overlay.hide()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_STOP = "com.phonehunger.app.STOP"
        const val ACTION_DEBUG_BATTERY = "com.phonehunger.app.DEBUG_BATTERY"
        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
    }
}

object MonitorRuntime { val running = MutableStateFlow(false) }

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = Settings(context).flow.first()
                if (settings.enabled && settings.reboot) try { MonitorService.start(context) } catch (_: Exception) { }
            } finally { pending.finish() }
        }
    }
}
