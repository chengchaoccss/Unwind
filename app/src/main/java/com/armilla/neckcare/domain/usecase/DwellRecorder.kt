package com.armilla.neckcare.domain.usecase

import com.armilla.neckcare.domain.model.Direction
import kotlin.math.abs
import kotlin.math.roundToInt

/** Thresholds of PRD §11 "记录条件". All are starting values to be tuned on device. */
data class RecorderConfig(
    val dwellSpeedDps: Float = 5f,
    val dwellSeconds: Float = 2f,
    val minValidAngleDeg: Float = 10f,
    val fastSpeedDps: Float = 30f,
    val tooFastSpeedDps: Float = 60f,
    val neutralBandDeg: Float = 5f,
    val smoothingSeconds: Float = 0.1f,
    val skipHintSeconds: Float = 20f,
    val recordedHoldSeconds: Float = 0.6f,
)

enum class SpeedTier(val label: String) {
    OK("合适"),
    FAST("慢一点"),
    TOO_FAST("太快了"),
}

enum class RecorderPhase {
    /** Moving toward the end of range, waiting for a steady dwell. */
    MEASURING,

    /** A reading was just taken; the reticle shows its "recorded" state. */
    RECORDED,

    /** Reading taken, waiting for the head to come back inside the neutral band. */
    RETURNING,

    /** Reading voided (too fast or implausible); must return to neutral, then measure again. */
    VOIDED,

    /** Back at neutral with a reading: the caller moves on to the next direction. */
    DONE,
}

data class RecorderSnapshot(
    val phase: RecorderPhase,
    /** Angle toward the measured direction, smoothed and rounded, never negative. */
    val displayAngleDeg: Int,
    /** Unrounded version of [displayAngleDeg] for drawing the swept band. */
    val sweepAngleDeg: Float,
    val speedDps: Float,
    val speedTier: SpeedTier,
    /** Number of lit speed bars, 1..5. */
    val speedBars: Int,
    /** 0..1 progress of the dwell arc on the reticle. */
    val dwellProgress: Float,
    val recordedAngleDeg: Int?,
    val showSkipHint: Boolean,
    val retries: Int,
    val peakSpeedDps: Float,
    val dwellMs: Long,
)

/**
 * Measures one direction. Feed it the signed angle of the active axis every frame; it records one
 * reading only while the head is slow, steady for the dwell time and beyond the minimum angle.
 */
class DwellRecorder(
    private val direction: Direction,
    private val config: RecorderConfig = RecorderConfig(),
) {
    private var phase = RecorderPhase.MEASURING
    private var smoothedAngle = 0f
    private var smoothedSpeed = 0f
    private var lastAngle: Float? = null
    private var dwellTime = 0f
    private val dwellSamples = ArrayList<Float>()
    private var elapsedWithoutRecord = 0f
    private var recordedHold = 0f
    private var recorded: Int? = null
    private var retries = 0
    private var peakSpeed = 0f
    private var recordedDwellMs = 0L

    fun update(dtSeconds: Float, signedAxisAngleDeg: Float): RecorderSnapshot {
        val dt = dtSeconds.coerceIn(1e-4f, 0.1f)
        val toward = signedAxisAngleDeg * direction.sign
        val alpha = (dt / config.smoothingSeconds).coerceIn(0f, 1f)

        val rawSpeed = lastAngle?.let { abs(toward - it) / dt } ?: 0f
        lastAngle = toward
        smoothedSpeed += (rawSpeed - smoothedSpeed) * alpha
        smoothedAngle += (toward - smoothedAngle) * alpha

        when (phase) {
            RecorderPhase.MEASURING -> measure(dt, toward)
            RecorderPhase.RECORDED -> {
                recordedHold += dt
                if (recordedHold >= config.recordedHoldSeconds) phase = RecorderPhase.RETURNING
            }
            RecorderPhase.RETURNING -> if (abs(toward) <= config.neutralBandDeg) phase = RecorderPhase.DONE
            RecorderPhase.VOIDED ->
                if (abs(toward) <= config.neutralBandDeg) {
                    phase = RecorderPhase.MEASURING
                    resetDwell()
                }
            RecorderPhase.DONE -> Unit
        }
        return snapshot()
    }

    private fun measure(dt: Float, toward: Float) {
        elapsedWithoutRecord += dt
        peakSpeed = maxOf(peakSpeed, smoothedSpeed)

        val implausible = toward > direction.plausibleMaxDeg
        if (smoothedSpeed > config.tooFastSpeedDps || implausible) {
            // Only void once the user has actually left neutral, so a twitch at rest is ignored.
            if (toward > config.neutralBandDeg) {
                retries++
                phase = RecorderPhase.VOIDED
            }
            resetDwell()
            return
        }

        val steady = smoothedSpeed < config.dwellSpeedDps && toward >= config.minValidAngleDeg
        if (!steady) {
            resetDwell()
            return
        }
        dwellTime += dt
        dwellSamples.add(toward)
        if (dwellTime >= config.dwellSeconds) {
            recorded = median(dwellSamples).roundToInt()
            recordedDwellMs = (dwellTime * 1000).toLong()
            recordedHold = 0f
            phase = RecorderPhase.RECORDED
        }
    }

    private fun resetDwell() {
        dwellTime = 0f
        dwellSamples.clear()
    }

    private fun snapshot(): RecorderSnapshot {
        val tier =
            when {
                smoothedSpeed > config.tooFastSpeedDps || phase == RecorderPhase.VOIDED -> SpeedTier.TOO_FAST
                smoothedSpeed > config.fastSpeedDps -> SpeedTier.FAST
                else -> SpeedTier.OK
            }
        val bars =
            when {
                tier == SpeedTier.TOO_FAST -> 5
                smoothedSpeed > 45f -> 4
                tier == SpeedTier.FAST -> 3
                smoothedSpeed > 15f -> 2
                else -> 1
            }
        val sweep = smoothedAngle.coerceAtLeast(0f)
        return RecorderSnapshot(
            phase = phase,
            displayAngleDeg = recorded?.takeIf { phase == RecorderPhase.RECORDED } ?: sweep.roundToInt(),
            sweepAngleDeg = sweep,
            speedDps = smoothedSpeed,
            speedTier = tier,
            speedBars = bars,
            dwellProgress =
                if (phase == RecorderPhase.MEASURING) (dwellTime / config.dwellSeconds).coerceIn(0f, 1f)
                else if (phase == RecorderPhase.RECORDED) 1f else 0f,
            recordedAngleDeg = recorded,
            showSkipHint =
                phase == RecorderPhase.MEASURING && elapsedWithoutRecord >= config.skipHintSeconds,
            retries = retries,
            peakSpeedDps = peakSpeed,
            dwellMs = recordedDwellMs,
        )
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
}
