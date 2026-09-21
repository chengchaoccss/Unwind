package com.armilla.neckcare.ui.result

import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import com.armilla.neckcare.domain.usecase.MobilityInsights
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Turns a finished test into the copy of the "今日数据" board (PRD §9). Statements are facts
 * about the user's own numbers: no norms, diagnoses or targets.
 */
class ResultPresenter(private val insights: MobilityInsights = MobilityInsights(), private val zone: ZoneId = ZoneId.systemDefault()) {

    fun present(result: TestResult, history: List<TestResult>, autoAdjust: Boolean, reminders: List<String>): ResultUiState {
        val previous = insights.previous(history, result)
        val time = Instant.ofEpochMilli(result.finishedAtMillis).atZone(zone)
        val dateText = "%d月%d日 %02d:%02d".format(time.monthValue, time.dayOfMonth, time.hour, time.minute)
        val complete = result.isComplete
        val delta = if (complete && previous?.isComplete == true) result.totalDeg - previous.totalDeg else null
        val caption =
            when {
                !complete -> "${result.measuredCount} 个方向合计"
                delta == null -> "合计"
                delta >= 0 -> "合计，比上次多 $delta°"
                else -> "合计，比上次少 ${abs(delta)}°"
            }
        return ResultUiState(
            dateLine = if (previous != null) "$dateText，白线是上次的位置" else dateText,
            totalText = "${result.totalDeg}°",
            totalCaption = caption,
            totalCaptionIsPositive = (delta ?: 0) >= 0 && delta != null,
            rows =
                listOf(
                    row("旋转", Axis.ROTATION, Direction.LEFT_ROTATION, Direction.RIGHT_ROTATION, result, previous),
                    row("屈伸", Axis.FLEXION, Direction.FLEXION, Direction.EXTENSION, result, previous),
                    row("侧屈", Axis.LATERAL, Direction.LEFT_BEND, Direction.RIGHT_BEND, result, previous),
                ),
            angles = Direction.entries.mapNotNull { d -> result.angle(d)?.let { d to it } }.toMap(),
            nextWeekText = nextWeek(result, previous, autoAdjust),
            reminderTimes = reminders,
        )
    }

    private fun row(title: String, axis: Axis, left: Direction, right: Direction, result: TestResult, previous: TestResult?): AxisRow {
        val asymmetry = insights.asymmetry(result, axis)
        return AxisRow(
            title = title,
            differenceTag = asymmetry?.takeIf(insights::isNotable)?.let { "相差 ${it.differenceDeg}°" },
            left = SideReading(left.label, result.angle(left), previous?.angle(left), left.side),
            right = SideReading(right.label, result.angle(right), previous?.angle(right), right.side),
        )
    }

    /** Three templates: first record, balanced, or a notable left/right rotation difference. */
    private fun nextWeek(result: TestResult, previous: TestResult?, autoAdjust: Boolean): String {
        if (previous == null) return "这是你的第一次记录，明天再测就能看到变化。"
        val rotation = insights.asymmetry(result, Axis.ROTATION)
        if (rotation == null || !insights.isNotable(rotation)) return "左右很均衡，保持现在的节奏。"
        val weak = if (rotation.weakSide == Direction.LEFT_ROTATION) "左旋" else "右旋"
        val strong = if (rotation.weakSide == Direction.LEFT_ROTATION) "右旋" else "左旋"
        val weakWord = if (rotation.weakSide == Direction.LEFT_ROTATION) "左" else "右"
        val before = insights.asymmetry(previous, Axis.ROTATION)?.differenceDeg
        val day = { r: TestResult -> Instant.ofEpochMilli(r.finishedAtMillis).atZone(zone).toLocalDate() }
        val since = if (day(previous).plusDays(1) == day(result)) "昨天" else "上次"
        val change =
            when {
                before == null || before == rotation.differenceDeg -> ""
                before > rotation.differenceDeg -> "，比${since}缩小了 ${before - rotation.differenceDeg}°"
                else -> "，比${since}扩大了 ${rotation.differenceDeg - before}°"
            }
        val first = "${weak}比${strong}少 ${rotation.differenceDeg}°$change。"
        // The test-only path does not mention exercise plans (PRD §9 last rule).
        val plan = if (autoAdjust && result.mode == SessionMode.FULL) "练习时会向${weakWord}多放一组光球。" else ""
        return first + plan
    }
}
