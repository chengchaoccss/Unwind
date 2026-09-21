package com.armilla.neckcare.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Cue sounds, all synthesised. The three of PRD §10 are short, soft single tones, never a rising
 * "victory" sound; the punch exercise adds a soft kick and a brushed tick for its beat, and a
 * brighter ping for a hit. One static AudioTrack per cue, reused for every play.
 */
class CuePlayer {
    enum class Cue(val seconds: Double, val volume: Float) {
        RECORDED(0.22, 1f),
        ORB_CAUGHT(0.22, 1f),
        LAP(0.22, 1f),
        PUNCH(0.18, 1f),
        KICK(0.20, 0.9f),
        TICK(0.07, 0.45f),
    }

    private val tracks = Cue.entries.associateWith(::build)

    fun play(cue: Cue) {
        tracks[cue]?.runCatching {
            stop()
            reloadStaticData()
            play()
        }
    }

    fun release() = tracks.values.forEach { it.release() }

    private fun tone(hz: Double, t: Double, decay: Double = 16.0) = sin(2 * PI * hz * t) * (1 - exp(-t * 220)) * exp(-t * decay) * 0.35

    private fun sample(cue: Cue, t: Double, noise: Random): Double =
        when (cue) {
            Cue.RECORDED -> tone(587.33, t)
            Cue.ORB_CAUGHT -> tone(783.99, t)
            Cue.LAP -> tone(440.0, t)
            // A bright two-note ping: the hit should feel rewarding on the beat.
            Cue.PUNCH -> tone(880.0, t, 20.0) + 0.6 * tone(1318.5, t, 26.0)
            // Kick: a sine that falls from 120 Hz to 45 Hz.
            Cue.KICK -> sin(2 * PI * (45.0 * t + (75.0 / 28.0) * (1 - exp(-28.0 * t)))) * exp(-t * 18) * 0.8
            Cue.TICK -> (noise.nextDouble() * 2 - 1) * exp(-t * 90) * 0.5
        }

    private fun build(cue: Cue): AudioTrack {
        val rate = 44100
        val noise = Random(3)
        val samples = ShortArray((rate * cue.seconds).toInt()) { i ->
            (sample(cue, i / rate.toDouble(), noise).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .apply {
                write(samples, 0, samples.size)
                setVolume(cue.volume)
            }
    }
}
