package com.armilla.neckcare.domain.usecase

import com.armilla.neckcare.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellRecorderTest {
    private val dt = 1f / 90f

    /** Moves at [speedDps] to [target], then holds for [holdSeconds]; returns the last snapshot. */
    private fun DwellRecorder.sweep(from: Float, target: Float, speedDps: Float, holdSeconds: Float): RecorderSnapshot {
        var angle = from
        val step = speedDps * dt * if (target >= from) 1f else -1f
        var last = update(dt, angle)
        while (if (step > 0) angle < target else angle > target) {
            angle = if (step > 0) minOf(target, angle + step) else maxOf(target, angle + step)
            last = update(dt, angle)
        }
        repeat((holdSeconds / dt).toInt()) { last = update(dt, target) }
        return last
    }

    @Test
    fun recordsAfterTwoSteadySecondsAtEndOfRange() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION)
        val s = recorder.sweep(0f, 71f, speedDps = 20f, holdSeconds = 2.3f)
        assertEquals(71, s.recordedAngleDeg)
        assertTrue(s.phase == RecorderPhase.RECORDED || s.phase == RecorderPhase.RETURNING)
    }

    @Test
    fun leftDirectionReadsThePositiveMagnitudeOfANegativeAxisAngle() {
        val recorder = DwellRecorder(Direction.LEFT_ROTATION)
        val s = recorder.sweep(0f, -64f, speedDps = 20f, holdSeconds = 2.3f)
        assertEquals(64, s.recordedAngleDeg)
    }

    @Test
    fun doesNotRecordBeforeTheDwellTimeIsUp() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION)
        val s = recorder.sweep(0f, 60f, speedDps = 20f, holdSeconds = 1.5f)
        assertNull(s.recordedAngleDeg)
        assertTrue(s.dwellProgress in 0.5f..0.95f)
    }

    @Test
    fun doesNotRecordNearTheStartingPosition() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION)
        val s = recorder.sweep(0f, 8f, speedDps = 10f, holdSeconds = 3f)
        assertNull(s.recordedAngleDeg)
        assertEquals(0f, s.dwellProgress, 0.001f)
    }

    @Test
    fun aFastTurnIsNotPunishedByDefault() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION)
        val s = recorder.sweep(0f, 70f, speedDps = 120f, holdSeconds = 2.5f)
        assertEquals(70, s.recordedAngleDeg)
        assertEquals(0, s.retries)
    }

    @Test
    fun turningFasterThanSixtyDegreesPerSecondVoidsTheReadingWhenThatRuleIsOn() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION, RecorderConfig(voidWhenTooFast = true))
        val s = recorder.sweep(0f, 70f, speedDps = 120f, holdSeconds = 2.5f)
        assertNull(s.recordedAngleDeg)
        assertEquals(RecorderPhase.VOIDED, s.phase)
        assertEquals(1, s.retries)
    }

    @Test
    fun aVoidedDirectionCanBeMeasuredAgainAfterReturningToNeutral() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION, RecorderConfig(voidWhenTooFast = true))
        recorder.sweep(0f, 70f, speedDps = 120f, holdSeconds = 0.5f)
        recorder.sweep(70f, 0f, speedDps = 25f, holdSeconds = 0.3f)
        val s = recorder.sweep(0f, 68f, speedDps = 20f, holdSeconds = 2.3f)
        assertEquals(68, s.recordedAngleDeg)
    }

    @Test
    fun speedTiersFollowThirtyAndSixtyDegreesPerSecond() {
        assertEquals(SpeedTier.OK, DwellRecorder(Direction.RIGHT_ROTATION).sweep(0f, 30f, 20f, 0f).speedTier)
        assertEquals(SpeedTier.FAST, DwellRecorder(Direction.RIGHT_ROTATION).sweep(0f, 40f, 45f, 0f).speedTier)
        assertEquals(SpeedTier.TOO_FAST, DwellRecorder(Direction.RIGHT_ROTATION).sweep(0f, 60f, 90f, 0f).speedTier)
    }

    @Test
    fun implausiblyLargeAnglesAreVoided() {
        val recorder = DwellRecorder(Direction.LEFT_BEND)
        val s = recorder.sweep(0f, -75f, speedDps = 20f, holdSeconds = 2.5f)
        assertNull(s.recordedAngleDeg)
        assertEquals(RecorderPhase.VOIDED, s.phase)
    }

    @Test
    fun movesOnOnlyAfterTheHeadIsBackInsideTheNeutralBand() {
        val recorder = DwellRecorder(Direction.RIGHT_ROTATION)
        recorder.sweep(0f, 50f, speedDps = 20f, holdSeconds = 2.8f)
        assertEquals(RecorderPhase.RETURNING, recorder.update(dt, 50f).phase)
        assertEquals(RecorderPhase.RETURNING, recorder.sweep(50f, 12f, 25f, 0.2f).phase)
        assertEquals(RecorderPhase.DONE, recorder.sweep(12f, 3f, 25f, 0.2f).phase)
    }

    @Test
    fun offersToSkipAfterTwentySecondsWithoutAReading() {
        val recorder = DwellRecorder(Direction.FLEXION)
        assertFalse(recorder.sweep(0f, 0f, 1f, holdSeconds = 19f).showSkipHint)
        assertTrue(recorder.sweep(0f, 0f, 1f, holdSeconds = 1.5f).showSkipHint)
    }
}
