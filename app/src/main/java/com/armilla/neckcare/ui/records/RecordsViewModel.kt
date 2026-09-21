package com.armilla.neckcare.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armilla.neckcare.data.repository.SessionRepository
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.TestResult
import com.armilla.neckcare.domain.usecase.MobilityInsights
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordsRange(val label: String, val days: Int?) {
    TWO_WEEKS("14 天", 14),
    MONTH("30 天", 30),
    ALL("全部", null),
}

data class DirectionSeries(val direction: Direction, val values: List<Int>, val shownDeg: Int?)

data class CalendarDay(val date: LocalDate, val hasRecord: Boolean)

data class RecordsUiState(
    val range: RecordsRange = RecordsRange.TWO_WEEKS,
    val totals: List<Int> = emptyList(),
    val firstLabel: String = "",
    val lastLabel: String = "",
    val series: List<DirectionSeries> = emptyList(),
    val calendar: List<CalendarDay> = emptyList(),
    val selected: LocalDate? = null,
    val selectedLabel: String = "",
    val empty: Boolean = true,
)

sealed interface RecordsEvent {
    data object Refresh : RecordsEvent

    data class Range(val range: RecordsRange) : RecordsEvent

    data class SelectDay(val date: LocalDate) : RecordsEvent
}

/** 记录 (PRD §10): total trend over 14 / 30 days / all, a line per direction, a four-week calendar. */
class RecordsViewModel(
    private val sessions: SessionRepository,
    private val insights: MobilityInsights = MobilityInsights(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _state = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()
    private var history: List<TestResult> = emptyList()

    init {
        onEvent(RecordsEvent.Refresh)
    }

    fun onEvent(event: RecordsEvent) {
        when (event) {
            RecordsEvent.Refresh -> viewModelScope.launch { history = sessions.history(); render(_state.value.range, _state.value.selected) }
            is RecordsEvent.Range -> render(event.range, _state.value.selected)
            is RecordsEvent.SelectDay -> render(_state.value.range, if (_state.value.selected == event.date) null else event.date)
        }
    }

    private fun render(range: RecordsRange, selected: LocalDate?) {
        val daily = insights.dailyResults(history)
        val day = { r: TestResult -> Instant.ofEpochMilli(r.finishedAtMillis).atZone(zone).toLocalDate() }
        val end = daily.lastOrNull()?.let(day) ?: today()
        val inRange = daily.filter { range.days == null || !day(it).isBefore(end.minusDays(range.days - 1L)) }
        val complete = inRange.filter { it.isComplete }
        val shown = selected?.let { s -> daily.firstOrNull { day(it) == s } } ?: daily.lastOrNull()
        val recorded = daily.map(day).toSet()
        fun label(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"
        _state.value =
            RecordsUiState(
                range = range,
                totals = complete.map { it.totalDeg },
                firstLabel = complete.firstOrNull()?.let { label(day(it)) }.orEmpty(),
                lastLabel = complete.lastOrNull()?.let { label(day(it)) }.orEmpty(),
                series = Direction.entries.map { d -> DirectionSeries(d, inRange.mapNotNull { it.angle(d) }, shown?.angle(d)) },
                calendar = (27 downTo 0).map { back -> today().minusDays(back.toLong()).let { CalendarDay(it, it in recorded) } },
                selected = selected?.takeIf { it in recorded },
                selectedLabel = shown?.let { r -> day(r).let { "${it.monthValue}月${it.dayOfMonth}日的读数" } }.orEmpty(),
                empty = daily.isEmpty(),
            )
    }
}
