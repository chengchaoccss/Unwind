package com.armilla.neckcare.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadAngleCalculatorTest {
    private val up = floatArrayOf(0f, 1f, 0f)
    private val right = floatArrayOf(1f, 0f, 0f)
    private val towardUser = floatArrayOf(0f, 0f, 1f)

    private fun turn(axis: FloatArray, deg: Float) = Quaternion.axisAngle(axis[0], axis[1], axis[2], deg)

    @Test
    fun neutralPoseReadsZeroOnEveryAxis() {
        val a = HeadAngleCalculator.angles(Quaternion.IDENTITY, Quaternion.IDENTITY)
        assertEquals(0f, a.rotationDeg, 0.01f)
        assertEquals(0f, a.flexionDeg, 0.01f)
        assertEquals(0f, a.lateralDeg, 0.01f)
    }

    @Test
    fun turningRightIsPositiveRotation() {
        // A right-handed turn about +Y is to the left, so turning right is a negative angle.
        val a = HeadAngleCalculator.angles(Quaternion.IDENTITY, turn(up, -38f))
        assertEquals(38f, a.rotationDeg, 0.05f)
        assertEquals(0f, a.flexionDeg, 0.05f)
        assertEquals(0f, a.lateralDeg, 0.05f)
    }

    @Test
    fun turningLeftIsNegativeRotation() {
        val a = HeadAngleCalculator.angles(Quaternion.IDENTITY, turn(up, 64f))
        assertEquals(-64f, a.rotationDeg, 0.05f)
    }

    @Test
    fun lookingUpIsPositiveFlexionAxis() {
        val a = HeadAngleCalculator.angles(Quaternion.IDENTITY, turn(right, 58f))
        assertEquals(58f, a.flexionDeg, 0.05f)
        assertEquals(0f, a.rotationDeg, 0.05f)
    }

    @Test
    fun rightEarToRightShoulderIsPositiveLateral() {
        // Forward is -Z, so tilting toward the right shoulder is a negative turn about +Z.
        val a = HeadAngleCalculator.angles(Quaternion.IDENTITY, turn(towardUser, -41f))
        assertEquals(41f, a.lateralDeg, 0.05f)
        assertEquals(0f, a.rotationDeg, 0.05f)
    }

    @Test
    fun anglesAreRelativeToTheCalibratedHeading() {
        val neutral = turn(up, 120f)
        val current = neutral * turn(up, -30f)
        assertEquals(30f, HeadAngleCalculator.angles(neutral, current).rotationDeg, 0.05f)
    }

    @Test
    fun yawOnlyDropsPitchButKeepsHeading() {
        val tiltedAndTurned = turn(up, 40f) * turn(right, 25f)
        val heading = HeadAngleCalculator.yawOnly(tiltedAndTurned)
        val a = HeadAngleCalculator.angles(heading, turn(up, 40f))
        assertEquals(0f, a.rotationDeg, 0.05f)
        assertEquals(0f, a.flexionDeg, 0.05f)
    }
}
