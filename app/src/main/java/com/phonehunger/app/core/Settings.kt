package com.phonehunger.app.core

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("phonehunger_settings_v1")

data class UserSettings(
    val enabled: Boolean = false, val volume: Float = 0.8f, val voiceLanguage: String = "en-US",
    val quietEnabled: Boolean = false, val quietStart: Int = 22, val quietEnd: Int = 7,
    val thresholds: Set<Int> = setOf(20, 10, 5, 1), val reboot: Boolean = false,
    val skinColor: Int = 0xff72e2a0.toInt(), val overlay: Boolean = false,
    val cooldownSeconds: Int = 30, val personality: Int = 0, val voiceName: String? = null,
    val skinSize: Int = 220, val overlayLeft: Boolean = false
)

class Settings(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val volume = floatPreferencesKey("volume")
        val language = stringPreferencesKey("language")
        val quiet = booleanPreferencesKey("quiet")
        val start = intPreferencesKey("start")
        val end = intPreferencesKey("end")
        val thresholds = stringSetPreferencesKey("thresholds")
        val reboot = booleanPreferencesKey("reboot")
        val color = intPreferencesKey("color")
        val overlay = booleanPreferencesKey("overlay")
        val cooldown = intPreferencesKey("cooldown")
        val personality = intPreferencesKey("personality")
        val voiceName = stringPreferencesKey("voice_name")
        val skinSize = intPreferencesKey("skin_size")
        val overlayLeft = booleanPreferencesKey("overlay_left")
    }
    val flow: Flow<UserSettings> = context.store.data.map { p ->
        UserSettings(
            p[Keys.enabled] ?: false, p[Keys.volume] ?: .8f, p[Keys.language] ?: "en-US",
            p[Keys.quiet] ?: false, p[Keys.start] ?: 22, p[Keys.end] ?: 7,
            p[Keys.thresholds]?.mapNotNull(String::toIntOrNull)?.toSet() ?: setOf(20, 10, 5, 1),
            p[Keys.reboot] ?: false, p[Keys.color] ?: 0xff72e2a0.toInt(), p[Keys.overlay] ?: false,
            p[Keys.cooldown] ?: 30, p[Keys.personality] ?: 0, p[Keys.voiceName],
            p[Keys.skinSize] ?: 220, p[Keys.overlayLeft] ?: false
        )
    }
    suspend fun enabled(value: Boolean) = context.store.edit { it[Keys.enabled] = value }
    suspend fun volume(value: Float) = context.store.edit { it[Keys.volume] = value.coerceIn(0f, 1f) }
    suspend fun language(value: String) = context.store.edit { it[Keys.language] = value }
    suspend fun quiet(value: Boolean) = context.store.edit { it[Keys.quiet] = value }
    suspend fun quietHours(start: Int, end: Int) = context.store.edit { it[Keys.start] = start.coerceIn(0, 23); it[Keys.end] = end.coerceIn(0, 23) }
    suspend fun threshold(level: Int, value: Boolean) = context.store.edit {
        val set = (it[Keys.thresholds] ?: setOf("20", "10", "5", "1")).toMutableSet()
        if (value) set.add(level.toString()) else set.remove(level.toString())
        it[Keys.thresholds] = set
    }
    suspend fun reboot(value: Boolean) = context.store.edit { it[Keys.reboot] = value }
    suspend fun color(value: Int) = context.store.edit { it[Keys.color] = value }
    suspend fun overlay(value: Boolean) = context.store.edit { it[Keys.overlay] = value }
    suspend fun cooldown(value: Int) = context.store.edit { it[Keys.cooldown] = value.coerceIn(0, 300) }
    suspend fun personality(value: Int) = context.store.edit { it[Keys.personality] = value.coerceIn(0, 3) }
    suspend fun voiceName(value: String?) = context.store.edit { if (value == null) it.remove(Keys.voiceName) else it[Keys.voiceName] = value }
    suspend fun skinSize(value: Int) = context.store.edit { it[Keys.skinSize] = value.coerceIn(160, 280) }
    suspend fun overlayLeft(value: Boolean) = context.store.edit { it[Keys.overlayLeft] = value }
    suspend fun reset() = context.store.edit { it.clear() }
}

fun isQuiet(settings: UserSettings, hour: Int): Boolean = settings.quietEnabled && when {
    settings.quietStart == settings.quietEnd -> true
    settings.quietStart < settings.quietEnd -> hour in settings.quietStart until settings.quietEnd
    else -> hour >= settings.quietStart || hour < settings.quietEnd
}
