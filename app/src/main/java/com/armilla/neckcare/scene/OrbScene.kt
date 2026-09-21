package com.armilla.neckcare.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.armilla.neckcare.domain.usecase.GazePoint
import com.armilla.neckcare.domain.usecase.OrbPath
import com.armilla.neckcare.domain.usecase.OrbSnapshot
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 视线接光球 (PRD §7) on the 2.5 m sphere around the head: the orb with its breathing halo and
 * catch ring, the amber trail behind it, the dotted route and the next three catch points ahead,
 * and the jade dotted motion boundary. Artboard pixels at 2.5 m are 3.125 mm.
 */
class OrbScene(private val eyeHeightM: Float, private val path: OrbPath) : Entity() {
    /** Sits at the eyes and turns toward the orb; its children live at (0, 0, -2.5). */
    val pivot = Entity()
    private val haloMaterial: UnlitMaterial
    private var progressArc: Entity? = null
    private var shownProgress = -1
    private var trail: List<Entity> = emptyList()
    private var ahead: List<Entity> = emptyList()
    private var shownSample = -100
    private var shownNext = -1
    private var breath = 0f
    private var flash = 0f

    init {
        pivot.components[TransformComponent::class.java]?.setPosition(Vector3(0f, eyeHeightM, 0f))
        addChild(pivot)
        val centre = Vec3(0f, 0f, -RADIUS_M)

        // O-02: core 30 px with a #fff7e2 -> #f8cd82 -> #f0b45a gradient, halo 190 px fading to 0.
        haloMaterial = SceneKit.textured(paintHalo(), additive = true)
        SceneKit.model(MeshData().quad(centre, Vec3.X * (190f * PX), Vec3.Y * (190f * PX)), haloMaterial, "orb_halo")?.let(pivot::addChild)
        SceneKit.model(
            MeshData().quad(centre + Vec3(0f, 0f, 0.01f), Vec3.X * (34f * PX), Vec3.Y * (34f * PX)),
            SceneKit.textured(paintCore()),
            "orb_core",
        )?.let(pivot::addChild)

        // O-03: catch ring, radius 66 px, paper white 30 %, 2 px.
        val ring = MeshData().tube(MeshData.arc(centre, 66f * PX, Vec3.Y, Vec3.X, 0f, 360f, 6f), 1f * PX, 6, closed = true)
        SceneKit.model(ring, SceneKit.material(SceneColor.PAPER, 0.30f), "catch_ring")?.let(pivot::addChild)

        buildBoundary()
    }

    /** Anchor-local position of a gaze direction on the 2.5 m sphere. */
    fun pointAt(gaze: GazePoint): Vec3 {
        val az = Math.toRadians(gaze.azimuthDeg.toDouble())
        val el = Math.toRadians(gaze.elevationDeg.toDouble())
        return Vec3(0f, eyeHeightM, 0f) +
            Vec3((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (-cos(el) * cos(az)).toFloat()) * RADIUS_M
    }

    fun update(dt: Float, snap: OrbSnapshot) {
        val p = snap.position
        pivot.components[TransformComponent::class.java]?.setQuaternion(
            StageAnchor.yaw(-p.azimuthDeg) * StageAnchor.pitch(p.elevationDeg)
        )
        // Halo breathes over 4 s between 0.75 and 1; a caught orb flashes once; a waiting orb pulses.
        breath += dt * (if (snap.waiting) 2.2f else 1f)
        if (snap.justCaught) flash = 1f
        flash = (flash - dt * 2.5f).coerceAtLeast(0f)
        val level = (0.875f + 0.125f * sin(breath * 2f * PI.toFloat() / 4f) + flash * 0.6f).coerceAtMost(1.6f)
        haloMaterial.setBaseColor(Color4(level, level, level, 1f))

        val progress = (snap.catchProgress * 36).toInt()
        if (progress != shownProgress) {
            shownProgress = progress
            progressArc?.destroy()
            progressArc =
                if (progress == 0) null
                else {
                    // Amber 4 px arc from 12 o'clock, clockwise as the user sees it.
                    val arc = MeshData.arc(Vec3(0f, 0f, -RADIUS_M + 0.005f), 66f * PX, Vec3.Y, Vec3.X, 0f, 360f * progress / 36f, 5f)
                    SceneKit.model(MeshData().tube(arc, 2f * PX, 8), SceneKit.material(SceneColor.AMBER), "catch_progress")?.also(pivot::addChild)
                }
        }
        if (kotlin.math.abs(snap.sampleIndex - shownSample) >= 2 || snap.nextCatchOrdinal != shownNext) {
            shownSample = snap.sampleIndex
            shownNext = snap.nextCatchOrdinal
            rebuildRoute(snap)
        }
    }

    private fun rebuildRoute(snap: OrbSnapshot) {
        (trail + ahead).forEach { it.destroy() }
        val n = path.samples.size
        val created = ArrayList<Entity>()

        // O-05: amber trail fading in toward the orb; three steps stand in for the gradient.
        val behind = (0..TRAIL_SAMPLES).map { pointAt(path.samples[((snap.sampleIndex - TRAIL_SAMPLES + it) % n + n) % n]) } + pointAt(snap.position)
        val third = behind.size / 3
        listOf(0.2f, 0.5f, 0.85f).forEachIndexed { i, alpha ->
            val part = behind.subList(i * third, if (i == 2) behind.size else (i + 1) * third + 1)
            if (part.size >= 2) {
                SceneKit.model(MeshData().tube(part, 7f * PX, 6), SceneKit.material(SceneColor.AMBER, alpha * 0.2f, additive = true), "trail_glow")?.let(created::add)
                SceneKit.model(MeshData().tube(part, 1.75f * PX, 6), SceneKit.material(SceneColor.AMBER, alpha), "trail")?.let(created::add)
            }
        }
        trail = created.toList()

        // O-06: paper-white dots 4 px across, 17 px apart, fading from 75 % to 12 % with distance.
        val near = MeshData()
        val far = MeshData()
        var travelled = 0f
        var nextDot = 17f * PX * 4
        var previous = pointAt(snap.position)
        for (k in 1..AHEAD_SAMPLES) {
            val point = pointAt(path.samples[(snap.sampleIndex + k) % n])
            travelled += (point - previous).length()
            previous = point
            if (travelled >= nextDot) {
                nextDot += 17f * PX
                (if (k < AHEAD_SAMPLES / 2) near else far).sphere(point, 2f * PX, 6, 4)
            }
        }
        val aheadEntities = ArrayList<Entity>()
        SceneKit.model(near, SceneKit.material(SceneColor.PAPER, 0.75f), "route_near")?.let(aheadEntities::add)
        SceneKit.model(far, SceneKit.material(SceneColor.PAPER, 0.3f), "route_far")?.let(aheadEntities::add)

        // O-07: the next three catch points as hollow rings, nearer ones larger and brighter.
        listOf(15f to 0.75f, 11f to 0.55f, 8f to 0.4f).forEachIndexed { i, (radiusPx, alpha) ->
            val gaze = path.catchPoints[(snap.nextCatchOrdinal + i) % path.catchPoints.size]
            val centre = pointAt(gaze)
            val toEye = (Vec3(0f, eyeHeightM, 0f) - centre).normalized()
            val right = Vec3.Y.cross(toEye).normalized()
            val up = toEye.cross(right)
            val ring = MeshData().tube(MeshData.arc(centre, radiusPx * PX, up, right, 0f, 360f, 12f), 0.7f * PX, 5, closed = true)
            SceneKit.model(ring, SceneKit.material(SceneColor.PAPER, alpha), "catch_preview")?.let(aheadEntities::add)
        }
        ahead = aheadEntities
        (trail + ahead).forEach(::addChild)
    }

    /** O-08: jade dots along the ellipse of the motion boundary, 70 %. */
    private fun buildBoundary() {
        val dots = MeshData()
        val b = path.boundary
        for (deg in 0 until 360 step 2) {
            val a = Math.toRadians(deg.toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            dots.sphere(pointAt(GazePoint(c * (if (c < 0) b.left else b.right), s * (if (s < 0) b.down else b.up))), 1.6f * PX, 6, 4)
        }
        SceneKit.model(dots, SceneKit.material(SceneColor.JADE, 0.7f), "motion_boundary")?.let(::addChild)
    }

    private fun paintHalo(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        size / 2f, size / 2f, size / 2f,
                        // Additive blending adds RGB, so the falloff is painted as brightness:
                        // amber at 50 % in the centre, fading to nothing at 190 px.
                        intArrayOf(Color.rgb(0x78, 0x5A, 0x2D), Color.rgb(0x2B, 0x20, 0x10), Color.rgb(0, 0, 0)),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun paintCore(): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val r = size / 2f * 30f / 34f
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        size * 0.42f, size * 0.4f, r * 1.15f,
                        intArrayOf(Color.parseColor("#fff7e2"), Color.parseColor("#f8cd82"), Color.parseColor("#f0b45a")),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        Canvas(bitmap).drawCircle(size / 2f, size / 2f, r, paint)
        return bitmap
    }

    /** Anchor-local spot for the "已接住" tag: left of the orb, facing the user. */
    fun countTagLocal(): Vector3 = Vector3(-150f * PX, -4f * PX, -RADIUS_M)

    /** Where the boundary caption sits: on the lower left of the boundary ellipse. */
    fun boundaryTagPosition(): Vector3 {
        val p = pointAt(GazePoint(-path.boundary.left * 0.86f, -path.boundary.down * 0.62f))
        return Vector3(p.x, p.y, p.z)
    }

    fun boundaryTagYawDeg(): Float = path.boundary.left * 0.86f

    @Suppress("unused")
    private fun identity() = Quat.identity()

    private companion object {
        const val RADIUS_M = 2.5f
        const val PX = 0.003125f
        const val TRAIL_SAMPLES = 18
        const val AHEAD_SAMPLES = 44
    }
}
