package com.armilla.neckcare.domain.usecase

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A gaze direction: degrees to the right of straight ahead and degrees above the line of sight. */
data class GazePoint(val azimuthDeg: Float, val elevationDeg: Float) {
    fun distanceTo(o: GazePoint) = hypot(azimuthDeg - o.azimuthDeg, elevationDeg - o.elevationDeg)
}

/** The closed route of the orb and the catch points on it (indices into [samples]). */
data class OrbPath(val samples: List<GazePoint>, val catchIndices: List<Int>, val boundary: MotionBoundary) {
    val catchPoints: List<GazePoint>
        get() = catchIndices.map { samples[it] }
}

/**
 * PRD §11 "光球路径生成": catch points inside the ellipse of the personal motion boundary in the
 * rotation × flexion plane, visited in order of their angle around the centre so the route never
 * doubles back, and joined by a smooth closed spline that is pulled back inside the boundary.
 */
object OrbPathGenerator {
    const val TOTAL = 12

    fun generate(boundary: MotionBoundary, leftCount: Int = 6, rightCount: Int = 6): OrbPath {
        require(leftCount + rightCount == TOTAL) { "catch points must add up to $TOTAL" }
        val points = ArrayList<GazePoint>()
        // Left points fan over the left half-plane (90° to 270°), right points over the right.
        points += fan(leftCount, 100.0, 260.0, boundary)
        points += fan(rightCount, -80.0, 80.0, boundary)
        val ordered = points.sortedBy { atan2(it.elevationDeg.toDouble(), it.azimuthDeg.toDouble()) }

        val samples = ArrayList<GazePoint>()
        val catchIndices = ArrayList<Int>()
        val n = ordered.size
        for (i in 0 until n) {
            catchIndices += samples.size
            val p0 = ordered[(i - 1 + n) % n]
            val p1 = ordered[i]
            val p2 = ordered[(i + 1) % n]
            val p3 = ordered[(i + 2) % n]
            val steps = maxOf(4, (p1.distanceTo(p2) / SAMPLE_STEP_DEG).toInt())
            for (s in 0 until steps) samples += clamp(catmullRom(p0, p1, p2, p3, s / steps.toFloat()), boundary)
        }
        return OrbPath(samples, catchIndices, boundary)
    }

    /** [count] points spread over an arc of polar angles, alternating between two radii. */
    private fun fan(count: Int, fromDeg: Double, toDeg: Double, boundary: MotionBoundary): List<GazePoint> =
        (0 until count).map { i ->
            val angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 0.5) / count)
            val reach = if (i % 2 == 0) 0.78f else 0.5f
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            GazePoint(
                azimuthDeg = c * reach * (if (c < 0) boundary.left else boundary.right),
                elevationDeg = s * reach * (if (s < 0) boundary.down else boundary.up),
            )
        }

    private fun catmullRom(p0: GazePoint, p1: GazePoint, p2: GazePoint, p3: GazePoint, t: Float): GazePoint {
        fun f(a: Float, b: Float, c: Float, d: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            return 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
        }
        return GazePoint(
            f(p0.azimuthDeg, p1.azimuthDeg, p2.azimuthDeg, p3.azimuthDeg),
            f(p0.elevationDeg, p1.elevationDeg, p2.elevationDeg, p3.elevationDeg),
        )
    }

    /** 1 on the boundary ellipse, below 1 inside it. */
    fun boundaryFraction(p: GazePoint, boundary: MotionBoundary): Float {
        val a = if (p.azimuthDeg < 0) boundary.left else boundary.right
        val b = if (p.elevationDeg < 0) boundary.down else boundary.up
        return sqrt((p.azimuthDeg / a) * (p.azimuthDeg / a) + (p.elevationDeg / b) * (p.elevationDeg / b))
    }

    private fun clamp(p: GazePoint, boundary: MotionBoundary): GazePoint {
        val f = boundaryFraction(p, boundary)
        return if (f <= MAX_FRACTION) p else GazePoint(p.azimuthDeg * MAX_FRACTION / f, p.elevationDeg * MAX_FRACTION / f)
    }

    private const val SAMPLE_STEP_DEG = 1.5f
    private const val MAX_FRACTION = 0.96f
}

data class OrbConfig(
    val durationSeconds: Float = 90f,
    val maxSpeedDps: Float = 15f,
    val nearBoundarySpeedDps: Float = 8f,
    val nearBoundaryDeg: Float = 10f,
    val catchRadiusDeg: Float = 4.7f,
    val catchSeconds: Float = 1.5f,
    val waitAfterAwaySeconds: Float = 3f,
    val tooFastHeadDps: Float = 60f,
    val tooFastPauseSeconds: Float = 1f,
)

data class OrbSnapshot(
    val position: GazePoint,
    val caught: Int,
    val total: Int,
    val catchProgress: Float,
    val remainingSeconds: Int,
    val timeProgress: Float,
    val waiting: Boolean,
    val showSlowHint: Boolean,
    val justCaught: Boolean,
    val finished: Boolean,
    val sampleIndex: Int,
    val nextCatchOrdinal: Int,
)

/**
 * PRD §7 rules: the orb never exceeds 15°/s (8°/s near the boundary); gaze time inside the catch
 * ring accumulates and never runs backward; the orb waits at a catch point until it is caught, and
 * where it is when the gaze has been away for 3 s; a head turn above 60°/s pauses it for 1 s.
 * Nothing is scored down and nothing reports failure.
 */
class OrbExercise(private val path: OrbPath, private val config: OrbConfig = OrbConfig()) {
    private var index = 0f
    private var nextCatch = 1 % path.catchIndices.size
    private var caught = 0
    private var inRing = 0f
    private var away = 0f
    private var elapsed = 0f
    private var pause = 0f
    private var hint = 0f

    fun update(dtSeconds: Float, gaze: GazePoint, headSpeedDps: Float): OrbSnapshot {
        val dt = dtSeconds.coerceIn(0f, 0.1f)
        elapsed += dt
        val finishedBefore = isFinished()
        var justCaught = false
        if (!finishedBefore) {
            if (headSpeedDps > config.tooFastHeadDps) {
                pause = config.tooFastPauseSeconds
                hint = 3f
            }
            pause = (pause - dt).coerceAtLeast(0f)
            hint = (hint - dt).coerceAtLeast(0f)

            val here = position()
            val looking = gaze.distanceTo(here) <= config.catchRadiusDeg
            if (looking) { inRing += dt; away = 0f } else away += dt

            val target = path.catchIndices[nextCatch].toFloat()
            val atTarget = forwardDistance(index, target) < 0.5f
            if (atTarget && inRing >= config.catchSeconds) {
                caught++
                justCaught = true
                inRing = 0f
                nextCatch = (nextCatch + 1) % path.catchIndices.size
            } else if (!atTarget && pause <= 0f && away < config.waitAfterAwaySeconds) {
                advance(dt, target)
            }
        }
        val remaining = (config.durationSeconds - elapsed).coerceAtLeast(0f)
        return OrbSnapshot(
            position = position(),
            caught = caught,
            total = OrbPathGenerator.TOTAL,
            catchProgress = (inRing / config.catchSeconds).coerceIn(0f, 1f),
            remainingSeconds = kotlin.math.ceil(remaining).toInt(),
            timeProgress = (elapsed / config.durationSeconds).coerceIn(0f, 1f),
            waiting = away >= config.waitAfterAwaySeconds,
            showSlowHint = hint > 0f,
            justCaught = justCaught,
            finished = isFinished(),
            sampleIndex = index.toInt() % path.samples.size,
            nextCatchOrdinal = nextCatch,
        )
    }

    private fun isFinished() = elapsed >= config.durationSeconds || caught >= OrbPathGenerator.TOTAL

    private fun advance(dt: Float, target: Float) {
        val here = position()
        val near = (1f - OrbPathGenerator.boundaryFraction(here, path.boundary)) * minExtent() < config.nearBoundaryDeg
        val speed = if (near) config.nearBoundarySpeedDps else config.maxSpeedDps
        var budget = speed * dt
        while (budget > 0f) {
            val i = index.toInt() % path.samples.size
            val j = (i + 1) % path.samples.size
            val segment = path.samples[i].distanceTo(path.samples[j]).coerceAtLeast(1e-4f)
            val fraction = index - index.toInt()
            val left = segment * (1f - fraction)
            val toTarget = forwardDistance(index, target)
            if (budget >= left && toTarget >= 1f - fraction) {
                index = ((index.toInt() + 1) % path.samples.size).toFloat()
                budget -= left
                if (abs(index - target) < 1e-3f) return
            } else {
                index += minOf(budget / segment, toTarget)
                return
            }
        }
    }

    private fun minExtent() = with(path.boundary) { minOf(left, right, up, down) }

    private fun forwardDistance(from: Float, to: Float): Float {
        val n = path.samples.size
        return ((to - from) % n + n) % n
    }

    private fun position(): GazePoint {
        val n = path.samples.size
        val i = index.toInt() % n
        val j = (i + 1) % n
        val t = index - index.toInt()
        val a = path.samples[i]
        val b = path.samples[j]
        return GazePoint(a.azimuthDeg + (b.azimuthDeg - a.azimuthDeg) * t, a.elevationDeg + (b.elevationDeg - a.elevationDeg) * t)
    }

    companion object {
        /** Unused helper kept out: PI import guard for polar maths in tests. */
        internal const val TWO_PI = (2 * PI).toFloat()
    }
}
