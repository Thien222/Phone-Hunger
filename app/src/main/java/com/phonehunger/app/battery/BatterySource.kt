package com.phonehunger.app.battery

import android.content.*
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow

object BatteryState {
    val snapshot = MutableStateFlow<BatterySnapshot?>(null)
    fun fromIntent(intent: Intent): BatterySnapshot? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return BatterySnapshot((level * 100 / scale).coerceIn(0, 100), plugged,
            status == BatteryManager.BATTERY_STATUS_FULL || level >= scale && plugged, System.currentTimeMillis())
    }
    fun initial(context: Context): BatterySnapshot? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::fromIntent)
    fun register(context: Context, receiver: BroadcastReceiver) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }
}
