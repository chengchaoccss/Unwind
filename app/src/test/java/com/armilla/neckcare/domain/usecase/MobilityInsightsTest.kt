package com.armilla.neckcare.domain.usecase

import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Measurement
import com.armilla.neckcare.domain.model.MeasurementStatus
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityInsightsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val insights = MobilityInsights(zone)
    private var nextId = 1L

    private fun result(date: String, time: String, vararg angles: Int?): TestResult {
        val millis = LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()
        val measurements =
            Direction.testOrder.mapIndexed { i, d ->
                val a = angles.getOrNull(i)
                if (a == null) Measurement(d, 0, status = MeasurementStatus.SKIPPED) else Measurement(d, a)
            }
        return TestResult(nextId++, millis, SessionMode.FULL, measurements)
    }

    // Order: 左旋, 右旋, 前屈, 后仰, 左侧屈, 右侧屈 — the figures on the design boards.
    private val sep20 = result("2026-09-20", "08:00:00", 62, 71, 46, 58, 36, 41)
    private val sep21 = result("2026-09-21", "07:38:00", 64, 71, 47, 59, 37, 42)

    @Test
    fun totalsMatchTheDesignBoards() {
        assertEquals(314, sep20.totalDeg)
        assertEquals(320, sep21.totalDeg)
        assertEquals(6, sep21.totalDeg - insights.previous(listOf(sep20, sep21), sep21)!!.totalDeg)
    }

    @Test
    fun rotationDifferenceOfSevenIsNotableButLateralDifferenceOfFiveIsNot() {
        val rotation = insights.asymmetry(sep21, Axis.ROTATION)!!
        assertEquals(7, rotation.differenceDeg)
        assertEquals(Direction.LEFT_ROTATION, rotation.weakSide)
        assertTrue(insights.isNotable(rotation))

        val lateral = insights.asymmetry(sep21, Axis.LATERAL)!!
        assertEquals(5, lateral.differenceDeg)
        assertFalse(insights.isNotable(lateral))
    }

    @Test
    fun flexionAndExtensionAreNeverCompared() {
        assertNull(insights.asymmetry(sep21, Axis.FLEXION))
    }

    @Test
    fun orbsFavourTheWeakSideOnlyWhenAutoAdjustIsOnAndTheDifferenceIsNotable() {
        assertEquals(7 to 5, insights.orbSplit(sep21, autoAdjust = true))
        assertEquals(6 to 6, insights.orbSplit(sep21, autoAdjust = false))
        val balanced = result("2026-09-22", "08:00:00", 68, 71, 47, 59, 37, 42)
        assertEquals(6 to 6, insights.orbSplit(balanced, autoAdjust = true))
        assertEquals(6 to 6, insights.orbSplit(null, autoAdjust = true))
    }

    @Test
    fun boundaryIsNinetyPercentOfTheMedianOfTheLastThreeReadings() {
        val older = result("2026-09-18", "08:00:00", 50, 80, 40, 50, 30, 40)
        val oldest = result("2026-09-10", "08:00:00", 10, 10, 10, 10, 10, 10)
        val b = insights.motionBoundary(listOf(oldest, older, sep20, sep21))
        assertEquals(62 * 0.9f, b.left, 0.01f) // median of 64, 62, 50
        assertEquals(71 * 0.9f, b.right, 0.01f) // median of 71, 71, 80
        assertEquals(46 * 0.9f, b.down, 0.01f)
    }

    @Test
    fun boundaryFallsBackToConservativeDefaultsWithoutHistory() {
        assertEquals(MotionBoundary.DEFAULT, insights.motionBoundary(emptyList()))
    }

    @Test
    fun theLastMeasurementOfADayIsTheDailyValue() {
        val retest = result("2026-09-21", "19:00:00", 66, 72, 47, 59, 37, 42)
        val daily = insights.dailyResults(listOf(sep20, sep21, retest))
        assertEquals(2, daily.size)
        assertEquals(retest.sessionId, daily.last().sessionId)
    }

    @Test
    fun streakCountsBackFromTodayAndSurvivesNotHavingPractisedYetToday() {
        val history = (16..21).map { result("2026-09-$it", "08:00:00", 60, 70, 45, 55, 35, 40) }
        assertEquals(6, insights.streakDays(history, LocalDate.parse("2026-09-21")))
        assertEquals(6, insights.streakDays(history, LocalDate.parse("2026-09-22")))
        assertEquals(0, insights.streakDays(history, LocalDate.parse("2026-09-24")))
    }

    @Test
    fun twoWeekChangeNeedsAtLeastTwoDays() {
        assertNull(insights.changeOverTwoWeeks(listOf(sep21), LocalDate.parse("2026-09-21")))
        val sep7 = result("2026-09-07", "08:00:00", 58, 66, 42, 54, 33, 38)
        assertEquals(320 - 291, insights.changeOverTwoWeeks(listOf(sep7, sep20, sep21), LocalDate.parse("2026-09-21")))
    }

    @Test
    fun skippedDirectionsAreLeftOutOfTheTotalAndTheTrend() {
        val partial = result("2026-09-22", "08:00:00", 64, 71, null, 59, 37, 42)
        assertEquals(5, partial.measuredCount)
        assertEquals(273, partial.totalDeg)
        assertTrue(insights.trend(listOf(partial), LocalDate.parse("2026-09-22")).isEmpty())
    }

    @Test
    fun greetingFollowsTheFourDayParts() {
        assertEquals("早上好", insights.greeting(7))
        assertEquals("中午好", insights.greeting(12))
        assertEquals("下午好", insights.greeting(15))
        assertEquals("晚上好", insights.greeting(22))
        assertEquals("晚上好", insights.greeting(3))
    }
}
