package com.armilla.neckcare.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoulderExerciseTest {
    private val dt = 1f / 72f

    /** Circles both hands at [degPerSecond] for [seconds]; sign +1 backward, -1 forward. */
    private fun ShoulderExercise.circle(seconds: Float, degPerSecond: Float, sign: Int, offPlane: Float = 0.02f, start: Float = 0f): Pair<ShoulderSnapshot, Float> {
        var angle = start
        var snap = update(0f, HandSample(angle, offPlane), HandSample(angle, offPlane))
        repeat((seconds / dt).toInt()) {
            angle = ((angle + sign * degPerSecond * dt) % 360f + 360f) % 360f
            snap = update(dt, HandSample(angle, offPlane), HandSample(angle, offPlane))
        }
        return snap to angle
    }

    @Test
    fun startsWithTheTwoSecondSafetyNotice() {
        val exercise = ShoulderExercise()
        assertEquals(ShoulderPhase.NOTICE, exercise.circle(1.5f, 90f, 1).first.phase)
        assertEquals(ShoulderPhase.BACKWARD, exercise.circle(0.7f, 90f, 1).first.phase)
    }

    @Test
    fun oneFullCircleOnTheRingCountsOneLap() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        assertEquals(1, exercise.circle(4.1f, 90f, 1).first.laps)
    }

    @Test
    fun aSteadyOffsetFromTheEstimatedRingStillCounts() {
        // The ring is placed from a guess of where the shoulders are; sitting 20 cm nearer must not fail laps.
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        assertEquals(2, exercise.circle(8.2f, 90f, 1, offPlane = 0.2f).first.laps)
    }

    @Test
    fun wavingInAndOutOfThePlaneDoesNotCountAndSaysWhy() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        var angle = 0f
        var snap = exercise.update(0f, HandSample(0f, 0f), HandSample(0f, 0f))
        var i = 0
        repeat((4.2f / dt).toInt()) {
            angle = (angle + 90f * dt) % 360f
            val off = if ((i++ / 9) % 2 == 0) 0.25f else -0.25f
            snap = exercise.update(dt, HandSample(angle, off), HandSample(angle, off))
        }
        assertEquals(0, snap.laps)
        assertEquals("贴着环画，画得和环一样大", snap.hint)
    }

    @Test
    fun aHandThatGoesOutOfSightForHalfTheCircleIsCreditedWhenItComesBack() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        var angle = 0f
        var snap = exercise.update(0f, HandSample(0f, 0f), HandSample(0f, 0f))
        repeat((12.3f / dt).toInt()) {
            angle = (angle + 90f * dt) % 360f
            // Hidden between 120° and 300° of every circle: two seconds, longer than the 1.5 s grace.
            val visible = angle < 120f || angle > 300f
            val sample = if (visible) HandSample(angle, 0.01f) else null
            snap = exercise.update(dt, sample, sample)
        }
        assertEquals(3, snap.laps)
    }

    @Test
    fun thePanelShowsTheSlowerHand() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        var left = 0f
        var right = 0f
        var snap = exercise.update(0f, HandSample(0f, 0f), HandSample(0f, 0f))
        repeat((8.2f / dt).toInt()) {
            left = (left + 90f * dt) % 360f
            right = (right + 45f * dt) % 360f
            snap = exercise.update(dt, HandSample(left, 0f), HandSample(right, 0f))
        }
        assertEquals(2, snap.left.laps)
        assertEquals(1, snap.right.laps)
        assertEquals(1, snap.laps)
    }

    @Test
    fun eightLapsBackThenFiveSecondsRestThenForward() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        val (afterBack, angle) = exercise.circle(32.5f, 90f, 1)
        assertEquals(ShoulderPhase.REST, afterBack.phase)
        assertEquals(8, afterBack.lapsBack)
        assertEquals(ShoulderPhase.FORWARD, exercise.circle(5.2f, 0f, 1, start = angle).first.phase)
        val done = exercise.circle(32.5f, 90f, -1, start = angle).first
        assertTrue(done.finished)
        assertEquals(8, done.lapsForward)
    }

    @Test
    fun circlingTheWrongWayCountsNothing() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        assertEquals(0, exercise.circle(8.2f, 90f, -1).first.laps)
    }

    @Test
    fun aShortDropoutIsBridgedAndALongOneAsksToRaiseTheHands() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        var snap = exercise.update(dt, null, null)
        repeat(72) { snap = exercise.update(dt, null, null) }
        assertEquals(null, snap.hint)
        assertTrue(snap.left.progressDeg > 60f)
        repeat(72) { snap = exercise.update(dt, null, null) }
        assertNotNull(snap.hint)
        assertTrue(snap.left.lostLong)
    }

    @Test
    fun thePacerWaitsWhenTheHandsFallFarBehind() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        val snap = exercise.circle(6f, 0f, 1).first
        assertTrue(snap.pacerDeg <= 90f + 32f + 2f)
    }

    @Test
    fun endsAfterNinetySecondsEvenIfLapsAreMissing() {
        val exercise = ShoulderExercise()
        exercise.circle(2.1f, 0f, 1)
        assertTrue(exercise.circle(90.5f, 10f, 1).first.finished)
    }
}
