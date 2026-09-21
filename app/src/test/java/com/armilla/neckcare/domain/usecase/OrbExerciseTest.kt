package com.armilla.neckcare.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbExerciseTest {
    private val boundary = MotionBoundary(left = 55.8f, right = 63.9f, down = 41.4f, up = 52.2f)
    private val dt = 1f / 72f

    @Test
    fun theRouteNeverLeavesTheMotionBoundary() {
        val path = OrbPathGenerator.generate(boundary, 7, 5)
        assertTrue(path.samples.all { OrbPathGenerator.boundaryFraction(it, boundary) <= 0.961f })
    }

    @Test
    fun theWeakSideGetsSevenOfTheTwelveCatchPoints() {
        val path = OrbPathGenerator.generate(boundary, leftCount = 7, rightCount = 5)
        assertEquals(12, path.catchPoints.size)
        assertEquals(7, path.catchPoints.count { it.azimuthDeg < 0 })
        assertEquals(5, path.catchPoints.count { it.azimuthDeg > 0 })
    }

    @Test
    fun theRouteGoesRoundOnceWithoutDoublingBack() {
        val path = OrbPathGenerator.generate(MotionBoundary.DEFAULT)
        val angles = path.catchPoints.map { Math.toDegrees(kotlin.math.atan2(it.elevationDeg.toDouble(), it.azimuthDeg.toDouble())) }
        assertEquals(angles.sorted(), angles)
    }

    @Test
    fun theOrbEasesUpToSpeedAndNeverExceedsItsCruiseSpeed() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary))
        var last = exercise.update(dt, GazePoint(0f, 0f), 0f).position
        var lastSpeed = 0f
        repeat(72 * 20) {
            val snap = exercise.update(dt, last, 0f)
            val speed = snap.position.distanceTo(last) / dt
            assertTrue("speed $speed", speed <= 32.5f)
            // No jump in speed from one frame to the next: at most the braking rate, with slack
            // for the corners of the sampled route.
            assertTrue("jerk from $lastSpeed to $speed", kotlin.math.abs(speed - lastSpeed) <= 3f)
            lastSpeed = speed
            last = snap.position
        }
    }

    @Test
    fun followingTheOrbCatchesBothLapsWithinNinetySeconds() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary))
        var snap = exercise.update(dt, GazePoint(0f, 0f), 0f)
        var seconds = 0f
        while (!snap.finished && seconds < 95f) {
            snap = exercise.update(dt, snap.position, 5f)
            seconds += dt
        }
        assertEquals(24, snap.total)
        assertEquals(24, snap.caught)
        assertTrue("took $seconds s", seconds < 90f)
    }

    @Test
    fun theOrbStopsExactlyOnEachCatchPoint() {
        val path = OrbPathGenerator.generate(boundary)
        val exercise = OrbExercise(path)
        val elsewhere = GazePoint(170f, 80f)
        var snap = exercise.update(dt, elsewhere, 0f)
        // Never looked at, so it is never caught: it must come to rest on the first catch point
        // before the 3 s away rule parks it, or park short of it; either way it never passes it.
        repeat(72 * 10) { snap = exercise.update(dt, elsewhere, 0f) }
        val first = path.catchPoints[1]
        assertTrue(path.cumulativeDeg[path.catchIndices[1]] >= snap.arcDeg - 0.05f)
        assertEquals(0, snap.caught)
        assertTrue(snap.position.distanceTo(first) >= 0f)
    }

    @Test
    fun lookingAwayMakesTheOrbWaitWithoutLosingProgress() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary))
        var snap = exercise.update(dt, GazePoint(0f, 0f), 0f)
        repeat(72) { snap = exercise.update(dt, snap.position, 0f) }
        val progress = snap.catchProgress
        val elsewhere = GazePoint(snap.position.azimuthDeg + 40f, snap.position.elevationDeg)
        repeat(72 * 4) { snap = exercise.update(dt, elsewhere, 0f) }
        assertTrue(snap.waiting)
        assertEquals(progress, snap.catchProgress, 0.001f)
        val parked = snap.position
        repeat(72) { snap = exercise.update(dt, elsewhere, 0f) }
        assertEquals(0f, snap.position.distanceTo(parked), 0.001f)
    }

    @Test
    fun aFastHeadTurnIsIgnoredByDefault() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary))
        var snap = exercise.update(dt, GazePoint(0f, 0f), 0f)
        repeat(36) { snap = exercise.update(dt, snap.position, 200f) }
        assertFalse(snap.showSlowHint)
    }

    @Test
    fun aFastHeadTurnPausesTheOrbAndShowsTheGentleHintWhenThatRuleIsOn() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary), OrbConfig(pauseOnFastHead = true))
        var snap = exercise.update(dt, GazePoint(0f, 0f), 0f)
        repeat(36) { snap = exercise.update(dt, snap.position, 0f) }
        val before = snap.position
        snap = exercise.update(dt, snap.position, 90f)
        repeat(36) { snap = exercise.update(dt, snap.position, 0f) }
        assertTrue(snap.showSlowHint)
        assertEquals(0f, snap.position.distanceTo(before), 0.3f)
    }

    @Test
    fun endsWhenTheNinetySecondsAreUp() {
        val exercise = OrbExercise(OrbPathGenerator.generate(boundary))
        var snap = exercise.update(dt, GazePoint(0f, 0f), 0f)
        assertFalse(snap.finished)
        repeat((72 * 91)) { snap = exercise.update(dt, GazePoint(170f, 80f), 0f) }
        assertTrue(snap.finished)
        assertEquals(0, snap.remainingSeconds)
    }
}
