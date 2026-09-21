package com.armilla.neckcare.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** PRD §12 Settings singleton, reduced to what batch one uses. */
data class Settings(
    val autoAdjust: Boolean = true,
    val reminderTimes: List<String> = listOf("10:30", "15:30"),
)

interface SettingsRepository {
    val settings: StateFlow<Settings>

    fun setAutoAdjust(enabled: Boolean)
}

class PreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val prefs = context.applicationContext.getSharedPreferences("armilla_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(Settings(autoAdjust = prefs.getBoolean(KEY_AUTO_ADJUST, true)))
    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun setAutoAdjust(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ADJUST, enabled).apply()
        state.value = state.value.copy(autoAdjust = enabled)
    }

    private companion object {
        const val KEY_AUTO_ADJUST = "auto_adjust"
    }
}

class InMemorySettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = state.asStateFlow()

    override fun setAutoAdjust(enabled: Boolean) {
        state.value = state.value.copy(autoAdjust = enabled)
    }
}
