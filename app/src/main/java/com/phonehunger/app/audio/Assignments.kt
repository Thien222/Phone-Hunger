package com.phonehunger.app.audio

import android.content.Context
import com.phonehunger.app.battery.BatteryEvent

class Assignments(context: Context) {
    private val prefs = context.getSharedPreferences("audio_assignments_v1", Context.MODE_PRIVATE)
    private fun ids(event: BatteryEvent): List<String> = prefs.getString("${event.name}_ids", null)?.split('|')?.filter { it.isNotBlank() }
        ?: prefs.getString(event.name, null)?.let(::listOf) ?: emptyList()
    fun pick(event: BatteryEvent): String? = ids(event).let { clips -> if (prefs.getBoolean("${event.name}_random", false)) clips.randomOrNull() else clips.firstOrNull() }
    fun set(event: BatteryEvent, id: String?) { prefs.edit().putString("${event.name}_ids", id ?: "").remove(event.name).putBoolean("${event.name}_random", false).apply() }
    fun addRandom(event: BatteryEvent, id: String) {
        val list = (ids(event) + id).distinct()
        prefs.edit().putString("${event.name}_ids", list.joinToString("|")).remove(event.name).putBoolean("${event.name}_random", true).apply()
    }
    fun clearClip(id: String) { BatteryEvent.entries.forEach { event ->
        val remaining = ids(event).filterNot { it == id }
        prefs.edit().putString("${event.name}_ids", remaining.joinToString("|")).remove(event.name).putBoolean("${event.name}_random", remaining.size > 1).apply()
    } }
    fun clearAll() { prefs.edit().clear().apply() }
}
