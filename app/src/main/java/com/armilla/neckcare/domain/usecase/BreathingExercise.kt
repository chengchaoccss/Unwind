package com.armilla.neckcare.domain.usecase

data class BreathingConfig(
    val inhaleSeconds: Float = 4f,
    val exhaleSeconds: Float = 6f,
    val breaths: Int = 12,
    /** How far the rings stay fanned out at the end of the exhale. */
    val restExpansion: Float = 0.05f,
    val restScale: Float = 0.56f,
    val restBrightness: Float = 0.55f,
    /** PRD of the board: the environment dims to half within three seconds of starting. */
    val dimSeconds: Float = 3f,
    val dimmedEnvironment: Float = 0.5f,
)

enum class BreathPhase(val cue: String) {
    INHALE("吸气"),
    EXHALE("呼气"),
}

data class BreathSnapshot(
    val phase: BreathPhase,
    /** 1-based count of the breath in progress. */
    val breath: Int,
    val totalBreaths: Int,
    /** 0.05 (one flat ring) to 1 (a full sphere of rings). */
    val expansion: Float,
    /** 0.56 to 1: diameter of the sphere relative to a full breath. */
    val scale: Float,
    /** 0.55 to 1. */
    val brightness: Float,
    /** Jade glow swells with the inhale; amber glow peaks in the middle of the exhale. */
    val jadeGlow: Float,
    val amberGlow: Float,
    /** Opacity of the two cue words, which cross-fade a little after each turn of the breath. */
    val inhaleCue: Float,
    val exhaleCue: Float,
    val environment: Float,
    val remainingSeconds: Int,
    val progress: Float,
    val finished: Boolean,
)

/**
 * 三环呼吸: ten seconds a breath, four in and six out, six breaths a minute, twelve breaths. The
 * curve and every level are the key frames of the "一次呼吸的四个相位" board, eased in and out
 * between the end of the exhale (0 s), the full breath (4 s) and the end of the exhale again (10 s).
 */
class BreathingExercise(private val config: BreathingConfig = BreathingConfig()) {
    private val cycle = config.inhaleSeconds + config.exhaleSeconds
    private var elapsed = 0f

    fun update(dtSeconds: Float): BreathSnapshot {
        elapsed += dtSeconds.coerceIn(0f, 0.1f)
        val total = cycle * config.breaths
        val clamped = elapsed.coerceAtMost(total)
        val t = clamped % cycle
        val inhaling = t < config.inhaleSeconds && clamped < total
        // 0 at the end of the exhale, 1 at the full breath.
        val fullness =
            if (clamped >= total) 0f
            else if (inhaling) ease(t / config.inhaleSeconds)
            else 1f - ease((t - config.inhaleSeconds) / config.exhaleSeconds)
        val u = t / cycle
        return BreathSnapshot(
            phase = if (inhaling) BreathPhase.INHALE else BreathPhase.EXHALE,
            breath = ((clamped / cycle).toInt() + 1).coerceAtMost(config.breaths),
            totalBreaths = config.breaths,
            expansion = mix(config.restExpansion, 1f, fullness),
            scale = mix(config.restScale, 1f, fullness),
            brightness = mix(config.restBrightness, 1f, fullness),
            jadeGlow = mix(0.2f, 1f, fullness),
            // 0.25 at rest, 0.1 at the full breath, 0.9 at 72 % of the cycle, back to 0.25.
            amberGlow =
                when {
                    u < 0.4f -> mix(0.25f, 0.1f, ease(u / 0.4f))
                    u < 0.72f -> mix(0.1f, 0.9f, ease((u - 0.4f) / 0.32f))
                    else -> mix(0.9f, 0.25f, ease((u - 0.72f) / 0.28f))
                },
            inhaleCue = ramp(u, 0f, 0.05f) * (1f - ramp(u, 0.33f, 0.40f)),
            exhaleCue = ramp(u, 0.40f, 0.46f) * (1f - ramp(u, 0.92f, 0.99f)),
            environment = mix(1f, config.dimmedEnvironment, (elapsed / config.dimSeconds).coerceIn(0f, 1f)),
            remainingSeconds = kotlin.math.ceil((total - clamped).toDouble()).toInt(),
            progress = clamped / total,
            finished = elapsed >= total,
        )
    }

    private fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun ramp(x: Float, from: Float, to: Float) = ((x - from) / (to - from)).coerceIn(0f, 1f)

    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
}
