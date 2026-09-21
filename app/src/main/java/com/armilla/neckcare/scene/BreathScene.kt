package com.armilla.neckcare.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.armilla.neckcare.domain.usecase.BreathSnapshot
import com.armilla.neckcare.scene.SceneKit.toVector3
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3

/**
 * 三环呼吸 ("空间冥想" boards): 24 outer and 12 inner rings that share one diameter. At the end
 * of the exhale they lie almost in one plane, a single ring; breathing in fans them out about that
 * diameter until they close into a sphere, 1.5 m across, 2.5 m ahead. Outer rings run from jade
 * through to amber and back, inner rings from paper white to amber; the outer layer turns once in
 * 80 s, the inner, 56 % the size, once in 56 s the other way. A jade glow swells with the inhale,
 * an amber one with the exhale, and a pool of amber light lies on the water below.
 *
 * Tilts are the boards' CSS transforms, rotateX(62deg) rotateZ(-16deg) and rotateX(58deg)
 * rotateZ(28deg), carried into a right-handed frame with +Y up and +Z toward the user.
 */
class BreathScene(centre: Vector3, floorY: Float) : Entity() {
    private class Ring(val entity: Entity, val material: UnlitMaterial, val color: FloatArray, val alpha: Float, val fullAngleRad: Float)

    private val scaled = Entity()
    private val outerSpin = Entity()
    private val innerSpin = Entity()
    private val outer = ArrayList<Ring>()
    private val inner = ArrayList<Ring>()
    private val jadeGlow = glowCard(127, 209, 196)
    private val amberGlow = glowCard(240, 180, 90)
    private val pool = glowCard(240, 180, 90)
    private var clock = 0f

    init {
        components[TransformComponent::class.java]?.setPosition(centre)
        addChild(scaled)
        if (DEBUG_MARKER) {
            SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.12f), SceneKit.material(SceneColor.AMBER), "dbg_root")?.let(::addChild)
            SceneKit.model(MeshData().sphere(Vec3(0.5f, 0f, 0f), 0.08f), SceneKit.material(SceneColor.JADE), "dbg_scaled")?.let(scaled::addChild)
        }

        // Glows sit behind the rings and face the user: 170 % of the sphere's box.
        for ((card, z) in listOf(jadeGlow to -0.06f, amberGlow to -0.05f)) {
            card.first.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0f, z))
            scaled.addChild(card.first)
        }

        val outerTilt = Entity().apply { components[TransformComponent::class.java]?.setQuaternion(tilt(62f, -16f)) }
        val innerTilt = Entity().apply { components[TransformComponent::class.java]?.setQuaternion(tilt(58f, 28f)) }
        scaled.addChild(outerTilt)
        scaled.addChild(innerTilt)
        outerTilt.addChild(outerSpin)
        innerTilt.addChild(innerSpin)

        for (k in 0 until OUTER_COUNT) {
            // Jade at the ends of the fan, amber in the middle (k = 12), as on the board.
            val t = 1f - kotlin.math.abs(k - OUTER_COUNT / 2f) / (OUTER_COUNT / 2f)
            val color = blend(floatArrayOf(127f, 209f, 196f), floatArrayOf(240f, 180f, 90f), t)
            outer += ring(OUTER_RADIUS_M, 0.0026f, color, 0.82f, Math.toRadians(k * 7.5).toFloat()).also { outerSpin.addChild(it.entity) }
        }
        for (k in 0 until INNER_COUNT) {
            val t = 1f - kotlin.math.abs(k - INNER_COUNT / 2f) / (INNER_COUNT / 2f)
            val color = blend(floatArrayOf(239f, 233f, 220f), floatArrayOf(240f, 180f, 90f), t)
            inner += ring(OUTER_RADIUS_M * 0.56f, 0.0021f, color, 0.7f, Math.toRadians(k * 15.0).toFloat()).also { innerSpin.addChild(it.entity) }
        }
        // Where all outer rings cross, at the two ends of their shared diameter, the light gathers.
        for (sign in listOf(1f, -1f)) {
            val at = tilt(62f, -16f).rotateVector(Vector3(0f, sign * OUTER_RADIUS_M, 0f))
            val card = glowCard(239, 233, 220, sizeM = 0.34f, peak = 0.55f)
            card.first.components[TransformComponent::class.java]?.setPosition(Vector3(at.x, at.y, at.z + 0.02f))
            scaled.addChild(card.first)
        }
        // Pool of light on the water under the sphere: a flat card, wider than deep.
        pool.first.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, floorY - centre.y + 0.02f, 0.2f))
            setQuaternion(Quat(Vector3(1f, 0f, 0f), (-Math.PI / 2).toFloat()))
        }
        addChild(pool.first)
    }

    fun update(dt: Float, snap: BreathSnapshot, paused: Boolean) {
        if (!paused) clock += dt
        val s = snap.scale
        scaled.components[TransformComponent::class.java]?.setScaleVector(Vector3(s, s, s))
        outerSpin.components[TransformComponent::class.java]?.setQuaternion(aboutY(clock * TWO_PI / 80f))
        innerSpin.components[TransformComponent::class.java]?.setQuaternion(aboutY(-clock * TWO_PI / 56f))
        for (r in outer + inner) {
            r.entity.components[TransformComponent::class.java]?.setQuaternion(aboutY(r.fullAngleRad * snap.expansion))
            r.material.setBaseColor(Color4(r.color[0], r.color[1], r.color[2], r.alpha * snap.brightness))
        }
        level(jadeGlow.second, snap.jadeGlow * snap.brightness)
        level(amberGlow.second, snap.amberGlow)
        level(pool.second, 0.3f + 0.7f * (snap.scale - 0.56f) / 0.44f)
        val poolWidth = 0.62f + 0.38f * (snap.scale - 0.56f) / 0.44f
        pool.first.components[TransformComponent::class.java]?.setScaleVector(Vector3(poolWidth * 0.95f, 0.62f, 1f))
    }

    private fun level(material: UnlitMaterial, value: Float) {
        val v = value.coerceIn(0f, 1.2f)
        material.setBaseColor(Color4(v, v, v, 1f))
    }

    private fun ring(radius: Float, tube: Float, rgb: FloatArray, alpha: Float, fullAngle: Float): Ring {
        val material =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(Color4(rgb[0], rgb[1], rgb[2], alpha))
                setApplyToneMapping(false)
                setCullingMode(MaterialCullingMode.NONE)
                setDepthWrite(false)
            }
        val path = MeshData.arc(Vec3.ZERO, radius, Vec3.X, Vec3.Y, 0f, 360f, 5f).dropLast(1)
        val mesh = SceneKit.mesh(MeshData().tube(path, tube, 5, closed = true), "breath_ring")
        return Ring(Entity().apply { components.set(ModelComponent(mesh, material)) }, material, rgb, alpha, fullAngle)
    }

    /** A textured ADD card painted as brightness on opaque black: the recipe that glows here. */
    private fun glowCard(r: Int, g: Int, b: Int, sizeM: Float = OUTER_RADIUS_M * 2f * 1.7f, peak: Float = 0.42f): Pair<Entity, UnlitMaterial> {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fun rgb(level: Float) = Color.rgb((r * level).toInt(), (g * level).toInt(), (b * level).toInt())
        Canvas(bitmap).apply {
            drawColor(Color.BLACK)
            drawCircle(
                size / 2f, size / 2f, size / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    // radial-gradient(closest-side, 40 %, 13 % at 46 %, 0 at 74 %)
                    shader = RadialGradient(size / 2f, size / 2f, size / 2f, intArrayOf(rgb(peak), rgb(peak * 0.32f), Color.BLACK, Color.BLACK), floatArrayOf(0f, 0.46f, 0.74f, 1f), Shader.TileMode.CLAMP)
                },
            )
        }
        val material = SceneKit.textured(bitmap, additive = true)
        val mesh = MeshData().quad(Vec3.ZERO, Vec3.X * (sizeM / 2f), Vec3.Y * (sizeM / 2f))
        return SceneKit.model(mesh, material, "breath_glow")!! to material
    }

    private fun aboutY(rad: Float) = Quat(Vector3(0f, 1f, 0f), rad)

    /** CSS rotateX(x) rotateZ(z): z first, then x; CSS turns the other way round both axes. */
    private fun tilt(cssXDeg: Float, cssZDeg: Float): Quat =
        Quat(Vector3(1f, 0f, 0f), Math.toRadians(-cssXDeg.toDouble()).toFloat()) *
            Quat(Vector3(0f, 0f, 1f), Math.toRadians(-cssZDeg.toDouble()).toFloat())

    private fun blend(a: FloatArray, b: FloatArray, t: Float) = FloatArray(3) { (a[it] + (b[it] - a[it]) * t) / 255f }

    companion object {
        var DEBUG_MARKER = false

        /** Full breath: 1.5 m across. */
        const val OUTER_RADIUS_M = 0.75f
        private const val OUTER_COUNT = 24
        private const val INNER_COUNT = 12
        private const val TWO_PI = 6.2831855f
    }
}
