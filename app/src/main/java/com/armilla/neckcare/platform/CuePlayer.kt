package com.armilla.neckcare.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The three cue sounds of PRD §10, synthesised: short, soft single tones with a quick fade, never
 * a rising "victory" sound. One static AudioTrack per cue, reused for every play.
 */
class CuePlayer {
    enum class Cue(val hz: Double) {
        RECORDED(587.33),
        ORB_CAUGHT(783.99),
        LAP(440.0),
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

    private fun build(cue: Cue): AudioTrack {
        val rate = 44100
        val samples = ShortArray((rate * 0.22).toInt())
        for (i in samples.indices) {
            val t = i / rate.toDouble()
            val envelope = (1 - exp(-t * 220)) * exp(-t * 16)
            samples[i] = (sin(2 * PI * cue.hz * t) * envelope * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .apply { write(samples, 0, samples.size) }
    }
}
