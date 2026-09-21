package com.armilla.neckcare.domain.usecase

import kotlin.math.abs

data class ShoulderConfig(
    val lapsPerDirection: Int = 8,
    val durationSeconds: Float = 90f,
    val pacerDegPerSecond: Float = 90f,
    val pacerLeadDeg: Float = 32f,
    val maxLagDeg: Float = 90f,
    val onPlaneToleranceM: Float = 0.08f,
    val onPlaneShare: Float = 0.7f,
    val safetyNoticeSeconds: Float = 2f,
    val restSeconds: Float = 5f,
    val lostGraceSeconds: Float = 1.5f,
)

enum class ShoulderPhase(val title: String) {
    NOTICE("确认两臂范围内没有东西"),
    BACKWARD("向后画圈"),
    REST("放松一下"),
    FORWARD("向前画圈"),
    DONE(""),
}

/** One hand on its guide ring: angle around the ring (degrees) and distance from the ring plane. */
data class HandSample(val ringAngleDeg: Float, val offPlaneM: Float)

data class HandState(val progressDeg: Float, val laps: Int, val tracked: Boolean, val lostLong: Boolean)

data class ShoulderSnapshot(
    val phase: ShoulderPhase,
    /** The smaller of the two hands' lap counts in the current direction. */
    val laps: Int,
    val left: HandState,
    val right: HandState,
    val pacerDeg: Float,
    /** +1 while circling backward, -1 forward: the direction the arcs grow. */
    val direction: Int,
    val lapJustCounted: Boolean,
    val remainingSeconds: Int,
    val lapsBack: Int,
    val lapsForward: Int,
    val hint: String?,
    val finished: Boolean,
)

/**
 * PRD §8: a lap counts when the hand has gone 360° around its ring with at least 70 % of that
 * time within 8 cm of the ring plane; hands count separately and the panel shows the smaller
 * count; the pacer runs 90°/s and waits when a hand is more than 90° behind; a hand lost for up
 * to 1.5 s is carried along with the pacer; eight laps backward, five seconds of rest, eight
 * forward, or 90 seconds in all.
 */
class ShoulderExercise(private val config: ShoulderConfig = ShoulderConfig()) {
    private class Hand {
        var progress = 0f
        var laps = 0
        var lastAngle: Float? = null
        var onPlane = 0f
        var total = 0f
        var lost = 0f
    }

    private var phase = ShoulderPhase.NOTICE
    private var phaseTime = 0f
    private var elapsed = 0f
    private var pacer = 0f
    private val hands = arrayOf(Hand(), Hand())
    private var lapsBack = 0
    private var lapsForward = 0

    fun update(dtSeconds: Float, left: HandSample?, right: HandSample?): ShoulderSnapshot {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        phaseTime += dt
        var counted = false
        when (phase) {
            ShoulderPhase.NOTICE -> if (phaseTime >= config.safetyNoticeSeconds) enter(ShoulderPhase.BACKWARD)
            ShoulderPhase.REST -> if (phaseTime >= config.restSeconds) enter(ShoulderPhase.FORWARD)
            ShoulderPhase.BACKWARD, ShoulderPhase.FORWARD -> {
                elapsed += dt
                val sign = if (phase == ShoulderPhase.BACKWARD) 1 else -1
                val slowest = hands.minOf { it.progress + it.laps * 360f }
                if (pacer - slowest < config.maxLagDeg + config.pacerLeadDeg) pacer += config.pacerDegPerSecond * dt
                listOf(left, right).forEachIndexed { i, sample -> if (track(hands[i], sample, sign, dt)) counted = true }
                val laps = hands.minOf { it.laps }
                if (phase == ShoulderPhase.BACKWARD) lapsBack = laps else lapsForward = laps
                when {
                    elapsed >= config.durationSeconds -> enter(ShoulderPhase.DONE)
                    laps >= config.lapsPerDirection && phase == ShoulderPhase.BACKWARD -> enter(ShoulderPhase.REST)
                    laps >= config.lapsPerDirection -> enter(ShoulderPhase.DONE)
                }
            }
            ShoulderPhase.DONE -> Unit
        }
        val lostLong = hands.any { it.lost > config.lostGraceSeconds }
        return ShoulderSnapshot(
            phase = phase,
            laps = hands.minOf { it.laps },
            left = state(hands[0]),
            right = state(hands[1]),
            pacerDeg = pacer % 360f,
            direction = if (phase == ShoulderPhase.FORWARD) -1 else 1,
            lapJustCounted = counted,
            remainingSeconds = kotlin.math.ceil((config.durationSeconds - elapsed).coerceAtLeast(0f)).toInt(),
            lapsBack = lapsBack,
            lapsForward = lapsForward,
            hint = if (lostLong && (phase == ShoulderPhase.BACKWARD || phase == ShoulderPhase.FORWARD)) "把手抬到看得见的位置" else null,
            finished = phase == ShoulderPhase.DONE,
        )
    }

    private fun track(hand: Hand, sample: HandSample?, sign: Int, dt: Float): Boolean {
        if (sample == null) {
            hand.lost += dt
            hand.lastAngle = null
            // Short dropouts behind the body: carry the hand along with the pacer.
            if (hand.lost <= config.lostGraceSeconds) advance(hand, config.pacerDegPerSecond * dt, onPlane = true, dt)
        } else {
            hand.lost = 0f
            val last = hand.lastAngle
            hand.lastAngle = sample.ringAngleDeg
            if (last != null) {
                var delta = sample.ringAngleDeg - last
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                // Only movement in the asked direction counts; going the other way does not undo it.
                val forward = (delta * sign).coerceIn(0f, 45f)
                advance(hand, forward, abs(sample.offPlaneM) <= config.onPlaneToleranceM, dt)
            }
        }
        if (hand.progress >= 360f) {
            val good = hand.total > 0f && hand.onPlane / hand.total >= config.onPlaneShare
            hand.progress -= 360f
            hand.onPlane = 0f
            hand.total = 0f
            if (good) {
                hand.laps++
                return true
            }
        }
        return false
    }

    private fun advance(hand: Hand, degrees: Float, onPlane: Boolean, dt: Float) {
        hand.progress += degrees
        if (degrees > 0f) {
            hand.total += dt
            if (onPlane) hand.onPlane += dt
        }
    }

    private fun state(hand: Hand) =
        HandState(hand.progress.coerceIn(0f, 360f), hand.laps, hand.lost == 0f, hand.lost > config.lostGraceSeconds)

    private fun enter(next: ShoulderPhase) {
        phase = next
        phaseTime = 0f
        if (next == ShoulderPhase.FORWARD || next == ShoulderPhase.BACKWARD) {
            pacer = 0f
            hands.forEach { it.progress = 0f; it.laps = 0; it.lastAngle = null; it.onPlane = 0f; it.total = 0f }
        }
    }
}
