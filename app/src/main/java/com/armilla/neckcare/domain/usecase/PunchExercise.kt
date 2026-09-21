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
    /** Targets land on the beat. 100 beats per minute: lively, still easy to follow. */
    val beatsPerMinute: Float = 100f,
    /** Four beats of flight, so a target leaves and arrives on a beat. */
    val travelBeats: Int = 4,
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
    /** True on the frame a beat lands; [beatStrong] marks the first beat of a bar. */
    val beat: Boolean,
    val beatStrong: Boolean,
    /** 0..1 since the last beat, for visuals that pulse in time. */
    val beatPhase: Float,
    /** Hits in a row; a target that passes just ends the run, nothing is taken away. */
    val combo: Int,
    val hits: Int,
    val launched: Int,
    val remainingSeconds: Int,
    val timeProgress: Float,
    val justHit: Boolean,
    val finished: Boolean,
)

/**
 * 出拳: targets fly toward the user along a left and a right lane and are hit by reaching a fist to
 * them as they arrive, on the beat. The minute builds in three parts: a target every second beat,
 * then one on every beat, then every beat with both lanes at once on the last beat of each second
 * bar. Either hand may hit either target, no speed is demanded and a target that passes is simply
 * gone: nothing is scored down, in keeping with PRD §13.
 */
class PunchExercise(private val config: PunchConfig = PunchConfig()) {
    private class Live(val id: Int, val side: PunchSide, val height: Float, var age: Float = 0f, var state: TargetState = TargetState.FLYING, var stateAge: Float = 0f)

    private val beatSeconds = 60f / config.beatsPerMinute
    private val travelSeconds = beatSeconds * config.travelBeats
    private val live = ArrayList<Live>()
    private var elapsed = 0f
    private var launchBeat = 0
    private var soundedBeat = -1
    private var launched = 0
    private var singles = 0
    private var hits = 0
    private var combo = 0

    fun update(dtSeconds: Float, leftHand: Point3?, rightHand: Point3?): PunchSnapshot {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        elapsed += dt
        // Launch on beats; stop when a new target could no longer arrive before the time is up.
        while (launchBeat * beatSeconds <= elapsed) {
            if (launchBeat * beatSeconds + travelSeconds <= config.durationSeconds) launchFor(launchBeat)
            launchBeat++
        }
        var justHit = false
        for (target in live) {
            if (target.state == TargetState.FLYING) {
                target.age += dt
                val p = position(target)
                val inReach = p.z >= -(config.hitPlaneM + config.reachAheadM) && p.z <= config.passedBehindM
                val touched = listOfNotNull(leftHand, rightHand).any { it.distanceTo(p) <= config.hitRadiusM }
                when {
                    inReach && touched -> { target.state = TargetState.HIT; target.stateAge = 0f; hits++; combo++; justHit = true }
                    p.z > config.passedBehindM -> { target.state = TargetState.PASSED; target.stateAge = 0f; combo = 0 }
                }
            } else target.stateAge += dt
        }
        live.removeAll { it.state != TargetState.FLYING && it.stateAge > FADE_SECONDS }
        // Beats are heard once targets start arriving, so the drum and the hits coincide.
        val arrivalBeat = kotlin.math.floor(((elapsed - travelSeconds) / beatSeconds).toDouble()).toInt()
        val beatNow = arrivalBeat >= 0 && arrivalBeat != soundedBeat && elapsed < config.durationSeconds
        if (beatNow) soundedBeat = arrivalBeat
        val phase = (((elapsed - travelSeconds) / beatSeconds) % 1f + 1f) % 1f
        return PunchSnapshot(
            targets = live.map { PunchTarget(it.id, it.side, position(it), it.state, it.stateAge) },
            beat = beatNow,
            beatStrong = beatNow && arrivalBeat % 4 == 0,
            beatPhase = phase,
            combo = combo,
            hits = hits,
            launched = launched,
            remainingSeconds = kotlin.math.ceil((config.durationSeconds - elapsed).coerceAtLeast(0f)).toInt(),
            timeProgress = (elapsed / config.durationSeconds).coerceIn(0f, 1f),
            justHit = justHit,
            finished = elapsed >= config.durationSeconds,
        )
    }

    private fun launchFor(beat: Int) {
        val time = beat * beatSeconds
        val everyBeat = time >= WARM_UP_SECONDS
        if (!everyBeat && beat % 2 != 0) return
        val both = time >= FINALE_SECONDS && beat % 8 == 7
        val height = if ((singles / 2) % 2 == 0) SHOULDER_HEIGHT_M else CHEST_HEIGHT_M
        if (both) {
            live += Live(launched++, PunchSide.LEFT, SHOULDER_HEIGHT_M)
            live += Live(launched++, PunchSide.RIGHT, SHOULDER_HEIGHT_M)
        } else {
            live += Live(launched++, if (singles % 2 == 0) PunchSide.LEFT else PunchSide.RIGHT, height)
            singles++
        }
    }

    private fun position(target: Live): Point3 {
        val speed = (config.spawnDistanceM - config.hitPlaneM) / travelSeconds
        val x = if (target.side == PunchSide.LEFT) -config.laneOffsetM else config.laneOffsetM
        return Point3(x, target.height, -config.spawnDistanceM + speed * target.age)
    }

    companion object {
        const val SHOULDER_HEIGHT_M = -0.18f
        const val CHEST_HEIGHT_M = -0.32f
        const val FADE_SECONDS = 0.35f
        const val WARM_UP_SECONDS = 12f
        const val FINALE_SECONDS = 40f
    }
}
