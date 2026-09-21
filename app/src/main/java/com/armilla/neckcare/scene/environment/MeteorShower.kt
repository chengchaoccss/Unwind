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
 * Now and then a shooting star crosses the upper sky: a short additive streak, bright at the head,
 * that travels 15 to 30 degrees in about a second and fades in and out. Kept above 22° so it never
 * competes with panels, and rare (every 7 to 18 s) so the sky stays calm.
 */
class MeteorShower(private val parent: Entity, private val eyeHeight: () -> Float) {
    private val random = Random(System.nanoTime())
    private val material: UnlitMaterial = SceneKit.textured(paintStreak(), additive = true)
    private var streak: Entity? = null
    private var wait = 5f
    private var age = 0f
    private var duration = 1f
    private var start = Vec3.ZERO
    private var end = Vec3.ZERO

    fun update(dt: Float) {
        val entity = streak
        if (entity == null) {
            wait -= dt
            if (wait <= 0f) launch()
            return
        }
        age += dt
        val t = age / duration
        if (t >= 1f) {
            entity.destroy()
            streak = null
            wait = 7f + random.nextFloat() * 11f
            return
        }
        entity.components[TransformComponent::class.java]?.setPosition((start + (end - start) * t).toVector3())
        val glow = sin(PI.toFloat() * t)
        material.setBaseColor(Color4(glow, glow, glow, 1f))
    }

    private fun launch() {
        val az = -75f + random.nextFloat() * 150f
        val el = 28f + random.nextFloat() * 32f
        val sweep = (14f + random.nextFloat() * 16f) * (if (random.nextBoolean()) 1f else -1f)
        val drop = 6f + random.nextFloat() * 9f
        start = onSky(az, el)
        end = onSky(az + sweep, el - drop)
        duration = 0.8f + random.nextFloat() * 0.5f
        age = 0f
        val travel = (end - start).normalized()
        val toEye = (Vec3(0f, eyeHeight(), 0f) - start).normalized()
        val across = toEye.cross(travel).normalized()
        val mesh = MeshData().quad(Vec3.ZERO, travel * (LENGTH_M / 2f), across * (WIDTH_M / 2f))
        streak =
            SceneKit.model(mesh, material, "meteor")?.also {
                it.components[TransformComponent::class.java]?.setPosition(start.toVector3())
                parent.addChild(it)
            }
        material.setBaseColor(Color4(0f, 0f, 0f, 1f))
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
        const val LENGTH_M = 4.2f
        const val WIDTH_M = 0.22f
    }
}
