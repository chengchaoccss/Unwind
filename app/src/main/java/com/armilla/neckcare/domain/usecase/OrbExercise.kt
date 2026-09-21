package com.armilla.neckcare.domain.usecase

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

    /** Degrees travelled along the route up to each sample; the last entry closes the loop. */
    val cumulativeDeg: FloatArray by lazy {
        FloatArray(samples.size + 1).also { c ->
            for (i in samples.indices) c[i + 1] = c[i] + samples[i].distanceTo(samples[(i + 1) % samples.size]).coerceAtLeast(1e-4f)
        }
    }

    val lengthDeg: Float
        get() = cumulativeDeg[samples.size]

    fun wrap(arcDeg: Float): Float = ((arcDeg % lengthDeg) + lengthDeg) % lengthDeg

    /** Index of the sample at or just before [arcDeg] along the route. */
    fun sampleAt(arcDeg: Float): Int {
        val a = wrap(arcDeg)
        var lo = 0
        var hi = samples.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulativeDeg[mid] <= a) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAtArc(arcDeg: Float): GazePoint {
        val a = wrap(arcDeg)
        val i = sampleAt(a)
        val p = samples[i]
        val q = samples[(i + 1) % samples.size]
        val t = ((a - cumulativeDeg[i]) / (cumulativeDeg[i + 1] - cumulativeDeg[i])).coerceIn(0f, 1f)
        return GazePoint(p.azimuthDeg + (q.azimuthDeg - p.azimuthDeg) * t, p.elevationDeg + (q.elevationDeg - p.elevationDeg) * t)
    }
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
    /**
     * PRD §7 capped the orb at 15°/s, 8°/s near the boundary. Worn, that read as slow and the hard
     * stops as jerky; the product owner asked for a quicker, smoother orb and no speed limits.
     */
    val maxSpeedDps: Float = 32f,
    val nearBoundarySpeedDps: Float = 20f,
    val nearBoundaryDeg: Float = 10f,
    /** The orb eases up to speed and brakes into each catch point instead of stopping dead. */
    val accelerationDps2: Float = 45f,
    val brakingDps2: Float = 40f,
    val catchRadiusDeg: Float = 5.5f,
    val catchSeconds: Float = 1.0f,
    /** Twice round the twelve catch points, so the quicker orb still fills the 90 s. */
    val laps: Int = 2,
    val waitAfterAwaySeconds: Float = 3f,
    val tooFastHeadDps: Float = 60f,
    val tooFastPauseSeconds: Float = 1f,
    /** PRD §7 pauses the orb after a fast head turn; switched off by the product owner. */
    val pauseOnFastHead: Boolean = false,
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
    /** Degrees along the route, for drawing the trail behind the orb. */
    val arcDeg: Float,
    val nextCatchOrdinal: Int,
)

/**
 * PRD §7 rules, with the speeds of [OrbConfig]: gaze time inside the catch ring accumulates and
 * never runs backward; the orb waits at a catch point until it is caught, and eases to a stop where
 * it is when the gaze has been away for 3 s. Nothing is scored down and nothing reports failure.
 */
class OrbExercise(private val path: OrbPath, private val config: OrbConfig = OrbConfig()) {
    private val total = OrbPathGenerator.TOTAL * config.laps
    private var arc = 0f
    private var speed = 0f
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
        var justCaught = false
        if (!isFinished()) {
            if (config.pauseOnFastHead && headSpeedDps > config.tooFastHeadDps) {
                pause = config.tooFastPauseSeconds
                hint = 3f
                speed = 0f
            }
            pause = (pause - dt).coerceAtLeast(0f)
            hint = (hint - dt).coerceAtLeast(0f)

            val looking = gaze.distanceTo(path.pointAtArc(arc)) <= config.catchRadiusDeg
            if (looking) { inRing += dt; away = 0f } else away += dt

            val remaining = path.wrap(path.cumulativeDeg[path.catchIndices[nextCatch]] - arc)
            val atTarget = remaining < AT_TARGET_DEG || remaining > path.lengthDeg - AT_TARGET_DEG
            if (atTarget) {
                speed = 0f
                if (looking && inRing >= config.catchSeconds) {
                    caught++
                    justCaught = true
                    inRing = 0f
                    nextCatch = (nextCatch + 1) % path.catchIndices.size
                }
            } else {
                advance(dt, remaining, hold = pause > 0f || away >= config.waitAfterAwaySeconds)
            }
        }
        val remainingSeconds = (config.durationSeconds - elapsed).coerceAtLeast(0f)
        return OrbSnapshot(
            position = path.pointAtArc(arc),
            caught = caught,
            total = total,
            catchProgress = (inRing / config.catchSeconds).coerceIn(0f, 1f),
            remainingSeconds = kotlin.math.ceil(remainingSeconds).toInt(),
            timeProgress = (elapsed / config.durationSeconds).coerceIn(0f, 1f),
            waiting = away >= config.waitAfterAwaySeconds,
            showSlowHint = hint > 0f,
            justCaught = justCaught,
            finished = isFinished(),
            sampleIndex = path.sampleAt(arc),
            arcDeg = arc,
            nextCatchOrdinal = nextCatch,
        )
    }

    private fun isFinished() = elapsed >= config.durationSeconds || caught >= total

    private fun advance(dt: Float, remaining: Float, hold: Boolean) {
        val here = path.pointAtArc(arc)
        val near = (1f - OrbPathGenerator.boundaryFraction(here, path.boundary)) * minExtent() < config.nearBoundaryDeg
        val cruise = if (near) config.nearBoundarySpeedDps else config.maxSpeedDps
        // The speed from which constant braking stops exactly on the catch point.
        val braking = sqrt(2f * config.brakingDps2 * remaining)
        val wanted = if (hold) 0f else minOf(cruise, maxOf(braking, CRAWL_DPS))
        speed += (wanted - speed).coerceIn(-config.brakingDps2 * 2f * dt, config.accelerationDps2 * dt)
        arc = path.wrap(arc + minOf(speed * dt, remaining))
    }

    private fun minExtent() = with(path.boundary) { minOf(left, right, up, down) }

    private companion object {
        const val AT_TARGET_DEG = 0.03f
        const val CRAWL_DPS = 1.5f
    }
}
