package com.armilla.neckcare.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armilla.neckcare.data.repository.Posture
import com.armilla.neckcare.data.repository.SessionRepository
import com.armilla.neckcare.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainPage {
    LOBBY,
    RECORDS,
    COURSES,
    SETTINGS,
}

/** First-run steps of PRD §10 that precede the first session. */
enum class OnboardingStep {
    HEALTH,
    POSTURE,
    REMINDERS,
}

data class MainUiState(
    val page: MainPage = MainPage.LOBBY,
    val onboarding: OnboardingStep? = null,
    /** The health notice opened again from settings. */
    val reviewingHealthNotice: Boolean = false,
    val confirmingDelete: Boolean = false,
    /** One calm line shown in the lobby, e.g. low battery or "刚练过". */
    val notice: String? = null,
    val message: String? = null,
    /** Bumped whenever reminders must be rescheduled or the layout re-centred. */
    val reminderSerial: Int = 0,
    val recentreSerial: Int = 0,
) {
    val pageOpen: Boolean
        get() = onboarding != null || page != MainPage.LOBBY
}

sealed interface MainEvent {
    data class Navigate(val page: MainPage) : MainEvent

    data object HealthAccepted : MainEvent

    data class PostureChosen(val posture: Posture) : MainEvent

    data class RemindersChosen(val enabled: Boolean) : MainEvent

    data object ReviewHealthNotice : MainEvent

    data object AskDelete : MainEvent

    data object CancelDelete : MainEvent

    data object ConfirmDelete : MainEvent

    data object Export : MainEvent

    data object Recentre : MainEvent

    data class ShiftReminder(val index: Int, val minutes: Int) : MainEvent

    data class SetReminders(val enabled: Boolean) : MainEvent

    data class SetPosture(val posture: Posture) : MainEvent

    data class SetAutoAdjust(val enabled: Boolean) : MainEvent

    data class NudgeRing(val deltaM: Float) : MainEvent

    data class NudgeVolume(val delta: Float) : MainEvent
}

class MainViewModel(
    private val settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val export: suspend () -> String = { "" },
    private val batteryPercent: () -> Int? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var repeatWarned = false

    init {
        val level = batteryPercent()
        _state.value =
            MainUiState(
                onboarding = if (settings.settings.value.onboarded) null else OnboardingStep.HEALTH,
                // PRD §10: below 10 %, say so once in the lobby and never interrupt an exercise.
                notice = if (level != null && level < 10) "电量不到 10%，练完记得充电" else null,
            )
    }

    /**
     * PRD §13: within 30 minutes of the last session the first tap only says so; tapping again
     * starts anyway, the hint can be ignored.
     */
    fun mayStartSession(): Boolean {
        val last = settings.settings.value.lastSessionEndMillis
        if (!repeatWarned && last > 0 && now() - last < THIRTY_MINUTES_MS) {
            repeatWarned = true
            _state.update { it.copy(notice = "刚练过，隔一会儿再来") }
            return false
        }
        _state.update { it.copy(notice = null) }
        return true
    }

    fun onSessionFinished() {
        repeatWarned = false
        settings.update { it.copy(lastSessionEndMillis = now()) }
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.Navigate -> if (_state.value.onboarding == null) _state.update { it.copy(page = event.page, confirmingDelete = false, reviewingHealthNotice = false, message = null) }
            MainEvent.HealthAccepted ->
                _state.update { if (it.reviewingHealthNotice) it.copy(reviewingHealthNotice = false) else it.copy(onboarding = OnboardingStep.POSTURE) }
            is MainEvent.PostureChosen -> {
                settings.update { it.copy(posture = event.posture) }
                _state.update { it.copy(onboarding = OnboardingStep.REMINDERS) }
            }
            is MainEvent.RemindersChosen -> {
                settings.update { it.copy(remindersEnabled = event.enabled, onboarded = true) }
                _state.update { it.copy(onboarding = null, page = MainPage.LOBBY, reminderSerial = it.reminderSerial + 1) }
            }
            MainEvent.ReviewHealthNotice -> _state.update { it.copy(reviewingHealthNotice = true) }
            MainEvent.AskDelete -> _state.update { it.copy(confirmingDelete = true) }
            MainEvent.CancelDelete -> _state.update { it.copy(confirmingDelete = false) }
            MainEvent.ConfirmDelete ->
                viewModelScope.launch {
                    // PRD §12: wipe every entity and go back to first-run onboarding.
                    sessions.deleteAll()
                    settings.reset()
                    _state.value = MainUiState(onboarding = OnboardingStep.HEALTH, reminderSerial = _state.value.reminderSerial + 1)
                }
            MainEvent.Export ->
                viewModelScope.launch {
                    val result = runCatching { export() }
                    _state.update { it.copy(message = result.fold({ path -> "已导出到 $path" }, { "导出没成功，稍后再试" })) }
                }
            MainEvent.Recentre -> _state.update { it.copy(recentreSerial = it.recentreSerial + 1, message = "已按现在的朝向重新校准") }
            is MainEvent.ShiftReminder -> {
                settings.update { s ->
                    s.copy(reminderTimes = s.reminderTimes.mapIndexed { i, t -> if (i == event.index) shift(t, event.minutes) else t })
                }
                _state.update { it.copy(reminderSerial = it.reminderSerial + 1) }
            }
            is MainEvent.SetReminders -> {
                settings.update { it.copy(remindersEnabled = event.enabled) }
                _state.update { it.copy(reminderSerial = it.reminderSerial + 1) }
            }
            is MainEvent.SetPosture -> settings.update { it.copy(posture = event.posture) }
            is MainEvent.SetAutoAdjust -> settings.update { it.copy(autoAdjust = event.enabled) }
            is MainEvent.NudgeRing -> settings.update { it.copy(ringRadiusM = (it.ringRadiusM + event.deltaM).coerceIn(0.13f, 0.27f)) }
            is MainEvent.NudgeVolume -> settings.update { it.copy(ambientVolume = ((it.ambientVolume + event.delta) * 10).toInt().coerceIn(0, 10) / 10f) }
        }
    }

    private fun shift(time: String, minutes: Int): String {
        val parts = time.split(":")
        val total = ((parts[0].toInt() * 60 + parts[1].toInt() + minutes) % 1440 + 1440) % 1440
        return "%d:%02d".format(total / 60, total % 60)
    }

    private companion object {
        const val THIRTY_MINUTES_MS = 30 * 60 * 1000L
    }
}
