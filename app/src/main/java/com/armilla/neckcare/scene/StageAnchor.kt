package com.armilla.neckcare.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Root of everything the app places. The stage origin is wherever the system put it, not where
 * the user is, so calibration moves this entity under the user's head and turns it to their
 * heading. Children then use the layout of the "空间布局" board directly: -Z is straight ahead,
 * +X is right, and heights are given relative to the line of sight via [eyeHeightM].
 */
class StageAnchor : Entity() {
    var eyeHeightM: Float = DEFAULT_EYE_HEIGHT_M
        private set

    /** Re-centres the layout on the given head pose (stage space). Only heading is used. */
    fun calibrate(headPosition: Vector3, headRotation: Quat) {
        val forward = headRotation.rotateVector(Vector3(0f, 0f, -1f))
        val yawRad = atan2(-forward.x, -forward.z)
        eyeHeightM = headPosition.y.coerceIn(MIN_EYE_HEIGHT_M, MAX_EYE_HEIGHT_M)
        components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(headPosition.x, 0f, headPosition.z))
            setQuaternion(Quat(Vector3(0f, 1f, 0f), yawRad))
        }
    }

    /**
     * A point at [distanceM] from the eyes, [azimuthDeg] to the right of straight ahead and
     * [elevationDeg] above the line of sight, in this anchor's local space.
     */
    fun polar(distanceM: Float, azimuthDeg: Float, elevationDeg: Float): Vector3 {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        return Vector3(
            (distanceM * cos(el) * sin(az)).toFloat(),
            eyeHeightM + (distanceM * sin(el)).toFloat(),
            (-distanceM * cos(el) * cos(az)).toFloat(),
        )
    }

    /**
     * A point given in artboard pixels (1600 x 900, horizon at y = 450, straight ahead at x = 800)
     * on a plane [distanceM] in front of the user: world mm = px x 1.25 x distance (PRD §4).
     */
    fun board(distanceM: Float, xPx: Float, yPx: Float): Vector3 {
        val mPerPx = 0.00125f * distanceM
        return Vector3((xPx - 800f) * mPerPx, eyeHeightM + (450f - yPx) * mPerPx, -distanceM)
    }

    companion object {
        const val DEFAULT_EYE_HEIGHT_M = 1.6f
        private const val MIN_EYE_HEIGHT_M = 0.9f
        private const val MAX_EYE_HEIGHT_M = 2.1f

        /** Rotation about +Y that makes a panel at [azimuthDeg] turn [towardUserDeg] to the user. */
        fun yaw(degrees: Float): Quat = Quat(Vector3(0f, 1f, 0f), Math.toRadians(degrees.toDouble()).toFloat())

        fun pitch(degrees: Float): Quat = Quat(Vector3(1f, 0f, 0f), Math.toRadians(degrees.toDouble()).toFloat())
    }
}
