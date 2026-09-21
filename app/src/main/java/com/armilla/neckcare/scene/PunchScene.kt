package com.armilla.neckcare.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.armilla.neckcare.domain.usecase.Point3
import com.armilla.neckcare.domain.usecase.PunchExercise
import com.armilla.neckcare.domain.usecase.PunchSide
import com.armilla.neckcare.domain.usecase.PunchSnapshot
import com.armilla.neckcare.domain.usecase.TargetState
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.acos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 出拳: comets fly in along a left (jade) and a right (amber) lane and are met with a boxing
 * glove. Every comet is a white-hot core in a coloured shell with a wide halo, a spinning dashed
 * ring and a tapering tail with sparks; they are pooled and only moved, never rebuilt in flight.
 * Every entity owns its materials: a destroyed entity closes the resources it holds.
 */
class PunchScene(private val eyeHeightM: Float) : Entity() {
    private class Comet(val entity: Entity, val ring: Entity, var id: Int = -1, val phase: Float)

    private val pools =
        mapOf(
            PunchSide.LEFT to List(POOL) { comet(SceneColor.JADE, it) },
            PunchSide.RIGHT to List(POOL) { comet(SceneColor.AMBER, it + POOL) },
        )
    private val leftGlove = glove(SceneColor.JADE, thumbSide = 1f)
    private val rightGlove = glove(SceneColor.AMBER, thumbSide = -1f)
    private var clock = 0f

    /** Debug captures only: show the gloves in front of the user when no hand is tracked. */
    var demoGloves = false

    init {
        pools.values.flatten().forEach {
            it.entity.enabled = false
            addChild(it.entity)
        }
        addChild(leftGlove)
        addChild(rightGlove)
        // Faint dotted lanes so the eye can find where the next comet comes from.
        val lanes = MeshData()
        for (x in listOf(-LANE_M, LANE_M)) {
            var z = -7f
            while (z < -0.9f) {
                lanes.sphere(Vec3(x, eyeHeightM + (PunchExercise.SHOULDER_HEIGHT_M + PunchExercise.CHEST_HEIGHT_M) / 2f, z), 0.008f, 6, 4)
                z += 0.35f
            }
        }
        SceneKit.model(lanes, SceneKit.material(SceneColor.PAPER, 0.22f), "punch_lanes")?.let(::addChild)
    }

    /** [leftForward] / [rightForward]: direction from the wrist to the knuckles, to aim each glove. */
    fun update(dt: Float, snap: PunchSnapshot, leftHand: Point3?, rightHand: Point3?, leftForward: Vec3?, rightForward: Vec3?) {
        clock += dt
        val demoAim = Vec3(0.15f, 0.25f, -1f)
        place(leftGlove, leftHand ?: Point3(-0.17f, -0.2f, -0.55f).takeIf { demoGloves }, leftForward ?: demoAim.takeIf { demoGloves })
        place(rightGlove, rightHand ?: Point3(0.2f, -0.12f, -0.6f).takeIf { demoGloves }, rightForward ?: Vec3(-0.3f, 0.1f, -1f).takeIf { demoGloves })
        for ((side, pool) in pools) {
            val wanted = snap.targets.filter { it.side == side }
            pool.forEach { slot -> if (wanted.none { it.id == slot.id }) { slot.id = -1; slot.entity.enabled = false } }
            for (target in wanted) {
                val slot = pool.firstOrNull { it.id == target.id } ?: pool.firstOrNull { it.id == -1 } ?: continue
                slot.id = target.id
                slot.entity.enabled = true
                val fade = (target.stateAge / PunchExercise.FADE_SECONDS).coerceIn(0f, 1f)
                val pulse = 1f + 0.07f * sin(clock * 7f + slot.phase)
                val scale =
                    when (target.state) {
                        TargetState.FLYING -> pulse
                        TargetState.HIT -> 1f + fade * 2.2f
                        TargetState.PASSED -> 1f - fade
                    }.coerceAtLeast(0.01f)
                slot.entity.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(target.position.x, eyeHeightM + target.position.y, target.position.z))
                    setScaleVector(Vector3(scale, scale, scale))
                }
                slot.ring.components[TransformComponent::class.java]?.setQuaternion(Quat(Vector3(0f, 0f, 1f), clock * 2.4f + slot.phase))
            }
        }
    }

    private fun place(glove: Entity, hand: Point3?, forward: Vec3?) {
        glove.enabled = hand != null
        if (hand == null) return
        glove.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(hand.x, eyeHeightM + hand.y, hand.z))
            forward?.let { setQuaternion(aim(it)) }
        }
    }

    /** Shortest rotation that turns the glove's own forward (-Z) to [forward]. */
    private fun aim(forward: Vec3): Quat {
        val f = forward.normalized()
        val from = Vec3(0f, 0f, -1f)
        val axis = from.cross(f)
        val dot = from.dot(f).coerceIn(-1f, 1f)
        if (axis.length() < 1e-4f) return if (dot > 0f) Quat.identity() else Quat(Vector3(0f, 1f, 0f), Math.PI.toFloat())
        val a = axis.normalized()
        return Quat(Vector3(a.x, a.y, a.z), acos(dot))
    }

    private fun comet(color: SceneColor, seed: Int): Comet {
        val random = Random(seed * 31 + 7)
        val root = Entity()
        // Tail: toward where it came from (-Z); two nested tapers and a line of sparks.
        SceneKit.model(MeshData().taper(Vec3(0f, 0f, -0.03f), Vec3(0f, 0f, -1.9f), 0.075f, 0.002f, 12), SceneKit.material(color, 0.30f, additive = true), "comet_tail_wide")?.let(root::addChild)
        SceneKit.model(MeshData().taper(Vec3(0f, 0f, -0.03f), Vec3(0f, 0f, -1.2f), 0.036f, 0.001f, 10), SceneKit.material(color, 0.65f, additive = true), "comet_tail_core")?.let(root::addChild)
        val sparks = MeshData()
        repeat(14) { i ->
            val z = -0.18f - i * 0.13f - random.nextFloat() * 0.06f
            val spread = 0.05f + i * 0.006f
            sparks.sphere(Vec3((random.nextFloat() - 0.5f) * spread * 2, (random.nextFloat() - 0.5f) * spread * 2, z), 0.011f - i * 0.0005f, 6, 4)
        }
        SceneKit.model(sparks, SceneKit.material(SceneColor.PAPER, 0.9f, additive = true), "comet_sparks")?.let(root::addChild)
        // Head: wide halo card, coloured shell, white-hot core.
        SceneKit.model(MeshData().quad(Vec3(0f, 0f, 0.002f), Vec3.X * 0.36f, Vec3.Y * 0.36f), SceneKit.textured(paintHalo(color), additive = true), "comet_halo")?.let(root::addChild)
        SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.092f, 20, 14), SceneKit.material(color, 0.6f, additive = true), "comet_shell")?.let(root::addChild)
        SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.05f, 16, 12), SceneKit.material(SceneColor.PAPER), "comet_core")?.let(root::addChild)
        // Dashed ring, spun every frame.
        val ringMesh = MeshData()
        for (k in 0 until 6) ringMesh.tube(MeshData.arc(Vec3.ZERO, 0.145f, Vec3.Y, Vec3.X, k * 60f, k * 60f + 36f, 6f), 0.0045f, 6)
        val ring = SceneKit.model(ringMesh, SceneKit.material(SceneColor.PAPER, 0.85f, additive = true), "comet_ring")!!
        root.addChild(ring)
        return Comet(root, ring, phase = seed * 1.7f)
    }

    /**
     * A boxing glove from a few stretched spheres: fist, knuckle bulge, thumb, a white cuff with a
     * stripe, plus a light patch above and a dark one below to stand in for shading (materials are
     * unlit). Forward is -Z; [thumbSide] is +1 when the thumb sits toward +X.
     */
    private fun glove(color: SceneColor, thumbSide: Float): Entity =
        Entity().apply {
            val body = MeshData()
            body.ellipsoid(Vec3(0f, 0f, -0.02f), 0.062f, 0.058f, 0.078f)
            body.ellipsoid(Vec3(0f, 0.012f, -0.072f), 0.056f, 0.046f, 0.042f)
            body.ellipsoid(Vec3(thumbSide * 0.056f, -0.006f, -0.022f), 0.026f, 0.029f, 0.047f)
            SceneKit.model(body, SceneKit.material(color), "glove_body")?.let(::addChild)
            val cuff = MeshData().taper(Vec3(0f, 0f, 0.04f), Vec3(0f, 0f, 0.125f), 0.05f, 0.057f, 16)
            cuff.disc(Vec3(0f, 0f, 0.125f), Vec3.Z, 0.057f, 16)
            SceneKit.model(cuff, SceneKit.material(SceneColor.PAPER), "glove_cuff")?.let(::addChild)
            SceneKit.model(MeshData().taper(Vec3(0f, 0f, 0.072f), Vec3(0f, 0f, 0.09f), 0.0535f, 0.0548f, 16), SceneKit.material(color), "glove_stripe")?.let(::addChild)
            val seam = MeshData.arc(Vec3(0f, 0f, -0.03f), 0.0605f, Vec3.Y, Vec3(thumbSide, 0f, 0f), -70f, 70f, 8f)
            SceneKit.model(MeshData().tube(seam, 0.002f, 5), SceneKit.material(SceneColor.INK, 0.55f), "glove_seam")?.let(::addChild)
            SceneKit.model(MeshData().ellipsoid(Vec3(-thumbSide * 0.014f, 0.043f, -0.05f), 0.032f, 0.012f, 0.04f), SceneKit.material(SceneColor.PAPER, 0.32f), "glove_light")?.let(::addChild)
            SceneKit.model(MeshData().ellipsoid(Vec3(0f, -0.042f, -0.02f), 0.05f, 0.016f, 0.065f), SceneKit.material(SceneColor.INK, 0.38f), "glove_shade")?.let(::addChild)
            SceneKit.model(MeshData().sphere(Vec3(0f, 0f, -0.03f), 0.105f, 24, 16), SceneKit.material(color, 0.14f, additive = true), "glove_glow")?.let(::addChild)
        }

    /** Additive blending adds RGB, so the falloff is painted as brightness. */
    private fun paintHalo(color: SceneColor): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fun rgb(level: Float) = Color.rgb((color.r * 255 * level).toInt(), (color.g * 255 * level).toInt(), (color.b * 255 * level).toInt())
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(size / 2f, size / 2f, size / 2f, intArrayOf(rgb(0.75f), rgb(0.28f), rgb(0.08f), Color.BLACK), floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
            }
        Canvas(bitmap).apply { drawColor(Color.BLACK); drawCircle(size / 2f, size / 2f, size / 2f, paint) }
        return bitmap
    }

    private companion object {
        const val POOL = 5
        const val LANE_M = 0.26f
    }
}
