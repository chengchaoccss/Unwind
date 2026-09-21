package com.armilla.neckcare.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingExerciseTest {
    private val dt = 1f / 72f

    private fun BreathingExercise.at(seconds: Float): BreathSnapshot {
        var snap = update(0f)
        repeat((seconds / dt).toInt()) { snap = update(dt) }
        return snap
    }

    @Test
    fun theFourKeyFramesOfTheBoard() {
        // 呼尽 0 s: 展开 5 %, 直径 56 %, 亮度 55 %.
        BreathingExercise().at(0.02f).let {
            assertEquals(0.05f, it.expansion, 0.01f)
            assertEquals(0.56f, it.scale, 0.01f)
            assertEquals(0.55f, it.brightness, 0.01f)
        }
        // 吸气中 2 s: 52 %, 78 %, 78 %.
        BreathingExercise().at(2f).let {
            assertEquals(BreathPhase.INHALE, it.phase)
            assertEquals(0.52f, it.expansion, 0.02f)
            assertEquals(0.78f, it.scale, 0.02f)
            assertEquals(0.78f, it.brightness, 0.02f)
        }
        // 吸满 4 s: everything at 100 %.
        BreathingExercise().at(4f).let {
            assertEquals(1f, it.expansion, 0.01f)
            assertEquals(1f, it.scale, 0.01f)
        }
        // 呼气中 7 s: back to 52 %, 78 %, 78 %, now in amber.
        BreathingExercise().at(7f).let {
            assertEquals(BreathPhase.EXHALE, it.phase)
            assertEquals(0.52f, it.expansion, 0.02f)
            assertEquals(0.78f, it.brightness, 0.02f)
            assertTrue(it.amberGlow > it.jadeGlow * 0.9f)
        }
    }

    @Test
    fun exhalingIsSlowerThanInhaling() {
        val inhaleHalf = BreathingExercise().at(1f).expansion - BreathingExercise().at(0.02f).expansion
        val exhaleHalf = BreathingExercise().at(4f).expansion - BreathingExercise().at(5f).expansion
        assertTrue(inhaleHalf > exhaleHalf)
    }

    @Test
    fun theCueWordsTakeTurnsAndNeverShowTogetherAtFullStrength() {
        val exercise = BreathingExercise()
        var snap = exercise.update(0f)
        repeat((10f / dt).toInt()) {
            snap = exercise.update(dt)
            assertTrue(snap.inhaleCue + snap.exhaleCue <= 1.01f)
        }
        assertEquals(1f, BreathingExercise().at(2f).inhaleCue, 0.01f)
        assertEquals(1f, BreathingExercise().at(7f).exhaleCue, 0.01f)
    }

    @Test
    fun theEnvironmentDimsToHalfWithinThreeSeconds() {
        assertEquals(1f, BreathingExercise().at(0.02f).environment, 0.02f)
        assertEquals(0.5f, BreathingExercise().at(3.1f).environment, 0.01f)
        assertEquals(0.5f, BreathingExercise().at(50f).environment, 0.01f)
    }

    @Test
    fun countsTwelveBreathsOverTwoMinutesAndEndsCollapsed() {
        val exercise = BreathingExercise()
        assertEquals(3, exercise.at(24f).breath)
        assertEquals(96, exercise.update(0f).remainingSeconds)
        assertFalse(exercise.update(0f).finished)
        val end = exercise.at(97f)
        assertTrue(end.finished)
        assertEquals(12, end.breath)
        assertEquals(0.05f, end.expansion, 0.01f)
        assertEquals(0, end.remainingSeconds)
    }
}
