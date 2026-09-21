package com.armilla.neckcare.domain.usecase

import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.TestResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Motion boundary of the orb exercise, in degrees toward each direction. */
data class MotionBoundary(val left: Float, val right: Float, val down: Float, val up: Float) {
    companion object {
        /** PRD §7: conservative defaults when there is no measurement yet. */
        val DEFAULT = MotionBoundary(left = 45f, right = 45f, down = 30f, up = 30f)
    }
}

data class TrendPoint(val date: LocalDate, val totalDeg: Int)

/** Left/right asymmetry of one axis. [weakSide] is the direction with the smaller reading. */
data class Asymmetry(val axis: Axis, val differenceDeg: Int, val weakSide: Direction, val strongSide: Direction)

/**
 * Everything the lobby and result screens state about history. Pure: it only reads the list it is
 * given. Comparisons state facts, never targets or norms (PRD §13).
 */
class MobilityInsights(private val zone: ZoneId = ZoneId.systemDefault()) {

    private fun dateOf(result: TestResult): LocalDate =
        Instant.ofEpochMilli(result.finishedAtMillis).atZone(zone).toLocalDate()

    /** One value per calendar day: the last measurement of that day (PRD §11 "每日值"). */
    fun dailyResults(history: List<TestResult>): List<TestResult> =
        history
            .filter { it.measuredCount > 0 }
            .groupBy(::dateOf)
            .toSortedMap()
            .values
            .map { day -> day.maxBy { it.finishedAtMillis } }

    fun latest(history: List<TestResult>): TestResult? =
        history.filter { it.measuredCount > 0 }.maxByOrNull { it.finishedAtMillis }

    /** The measurement taken before [result], for "比上次多 / 少 N°". */
    fun previous(history: List<TestResult>, result: TestResult): TestResult? =
        history
            .filter { it.measuredCount > 0 && it.finishedAtMillis < result.finishedAtMillis }
            .maxByOrNull { it.finishedAtMillis }

    /** Up to [days] daily totals ending today, complete measurements only. */
    fun trend(history: List<TestResult>, today: LocalDate, days: Int = 14): List<TrendPoint> {
        val from = today.minusDays(days.toLong() - 1)
        return dailyResults(history)
            .filter { it.isComplete }
            .map { TrendPoint(dateOf(it), it.totalDeg) }
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
    }

    /** "比两周前": latest total minus the daily total closest to 14 days earlier, if any. */
    fun changeOverTwoWeeks(history: List<TestResult>, today: LocalDate): Int? {
        val daily = dailyResults(history).filter { it.isComplete }
        if (daily.size < 2) return null
        val latest = daily.last()
        val target = today.minusDays(14)
        val reference =
            daily.dropLast(1).minByOrNull { abs(dateOf(it).toEpochDay() - target.toEpochDay()) } ?: return null
        return latest.totalDeg - reference.totalDeg
    }

    /** Consecutive calendar days with a finished session, counting back from today or yesterday. */
    fun streakDays(history: List<TestResult>, today: LocalDate): Int {
        val days = history.map(::dateOf).toSet()
        var cursor = if (today in days) today else today.minusDays(1)
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /**
     * Left/right difference of one axis. Only rotation and lateral bending are compared; flexion
     * and extension are different movements, not two sides of one.
     */
    fun asymmetry(result: TestResult, axis: Axis): Asymmetry? {
        val (left, right) =
            when (axis) {
                Axis.ROTATION -> Direction.LEFT_ROTATION to Direction.RIGHT_ROTATION
                Axis.LATERAL -> Direction.LEFT_BEND to Direction.RIGHT_BEND
                Axis.FLEXION -> return null
            }
        val l = result.angle(left) ?: return null
        val r = result.angle(right) ?: return null
        return if (l <= r) Asymmetry(axis, r - l, weakSide = left, strongSide = right)
        else Asymmetry(axis, l - r, weakSide = right, strongSide = left)
    }

    /** The difference tag, the "均衡" copy and the 7:5 orb split all use this one rule. */
    fun isNotable(asymmetry: Asymmetry?): Boolean =
        asymmetry != null && asymmetry.differenceDeg > NOTABLE_DIFFERENCE_DEG

    /** Median of the last three valid readings per direction, times 0.9 (PRD §7, §11). */
    fun motionBoundary(history: List<TestResult>): MotionBoundary {
        fun limit(direction: Direction, fallback: Float): Float {
            val recent =
                history
                    .sortedByDescending { it.finishedAtMillis }
                    .mapNotNull { it.angle(direction) }
                    .take(3)
            if (recent.isEmpty()) return fallback
            val sorted = recent.sorted()
            val median =
                if (sorted.size % 2 == 1) sorted[sorted.size / 2].toFloat()
                else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
            return median * BOUNDARY_FACTOR
        }
        val d = MotionBoundary.DEFAULT
        return MotionBoundary(
            left = limit(Direction.LEFT_ROTATION, d.left),
            right = limit(Direction.RIGHT_ROTATION, d.right),
            down = limit(Direction.FLEXION, d.down),
            up = limit(Direction.EXTENSION, d.up),
        )
    }

    /** How many of the 12 catch points go to the left and right half (PRD §7 rule 7). */
    fun orbSplit(latest: TestResult?, autoAdjust: Boolean): Pair<Int, Int> {
        val rotation = latest?.let { asymmetry(it, Axis.ROTATION) }
        if (!autoAdjust || !isNotable(rotation)) return 6 to 6
        return if (rotation!!.weakSide == Direction.LEFT_ROTATION) 7 to 5 else 5 to 7
    }

    fun greeting(hourOfDay: Int): String =
        when (hourOfDay) {
            in 5..10 -> "早上好"
            in 11..12 -> "中午好"
            in 13..17 -> "下午好"
            else -> "晚上好"
        }

    fun roundedTotal(angles: Collection<Float>): Int = angles.sum().roundToInt()

    companion object {
        /** Confirmed with the product owner: show a difference only when it is strictly above 5°. */
        const val NOTABLE_DIFFERENCE_DEG = 5
        const val BOUNDARY_FACTOR = 0.9f
    }
}
