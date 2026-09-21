package com.armilla.neckcare.scene.environment

import com.armilla.neckcare.scene.SceneColor
import com.armilla.neckcare.scene.SceneKit
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 群星: a loose river of stars arching over the lake, gathered into a handful of clusters, on top
 * of the painted sky. The stars are split into groups that each brighten and dim at their own
 * slow pace, so the sky shimmers instead of sitting still. One mesh and one material per group.
 */
class StarClusters(eyeHeightM: Float) : Entity() {
    private class Group(val material: UnlitMaterial, val color: SceneColor, val period: Float, val phase: Float, val low: Float, val high: Float)

    private val groups = ArrayList<Group>()
    private var clock = 0f

    init {
        val random = Random(20260921)
        val meshes = List(GROUPS) { MeshData() }
        // Cluster centres along an arc that rises from the left horizon, peaks ahead, falls right.
        val centres = (0 until CLUSTERS).map { i ->
            val t = i / (CLUSTERS - 1f)
            val az = -95f + 190f * t + (random.nextFloat() - 0.5f) * 14f
            val el = 30f + 34f * sin(Math.PI.toFloat() * t) + (random.nextFloat() - 0.5f) * 8f
            az to el
        }
        for ((cAz, cEl) in centres) {
            repeat(STARS_PER_CLUSTER) {
                // Gaussian-ish scatter: most stars huddle near the centre, a few stray.
                val spread = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f)
                val spreadEl = (random.nextFloat() + random.nextFloat() + random.nextFloat() - 1.5f)
                val az = cAz + spread * 16f
                val el = (cEl + spreadEl * 9f).coerceIn(14f, 85f)
                val big = random.nextFloat() < 0.12f
                val radius = if (big) 0.085f + random.nextFloat() * 0.05f else 0.03f + random.nextFloat() * 0.035f
                meshes[random.nextInt(GROUPS)].sphere(onSky(az, el, eyeHeightM), radius, 6, 4)
            }
        }
        meshes.forEachIndexed { i, mesh ->
            val color = if (i % 4 == 3) SceneColor.ORB_LIGHT else if (i % 4 == 2) SceneColor.JADE else SceneColor.WHITE_HOT
            val material = SceneKit.material(color, 0.6f, additive = true)
            SceneKit.model(mesh, material, "star_cluster_$i")?.let(::addChild)
            groups += Group(material, color, period = 2.4f + i * 0.9f, phase = i * 1.3f, low = 0.18f, high = if (i % 4 == 2) 0.55f else 0.95f)
        }
    }

    fun update(dt: Float) {
        clock += dt
        for (g in groups) {
            val wave = 0.5f + 0.5f * sin(clock * 6.2832f / g.period + g.phase)
            g.material.setBaseColor(g.color.color4(g.low + (g.high - g.low) * wave * wave))
        }
    }

    private fun onSky(azimuthDeg: Float, elevationDeg: Float, eyeHeightM: Float): Vec3 {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        return Vec3(0f, eyeHeightM, 0f) +
            Vec3((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (-cos(el) * cos(az)).toFloat()) * RADIUS_M
    }

    private companion object {
        const val RADIUS_M = 42f
        const val GROUPS = 6
        const val CLUSTERS = 7
        const val STARS_PER_CLUSTER = 46
    }
}
