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

/** Why a finished circle did or did not count; logged on device to tune the thresholds. */
data class LapReport(
    val hand: Int,
    val counted: Boolean,
    val onPlaneShare: Float,
    val medianOffPlaneM: Float,
    val carriedDeg: Float,
)

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
    val lapReports: List<LapReport> = emptyList(),
)

/**
 * PRD §8: a lap counts when the hand has gone 360° around its ring with at least 70 % of that
 * time within 8 cm of the ring plane. The ring is placed from an estimate of where the shoulders
 * are, so "the ring plane" is taken as the plane the hand itself circled in (the lap's median
 * offset): a user sitting a little nearer or farther is not failed for the app's estimate; hands count separately and the panel shows the smaller
 * count; the pacer runs 90°/s and waits when a hand is more than 90° behind; a hand lost for up
 * to 1.5 s is carried along with the pacer; eight laps backward, five seconds of rest, eight
 * forward, or 90 seconds in all.
 */
class ShoulderExercise(private val config: ShoulderConfig = ShoulderConfig()) {
    private class Hand {
        var progress = 0f
        var laps = 0
        var lastAngle: Float? = null
        var lost = 0f

        /** Where the hand is assumed to be while it is out of sight. */
        var virtualAngle: Float? = null
        var carried = 0f
        val offPlane = ArrayList<Float>()
    }

    private var phase = ShoulderPhase.NOTICE
    private var phaseTime = 0f
    private var elapsed = 0f
    private var pacer = 0f
    private val hands = arrayOf(Hand(), Hand())
    private var lapsBack = 0
    private var lapsForward = 0
    private var rejectedHint = 0f
    private val reports = ArrayList<LapReport>()

    fun update(dtSeconds: Float, left: HandSample?, right: HandSample?): ShoulderSnapshot {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        phaseTime += dt
        var counted = false
        reports.clear()
        rejectedHint = (rejectedHint - dt).coerceAtLeast(0f)
        when (phase) {
            ShoulderPhase.NOTICE -> if (phaseTime >= config.safetyNoticeSeconds) enter(ShoulderPhase.BACKWARD)
            ShoulderPhase.REST -> if (phaseTime >= config.restSeconds) enter(ShoulderPhase.FORWARD)
            ShoulderPhase.BACKWARD, ShoulderPhase.FORWARD -> {
                elapsed += dt
                val sign = if (phase == ShoulderPhase.BACKWARD) 1 else -1
                val slowest = hands.minOf { it.progress + it.laps * 360f }
                if (pacer - slowest < config.maxLagDeg + config.pacerLeadDeg) pacer += config.pacerDegPerSecond * dt
                listOf(left, right).forEachIndexed { i, sample -> if (track(i, hands[i], sample, sign, dt)) counted = true }
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
            hint =
                when {
                    phase != ShoulderPhase.BACKWARD && phase != ShoulderPhase.FORWARD -> null
                    lostLong -> "把手抬到看得见的位置"
                    rejectedHint > 0f -> "贴着环画，画得和环一样大"
                    else -> null
                },
            finished = phase == ShoulderPhase.DONE,
            lapReports = reports.toList(),
        )
    }

    private fun track(index: Int, hand: Hand, sample: HandSample?, sign: Int, dt: Float): Boolean {
        if (sample == null) {
            hand.lost += dt
            if (hand.virtualAngle == null) hand.virtualAngle = hand.lastAngle
            hand.lastAngle = null
            // Short dropouts behind the body: carry the hand along with the pacer.
            if (hand.lost <= config.lostGraceSeconds) {
                val step = config.pacerDegPerSecond * dt
                hand.progress += step
                hand.carried += step
                hand.virtualAngle = hand.virtualAngle?.let { it + sign * step }
            }
        } else {
            val last = hand.lastAngle
            val virtual = hand.virtualAngle
            if (last != null) {
                var delta = sample.ringAngleDeg - last
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                // Only movement in the asked direction counts; going the other way does not undo it.
                hand.progress += (delta * sign).coerceIn(0f, 45f)
            } else if (virtual != null) {
                // Back in sight: credit the arc travelled while hidden, measured the asked way round.
                val travelled = (((sample.ringAngleDeg - virtual) * sign) % 360f + 360f) % 360f
                if (travelled <= MAX_HIDDEN_ARC_DEG) {
                    hand.progress += travelled
                    hand.carried += travelled
                }
            }
            hand.lost = 0f
            hand.virtualAngle = null
            hand.lastAngle = sample.ringAngleDeg
            hand.offPlane += sample.offPlaneM
        }
        if (hand.progress < 360f) return false

        val sorted = hand.offPlane.sorted()
        val median = if (sorted.isEmpty()) 0f else sorted[sorted.size / 2]
        val share =
            if (sorted.isEmpty()) 0f
            else sorted.count { abs(it - median) <= config.onPlaneToleranceM } / sorted.size.toFloat()
        // A lap seen for less than a third of the way round is not evidence of a circle.
        val seenEnough = hand.carried <= 360f * MAX_CARRIED_SHARE
        val good = share >= config.onPlaneShare && seenEnough
        reports += LapReport(index, good, share, median, hand.carried)
        hand.progress -= 360f
        hand.carried = 0f
        hand.offPlane.clear()
        if (good) hand.laps++ else rejectedHint = REJECTED_HINT_SECONDS
        return good
    }

    private fun state(hand: Hand) =
        HandState(hand.progress.coerceIn(0f, 360f), hand.laps, hand.lost == 0f, hand.lost > config.lostGraceSeconds)

    private fun enter(next: ShoulderPhase) {
        phase = next
        phaseTime = 0f
        if (next == ShoulderPhase.FORWARD || next == ShoulderPhase.BACKWARD) {
            pacer = 0f
            hands.forEach {
                it.progress = 0f
                it.laps = 0
                it.lastAngle = null
                it.virtualAngle = null
                it.carried = 0f
                it.offPlane.clear()
            }
        }
    }

    private companion object {
        /** A hand that reappears more than this far round is treated as a fresh start. */
        const val MAX_HIDDEN_ARC_DEG = 270f
        const val MAX_CARRIED_SHARE = 0.67f
        const val REJECTED_HINT_SECONDS = 3f
    }
}
