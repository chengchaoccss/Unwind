package com.armilla.neckcare.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ambient bed for the lake at dawn, synthesised so the app ships no audio assets: a slow pad on an
 * open D chord whose voices swell and fade at different rates, over a very quiet wash of filtered
 * noise for water and wind. Every frequency and every swell fits the loop a whole number of times,
 * so the 32-second buffer repeats without a seam. PRD §10: default volume 30 %, can be turned off.
 */
class AmbientPlayer {
    private var track: AudioTrack? = null
    private var volume = 0.3f

    suspend fun start(initialVolume: Float) {
        volume = initialVolume
        if (track != null) return
        val samples = withContext(Dispatchers.Default) { render() }
        track =
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
                .apply {
                    write(samples, 0, samples.size)
                    setLoopPoints(0, samples.size, -1)
                    setVolume(volume)
                    if (volume > 0f) play()
                }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        track?.runCatching {
            setVolume(volume)
            if (volume <= 0f && playState == AudioTrack.PLAYSTATE_PLAYING) pause()
            if (volume > 0f && playState != AudioTrack.PLAYSTATE_PLAYING) play()
        }
    }

    fun pause() { track?.runCatching { pause() } }

    fun resume() { if (volume > 0f) track?.runCatching { play() } }

    fun release() {
        track?.release()
        track = null
    }

    private fun render(): ShortArray {
        val n = RATE * LOOP_SECONDS
        val mix = FloatArray(n)
        // D2 A2 D3 A3 F#4 A4 E5: voice, swells per loop, swell phase, level.
        val voices =
            listOf(
                Voice(73.42, 1, 0.0, 0.30), Voice(110.0, 2, 0.35, 0.22), Voice(146.83, 3, 0.6, 0.20),
                Voice(220.0, 2, 0.1, 0.13), Voice(369.99, 3, 0.8, 0.07), Voice(440.0, 5, 0.45, 0.05),
                Voice(659.25, 4, 0.25, 0.03),
            )
        for (v in voices) {
            val cycles = (v.hz * LOOP_SECONDS).roundToInt()
            val detuned = ((v.hz * 1.003) * LOOP_SECONDS).roundToInt()
            for (i in 0 until n) {
                val t = i.toDouble() / n
                val swell = 0.5 - 0.5 * kotlin.math.cos(2 * PI * (v.swells * t + v.phase))
                val tone = sin(2 * PI * cycles * t) + 0.6 * sin(2 * PI * detuned * t)
                mix[i] += (tone * swell * swell * v.level).toFloat()
            }
        }
        // Water and wind: low-passed noise that breathes twice per loop, cross-faded over its ends.
        val noise = FloatArray(n)
        val random = Random(7)
        var low = 0f
        for (i in 0 until n) {
            low += (random.nextFloat() * 2f - 1f - low) * 0.035f
            noise[i] = low
        }
        val fade = RATE * 2
        for (i in 0 until fade) {
            val w = i.toFloat() / fade
            noise[i] = noise[i] * w + noise[n - fade + i] * (1f - w)
        }
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val breathe = 0.55 + 0.45 * sin(2 * PI * 2 * t)
            mix[i] += (noise[i.coerceAtMost(n - fade - 1)] * breathe * 0.9).toFloat()
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-3f)
        return ShortArray(n) { (mix[it] / peak * 0.8f * Short.MAX_VALUE).toInt().toShort() }
    }

    private class Voice(val hz: Double, val swells: Int, val phase: Double, val level: Double)

    private companion object {
        const val RATE = 22050
        const val LOOP_SECONDS = 32
    }
}
