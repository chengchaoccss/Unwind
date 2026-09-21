package com.armilla.neckcare.domain.usecase

import com.armilla.neckcare.domain.model.Axis
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** Minimal quaternion so the measurement maths stays free of SDK and Android types. */
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    fun conjugate() = Quaternion(-x, -y, -z, w)

    operator fun times(o: Quaternion) =
        Quaternion(
            w * o.x + x * o.w + y * o.z - z * o.y,
            w * o.y - x * o.z + y * o.w + z * o.x,
            w * o.z + x * o.y - y * o.x + z * o.w,
            w * o.w - x * o.x - y * o.y - z * o.z,
        )

    fun normalized(): Quaternion {
        val n = sqrt(x * x + y * y + z * z + w * w)
        return if (n < 1e-9f) IDENTITY else Quaternion(x / n, y / n, z / n, w / n)
    }

    fun rotate(vx: Float, vy: Float, vz: Float): FloatArray {
        val p = this * Quaternion(vx, vy, vz, 0f) * conjugate()
        return floatArrayOf(p.x, p.y, p.z)
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        /** Rotation of [angleDeg] about a unit axis. */
        fun axisAngle(ax: Float, ay: Float, az: Float, angleDeg: Float): Quaternion {
            val half = Math.toRadians(angleDeg.toDouble()) / 2.0
            val s = kotlin.math.sin(half).toFloat()
            return Quaternion(ax * s, ay * s, az * s, kotlin.math.cos(half).toFloat())
        }
    }
}

/** Signed neck angles in degrees: right rotation, extension and right bend are positive. */
data class HeadAngles(val rotationDeg: Float, val flexionDeg: Float, val lateralDeg: Float) {
    fun of(axis: Axis): Float =
        when (axis) {
            Axis.ROTATION -> rotationDeg
            Axis.FLEXION -> flexionDeg
            Axis.LATERAL -> lateralDeg
        }

    companion object {
        val ZERO = HeadAngles(0f, 0f, 0f)
    }
}

/**
 * PRD §11: take the head pose relative to the calibrated neutral pose q0, move the head's forward
 * vector f and right vector r into the calibration frame (x right, y up, z forward) and read
 *
 *     rotation = atan2(f.x, f.z)    flexion = asin(f.y)    lateral = -asin(r.y)
 *
 * The stage frame is right-handed with +Z toward the user, so "forward" there is -Z.
 */
object HeadAngleCalculator {
    fun angles(neutral: Quaternion, current: Quaternion): HeadAngles {
        val relative = (neutral.conjugate() * current).normalized()
        val f = relative.rotate(0f, 0f, -1f)
        val r = relative.rotate(1f, 0f, 0f)
        val forwardZ = -f[2]
        return HeadAngles(
            rotationDeg = Math.toDegrees(atan2(f[0], forwardZ).toDouble()).toFloat(),
            flexionDeg = Math.toDegrees(asin(f[1].coerceIn(-1f, 1f)).toDouble()).toFloat(),
            lateralDeg = -Math.toDegrees(asin(r[1].coerceIn(-1f, 1f)).toDouble()).toFloat(),
        )
    }

    /** Keeps only the heading of [q], so calibration is not skewed by a tilted head. */
    fun yawOnly(q: Quaternion): Quaternion {
        val f = q.rotate(0f, 0f, -1f)
        val yawDeg = Math.toDegrees(atan2(-f[0], -f[2]).toDouble()).toFloat()
        return Quaternion.axisAngle(0f, 1f, 0f, yawDeg)
    }
}
