package com.phonehunger.app

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.phonehunger.app.monitor.MonitorService

object FakeBatterySource {
    fun send(context: Context, level: Int, plugged: Boolean) {
        context.startService(Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_DEBUG_BATTERY)
            .putExtra("level", level).putExtra("plugged", plugged))
    }
}

@Composable fun DebugPanel(enabled: Boolean) {
    val context = LocalContext.current
    Text("Debug battery simulator", style = MaterialTheme.typography.titleMedium)
    listOf(listOf(50, 30, 20), listOf(10, 5, 1)).forEach { levels ->
        Row { levels.forEach { level ->
            TextButton(enabled = enabled, onClick = { FakeBatterySource.send(context, level, false) }) { Text("$level%") }
        } }
    }
    Row {
        TextButton(enabled = enabled, onClick = { FakeBatterySource.send(context, 99, true) }) { Text("Plug 99%") }
        TextButton(enabled = enabled, onClick = { FakeBatterySource.send(context, 100, true) }) { Text("Full") }
        TextButton(enabled = enabled, onClick = { FakeBatterySource.send(context, 99, false) }) { Text("Unplug") }
    }
}
