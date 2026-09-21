package com.armilla.neckcare.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armilla.neckcare.data.repository.SessionRepository
import com.armilla.neckcare.data.repository.SettingsRepository
import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.usecase.MobilityInsights
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LobbyViewModel(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val insights: MobilityInsights = MobilityInsights(),
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) : ViewModel() {
    private val _state = MutableStateFlow(LobbyUiState())
    val state: StateFlow<LobbyUiState> = _state.asStateFlow()

    init {
        onEvent(LobbyEvent.Refresh)
    }

    fun onEvent(event: LobbyEvent) {
        when (event) {
            LobbyEvent.Refresh -> viewModelScope.launch { _state.value = load() }
        }
    }

    private suspend fun load(): LobbyUiState {
        val moment = now()
        val today = moment.toLocalDate()
        val history = sessions.history()
        val latest = insights.latest(history)
        val streak = insights.streakDays(history, today)
        val dateText = "${today.monthValue}月${today.dayOfMonth}日 ${weekday(today.dayOfWeek)}"
        val base =
            LobbyUiState(
                loading = false,
                greeting = insights.greeting(moment.hour),
                dateLine = if (streak > 0) "$dateText，连续第 $streak 天" else dateText,
                nextReminder = nextReminder(moment.toLocalTime(), settings.settings.value.reminderTimes),
            )
        if (latest == null) return base

        val measuredOn = java.time.Instant.ofEpochMilli(latest.finishedAtMillis).atZone(moment.zone).toLocalDate()
        val angles = Direction.entries.mapNotNull { d -> latest.angle(d)?.let { d to it } }.toMap()
        val rotation = insights.asymmetry(latest, Axis.ROTATION)
        val autoAdjust = settings.settings.value.autoAdjust
        val (headline, detail) =
            when {
                rotation == null -> null to null
                !insights.isNotable(rotation) -> "左右很均衡" to "保持现在的节奏"
                else -> {
                    val strong = if (rotation.strongSide == Direction.RIGHT_ROTATION) "右边" else "左边"
                    val weak = if (rotation.weakSide == Direction.LEFT_ROTATION) "左" else "右"
                    "左右旋转相差 ${rotation.differenceDeg}°" to
                        if (autoAdjust) "${strong}更灵活，今天会向${weak}多放一组光球" else "${strong}更灵活"
                }
            }
        return base.copy(
            armillaryTitle = "上次测量，${measuredOn.monthValue}月${measuredOn.dayOfMonth}日",
            armillarySubtitle = "走近一步，可以绕着它看",
            angles = angles,
            trend = trend(history, today, latest.totalDeg, latest.isComplete),
            balanceHeadline = headline,
            balanceDetail = detail,
        )
    }

    private fun trend(
        history: List<com.armilla.neckcare.domain.model.TestResult>,
        today: java.time.LocalDate,
        latestTotal: Int,
        latestComplete: Boolean,
    ): TrendUi? {
        val points = insights.trend(history, today)
        if (points.size < 2 || !latestComplete) return null
        val change = insights.changeOverTwoWeeks(history, today)
        val values = points.map { it.totalDeg }
        val low = values.min()
        val high = values.max()
        // Two quiet reference lines on round tens inside the data range, as on the artboard.
        val lower = ((low + 9) / 10) * 10
        val upper = (high / 10) * 10
        return TrendUi(
            totalText = "$latestTotal°",
            changeText =
                change?.let { if (it >= 0) "比两周前多 $it°" else "比两周前少 ${abs(it)}°" },
            changeIsPositive = (change ?: 0) >= 0,
            values = values,
            firstDateLabel = "${points.first().date.monthValue}/${points.first().date.dayOfMonth}",
            lastDateLabel = "${points.last().date.monthValue}/${points.last().date.dayOfMonth}",
            referenceLines = if (upper > lower) listOf(lower, upper) else listOf(lower),
        )
    }

    private fun nextReminder(time: LocalTime, reminders: List<String>): String {
        val parsed = reminders.mapNotNull { runCatching { LocalTime.parse(it) }.getOrNull() }.sorted()
        val next = parsed.firstOrNull { it.isAfter(time) } ?: parsed.firstOrNull() ?: return ""
        return "%d:%02d".format(next.hour, next.minute)
    }

    private fun weekday(day: DayOfWeek) =
        when (day) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
}
