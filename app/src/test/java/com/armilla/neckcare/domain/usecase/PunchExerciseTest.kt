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
        assertEquals(0, snap.combo)
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

    @Test
    fun targetsArriveOnTheBeat() {
        val exercise = PunchExercise()
        var t = 0f
        val beatTimes = ArrayList<Float>()
        val arrivals = HashMap<Int, Float>()
        repeat((9f / dt).toInt()) {
            t += dt
            val snap = exercise.update(dt, null, null)
            if (snap.beat) beatTimes += t
            snap.targets.forEach { if (it.position.z >= -0.5f) arrivals.putIfAbsent(it.id, t) }
        }
        assertTrue(beatTimes.size >= 8)
        assertEquals(0.6f, beatTimes[1] - beatTimes[0], 0.03f)
        arrivals.values.forEach { arrival -> assertTrue(beatTimes.any { kotlin.math.abs(it - arrival) < 0.05f }) }
    }

    @Test
    fun theMinuteBuildsFromEverySecondBeatToEveryBeatToDoubles() {
        val exercise = PunchExercise()
        val launchTimes = LinkedHashMap<Int, Float>()
        var t = 0f
        repeat((58f / dt).toInt()) {
            t += dt
            exercise.update(dt, null, null).targets.forEach { launchTimes.putIfAbsent(it.id, t) }
        }
        val early = launchTimes.values.count { it < 12f }
        val middle = launchTimes.values.count { it in 12f..24f }
        assertTrue("warm-up $early, then $middle", middle > early * 1.6f)
        val doubles = launchTimes.values.groupBy { (it * 10).toInt() }.count { it.value.size == 2 }
        assertTrue(doubles >= 2)
    }

    @Test
    fun aRunOfHitsCountsUpAndAPassedTargetOnlyEndsTheRun() {
        val exercise = PunchExercise()
        val fist = Point3(-0.26f, PunchExercise.SHOULDER_HEIGHT_M, -0.5f)
        var snap = exercise.update(dt, fist, null)
        repeat((3f / dt).toInt()) { snap = exercise.update(dt, fist, null) }
        assertEquals(1, snap.combo)
        repeat((6f / dt).toInt()) { snap = exercise.update(dt, null, null) }
        assertEquals(0, snap.combo)
        assertEquals(1, snap.hits)
    }
}
