package com.armilla.neckcare.scene

import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import kotlin.math.cos
import kotlin.math.sin

/**
 * 静台: the 1.5 m round platform under the user. Fill #091419, rim paper white 24 %, inner ring
 * 10 %, radial tick marks 22 %. It and the horizon are the fixed references for comfort.
 */
class Dais(radiusM: Float = 1.5f) : Entity() {
    init {
        val up = Vec3.Y
        val fill = MeshData().disc(Vec3(0f, 0.002f, 0f), up, radiusM, segments = 96)
        // The scale, reticle and readings of the flexion test reach below floor level when the user
        // looks down, so the platform must not hide them: it draws without writing depth.
        val fillMaterial = SceneKit.material(SceneColor.INK).apply { setDepthWrite(false) }
        SceneKit.model(fill, fillMaterial, "dais_fill")?.let(::addChild)

        val rim = MeshData().disc(Vec3(0f, 0.004f, 0f), up, radiusM, 96, innerRadius = radiusM - 0.006f)
        SceneKit.model(rim, SceneKit.material(SceneColor.PAPER, alpha = 0.24f), "dais_rim")?.let(::addChild)

        val inner = MeshData().disc(Vec3(0f, 0.004f, 0f), up, radiusM * 0.86f, 96, innerRadius = radiusM * 0.86f - 0.004f)
        SceneKit.model(inner, SceneKit.material(SceneColor.PAPER, alpha = 0.10f), "dais_inner")?.let(::addChild)

        val ticks = MeshData()
        for (deg in 0 until 360 step 5) {
            val a = Math.toRadians(deg.toDouble())
            val dir = Vec3(sin(a).toFloat(), 0f, -cos(a).toFloat())
            val length = if (deg % 15 == 0) 0.11f else 0.06f
            val start = dir * (radiusM - 0.03f) + Vec3(0f, 0.005f, 0f)
            val end = dir * (radiusM - 0.03f - length) + Vec3(0f, 0.005f, 0f)
            ticks.tube(listOf(start, end), 0.0018f, sides = 4)
        }
        SceneKit.model(ticks, SceneKit.material(SceneColor.PAPER, alpha = 0.22f), "dais_ticks")?.let(::addChild)
    }
}
