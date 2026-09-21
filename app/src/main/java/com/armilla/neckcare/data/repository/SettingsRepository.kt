package com.armilla.neckcare.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Posture(val label: String) {
    SEATED("坐姿"),
    STANDING("站姿"),
}

/** PRD §12 Settings singleton. */
data class Settings(
    val autoAdjust: Boolean = true,
    val remindersEnabled: Boolean = true,
    val reminderTimes: List<String> = listOf("10:30", "15:30"),
    val posture: Posture = Posture.SEATED,
    val ringRadiusM: Float = 0.19f,
    val ambientVolume: Float = 0.3f,
    val onboarded: Boolean = false,
    val lastSessionEndMillis: Long = 0L,
)

interface SettingsRepository {
    val settings: StateFlow<Settings>

    fun update(transform: (Settings) -> Settings)

    fun setAutoAdjust(enabled: Boolean) = update { it.copy(autoAdjust = enabled) }

    /** Back to first-run defaults, used by "删除全部数据". */
    fun reset() = update { Settings() }
}

class PreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("armilla_settings", Context.MODE_PRIVATE)
    private val state =
        MutableStateFlow(
            Settings(
                autoAdjust = prefs.getBoolean("auto_adjust", true),
                remindersEnabled = prefs.getBoolean("reminders_enabled", true),
                reminderTimes = (prefs.getString("reminder_times", null) ?: "10:30,15:30").split(","),
                posture = runCatching { Posture.valueOf(prefs.getString("posture", "SEATED")!!) }.getOrDefault(Posture.SEATED),
                ringRadiusM = prefs.getFloat("ring_radius_m", 0.19f),
                ambientVolume = prefs.getFloat("ambient_volume", 0.3f),
                onboarded = prefs.getBoolean("onboarded", false),
                lastSessionEndMillis = prefs.getLong("last_session_end", 0L),
            )
        )
    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        state.value = next
        prefs.edit()
            .putBoolean("auto_adjust", next.autoAdjust)
            .putBoolean("reminders_enabled", next.remindersEnabled)
            .putString("reminder_times", next.reminderTimes.joinToString(","))
            .putString("posture", next.posture.name)
            .putFloat("ring_radius_m", next.ringRadiusM)
            .putFloat("ambient_volume", next.ambientVolume)
            .putBoolean("onboarded", next.onboarded)
            .putLong("last_session_end", next.lastSessionEndMillis)
            .apply()
    }
}

class InMemorySettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun update(transform: (Settings) -> Settings) {
        state.value = transform(state.value)
    }
}
