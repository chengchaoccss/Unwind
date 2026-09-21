package com.armilla.neckcare.scene.environment

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.armilla.neckcare.scene.SceneKit
import com.armilla.neckcare.scene.SceneKit.toVector3
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shooting stars across the upper sky: a new one every 1 to 3.5 seconds, now and then a small
 * shower of two to four, and every 16 to 28 seconds a storm: twelve to eighteen meteors pouring
 * out of one radiant over about three seconds. Each is a short additive streak, bright at the head,
 * that travels 15 to 35 degrees in about a second and fades in and out. They stay above 24° so
 * they never cross the panels.
 */
class MeteorShower(private val parent: Entity, private val eyeHeight: () -> Float) {
    /**
     * One material per meteor: destroying an entity releases the resources it holds, so a shared
     * material would already be closed when the next meteor is built. Only the bitmap is kept.
     */
    private class Meteor(val entity: Entity, val material: UnlitMaterial, val start: Vec3, val end: Vec3, val duration: Float, val peak: Float, var age: Float)

    private val random = Random(System.nanoTime())
    private val streakBitmap = paintStreak()
    private val flying = ArrayList<Meteor>()
    private var wait = 1.5f
    private var stormWait = 9f

    fun update(dt: Float) {
        stormWait -= dt
        if (stormWait <= 0f) {
            stormWait = 16f + random.nextFloat() * 12f
            // A storm shares one radiant and heading, so the streaks fan out in parallel.
            val radiant = -60f + random.nextFloat() * 120f
            val heading = if (random.nextBoolean()) 1f else -1f
            repeat(12 + random.nextInt(7)) {
                launch(radiant + (random.nextFloat() - 0.5f) * 70f, heading, delay = random.nextFloat() * 3.2f)
            }
        }
        wait -= dt
        if (wait <= 0f) {
            val shower = random.nextFloat() < 0.22f
            val count = if (shower) 2 + random.nextInt(3) else 1
            // A shower shares a radiant, so its streaks run roughly parallel.
            val heading = if (random.nextBoolean()) 1f else -1f
            val az = -80f + random.nextFloat() * 160f
            repeat(count) { i -> if (flying.size < MAX_SINGLES) launch(az + i * (6f + random.nextFloat() * 10f), heading, delay = i * 0.18f) }
            wait = 1f + random.nextFloat() * 2.5f
        }
        val done = ArrayList<Meteor>()
        for (m in flying) {
            m.age += dt
            val t = m.age / m.duration
            when {
                t >= 1f -> done += m
                t >= 0f -> {
                    m.entity.components[TransformComponent::class.java]?.setPosition((m.start + (m.end - m.start) * t).toVector3())
                    val glow = sin(PI.toFloat() * t) * m.peak
                    m.material.setBaseColor(Color4(glow, glow, glow, 1f))
                }
            }
        }
        done.forEach {
            it.entity.destroy()
            flying.remove(it)
        }
    }

    private fun launch(azimuthDeg: Float, heading: Float, delay: Float) {
        val el = 26f + random.nextFloat() * 40f
        val sweep = (15f + random.nextFloat() * 20f) * heading
        val drop = 5f + random.nextFloat() * 11f
        val start = onSky(azimuthDeg, el)
        val end = onSky(azimuthDeg + sweep, (el - drop).coerceAtLeast(24f))
        val size = 0.6f + random.nextFloat() * 0.9f
        val travel = (end - start).normalized()
        val toEye = (Vec3(0f, eyeHeight(), 0f) - start).normalized()
        val across = toEye.cross(travel).normalized()
        val mesh = MeshData().quad(Vec3.ZERO, travel * (LENGTH_M * size / 2f), across * (WIDTH_M * size / 2f))
        val material = SceneKit.textured(streakBitmap, additive = true).apply { setBaseColor(Color4(0f, 0f, 0f, 1f)) }
        val entity = SceneKit.model(mesh, material, "meteor") ?: return
        entity.components[TransformComponent::class.java]?.setPosition(start.toVector3())
        parent.addChild(entity)
        flying += Meteor(entity, material, start, end, 0.7f + random.nextFloat() * 0.7f, 0.7f + random.nextFloat() * 0.6f, -delay)
    }

    private fun onSky(azimuthDeg: Float, elevationDeg: Float): Vec3 {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        return Vec3(0f, eyeHeight(), 0f) +
            Vec3((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (-cos(el) * cos(az)).toFloat()) * RADIUS_M
    }

    /** Additive blending adds RGB, so the tail is painted as brightness fading to black. */
    private fun paintStreak(): Bitmap {
        val w = 256
        val h = 16
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0 until h) {
            val fall = 1f - kotlin.math.abs(row - (h - 1) / 2f) / (h / 2f)
            val level = fall * fall
            paint.shader =
                LinearGradient(
                    0f, 0f, w.toFloat(), 0f,
                    intArrayOf(Color.BLACK, Color.rgb((0x60 * level).toInt(), (0x5C * level).toInt(), (0x52 * level).toInt()), Color.rgb((0xEF * level).toInt(), (0xE9 * level).toInt(), (0xDC * level).toInt())),
                    floatArrayOf(0f, 0.7f, 1f),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(0f, row.toFloat(), w.toFloat(), row + 1f, paint)
        }
        return bitmap
    }

    private companion object {
        const val RADIUS_M = 40f
        const val LENGTH_M = 4.6f
        const val WIDTH_M = 0.24f
        const val MAX_SINGLES = 8
    }
}
