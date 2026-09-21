package com.armilla.neckcare.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunchExerciseTest {
    private val dt = 1f / 72f

    @Test
    fun targetsAlternateBetweenTheLeftAndRightLane() {
        val exercise = PunchExercise()
        val sides = LinkedHashMap<Int, PunchSide>()
        repeat((8f / dt).toInt()) { exercise.update(dt, null, null).targets.forEach { sides[it.id] = it.side } }
        assertEquals(listOf(PunchSide.LEFT, PunchSide.RIGHT, PunchSide.LEFT, PunchSide.RIGHT), sides.values.take(4))
    }

    @Test
    fun targetsFlyTowardTheUser() {
        val exercise = PunchExercise()
        var snap = exercise.update(dt, null, null)
        repeat(72) { snap = exercise.update(dt, null, null) }
        val first = snap.targets.first { it.id == 0 }.position.z
        repeat(72) { snap = exercise.update(dt, null, null) }
        assertTrue(snap.targets.first { it.id == 0 }.position.z > first)
    }

    @Test
    fun aFistHeldWhereTheTargetArrivesHitsIt() {
        val exercise = PunchExercise()
        val fist = Point3(-0.26f, PunchExercise.SHOULDER_HEIGHT_M, -0.5f)
        var snap = exercise.update(dt, fist, null)
        repeat((4f / dt).toInt()) { snap = exercise.update(dt, fist, null) }
        assertEquals(1, snap.hits)
    }

    @Test
    fun eitherHandMayHitEitherTarget() {
        val exercise = PunchExercise()
        val fist = Point3(-0.26f, PunchExercise.SHOULDER_HEIGHT_M, -0.5f)
        var snap = exercise.update(dt, null, fist)
        repeat((4f / dt).toInt()) { snap = exercise.update(dt, null, fist) }
        assertEquals(1, snap.hits)
    }

    @Test
    fun aDistantTargetCannotBeHitEarly() {
        val exercise = PunchExercise()
        val farAhead = Point3(-0.26f, PunchExercise.SHOULDER_HEIGHT_M, -3f)
        var snap = exercise.update(dt, farAhead, null)
        repeat((1.5f / dt).toInt()) { snap = exercise.update(dt, farAhead, null) }
        assertEquals(0, snap.hits)
    }

    @Test
    fun missedTargetsPassWithoutAnyPenalty() {
        val exercise = PunchExercise()
        var snap = exercise.update(dt, null, null)
        repeat((10f / dt).toInt()) { snap = exercise.update(dt, null, null) }
        assertEquals(0, snap.hits)
        assertTrue(snap.launched >= 6)
        assertTrue(snap.targets.none { it.state == TargetState.HIT })
    }

    @Test
    fun endsAfterSixtySecondsAndStopsLaunchingNearTheEnd() {
        val exercise = PunchExercise()
        var snap = exercise.update(dt, null, null)
        assertFalse(snap.finished)
        repeat((60.2f / dt).toInt()) { snap = exercise.update(dt, null, null) }
        assertTrue(snap.finished)
        assertTrue(snap.targets.none { it.state == TargetState.FLYING && it.position.z < -1f })
    }
}
