package com.armilla.neckcare.domain.usecase

import kotlin.math.sqrt

/** A point relative to the eyes, in metres: x right, y up, z toward the user (forward is -z). */
data class Point3(val x: Float, val y: Float, val z: Float) {
    fun distanceTo(o: Point3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class PunchConfig(
    val durationSeconds: Float = 60f,
    /** One target every 1.3 s: an unhurried rhythm, this is mobility work and not a workout. */
    val intervalSeconds: Float = 1.3f,
    val travelSeconds: Float = 2.6f,
    val spawnDistanceM: Float = 7f,
    /** Where a comfortably extended fist meets the target. */
    val hitPlaneM: Float = 0.5f,
    val laneOffsetM: Float = 0.26f,
    val hitRadiusM: Float = 0.2f,
    /** A target can be hit from this far ahead of the hit plane until it has passed the body. */
    val reachAheadM: Float = 0.45f,
    val passedBehindM: Float = 0.25f,
)

enum class PunchSide {
    LEFT,
    RIGHT,
}

enum class TargetState {
    FLYING,
    HIT,
    PASSED,
}

data class PunchTarget(val id: Int, val side: PunchSide, val position: Point3, val state: TargetState, val stateAge: Float)

data class PunchSnapshot(
    val targets: List<PunchTarget>,
    val hits: Int,
    val launched: Int,
    val remainingSeconds: Int,
    val timeProgress: Float,
    val justHit: Boolean,
    val finished: Boolean,
)

/**
 * 出拳: targets fly toward the user along a left and a right lane, alternating sides and two
 * heights, and are hit by reaching a fist to them as they arrive. Either hand may hit either
 * target, no speed is demanded and a target that passes is simply gone: nothing is scored down,
 * in keeping with PRD §13 (no targets, no pushing for more).
 */
class PunchExercise(private val config: PunchConfig = PunchConfig()) {
    private class Live(val id: Int, val side: PunchSide, val height: Float, var age: Float = 0f, var state: TargetState = TargetState.FLYING, var stateAge: Float = 0f)

    private val live = ArrayList<Live>()
    private var elapsed = 0f
    private var sinceSpawn = 0f
    private var launched = 0
    private var hits = 0

    init {
        sinceSpawn = config.intervalSeconds - 1f
    }

    fun update(dtSeconds: Float, leftHand: Point3?, rightHand: Point3?): PunchSnapshot {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        elapsed += dt
        sinceSpawn += dt
        // Stop launching when a new target could no longer arrive before the time is up.
        if (sinceSpawn >= config.intervalSeconds && elapsed + config.travelSeconds <= config.durationSeconds) {
            sinceSpawn = 0f
            val side = if (launched % 2 == 0) PunchSide.LEFT else PunchSide.RIGHT
            val height = if ((launched / 2) % 2 == 0) SHOULDER_HEIGHT_M else CHEST_HEIGHT_M
            live += Live(launched, side, height)
            launched++
        }
        var justHit = false
        for (target in live) {
            if (target.state == TargetState.FLYING) {
                target.age += dt
                val p = position(target)
                val inReach = p.z >= -(config.hitPlaneM + config.reachAheadM) && p.z <= config.passedBehindM
                val touched = listOfNotNull(leftHand, rightHand).any { it.distanceTo(p) <= config.hitRadiusM }
                when {
                    inReach && touched -> { target.state = TargetState.HIT; target.stateAge = 0f; hits++; justHit = true }
                    p.z > config.passedBehindM -> { target.state = TargetState.PASSED; target.stateAge = 0f }
                }
            } else target.stateAge += dt
        }
        live.removeAll { it.state != TargetState.FLYING && it.stateAge > FADE_SECONDS }
        val finished = elapsed >= config.durationSeconds
        return PunchSnapshot(
            targets = live.map { PunchTarget(it.id, it.side, position(it), it.state, it.stateAge) },
            hits = hits,
            launched = launched,
            remainingSeconds = kotlin.math.ceil((config.durationSeconds - elapsed).coerceAtLeast(0f)).toInt(),
            timeProgress = (elapsed / config.durationSeconds).coerceIn(0f, 1f),
            justHit = justHit,
            finished = finished,
        )
    }

    private fun position(target: Live): Point3 {
        val speed = (config.spawnDistanceM - config.hitPlaneM) / config.travelSeconds
        val x = if (target.side == PunchSide.LEFT) -config.laneOffsetM else config.laneOffsetM
        return Point3(x, target.height, -config.spawnDistanceM + speed * target.age)
    }

    companion object {
        const val SHOULDER_HEIGHT_M = -0.18f
        const val CHEST_HEIGHT_M = -0.32f
        const val FADE_SECONDS = 0.35f
    }
}
