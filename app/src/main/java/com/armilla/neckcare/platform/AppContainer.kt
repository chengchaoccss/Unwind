package com.armilla.neckcare.platform

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.armilla.neckcare.data.repository.PreferencesSettingsRepository
import com.armilla.neckcare.data.repository.SessionRepository
import com.armilla.neckcare.data.repository.SettingsRepository
import com.armilla.neckcare.data.repository.SqliteSessionRepository

/** Hand-rolled dependency container: two repositories do not justify a DI framework. */
object AppContainer {
    lateinit var sessions: SessionRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    fun init(context: Context) {
        if (::sessions.isInitialized) return
        sessions = SqliteSessionRepository(context)
        settings = PreferencesSettingsRepository(context)
    }

    inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        }
}
