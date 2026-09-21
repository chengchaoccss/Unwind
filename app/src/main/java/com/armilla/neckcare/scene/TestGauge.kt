package com.armilla.neckcare.scene

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.scene.SceneKit.toVector3
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The measuring scale of the mobility test, 2.5 m from the head (PRD §6).
 *  - rotation: a horizontal ring 8° below the line of sight (T-02);
 *  - flexion / extension: a vertical arc 8° to the right of straight ahead;
 *  - lateral bending: a dial straight ahead, 16° across, with a line that tilts with the head.
 * Ticks every 5° (short) and 10° (long, numbered) are painted into one texture per scale.
 * Artboard pixels at 2.5 m are 3.125 mm.
 */
class TestGauge(private val eyeHeightM: Float, private val numerals: Typeface) : Entity() {
    private val eye = Vec3(0f, eyeHeightM, 0f)
    private var axis: Axis? = null
    private var direction: Direction? = null
    private var scale: Entity? = null
    private var lastMark: Entity? = null
    private var sweep: List<Entity> = emptyList()
    private var needle: Entity? = null
    private var shownAngle = Float.NaN

    /** Shows the scale for [direction] and, when known, a dashed mark at the previous reading. */
    fun configure(direction: Direction, lastAngleDeg: Int?) {
        this.direction = direction
        if (axis != direction.axis) {
            axis = direction.axis
            scale?.destroy()
            needle?.destroy()
            needle = null
            scale = buildScale(direction.axis)?.also(::addChild)
            if (direction.axis == Axis.LATERAL) needle = buildNeedle().also(::addChild)
        }
        lastMark?.destroy()
        lastMark = lastAngleDeg?.let { buildLastMark(direction, it) }?.also(::addChild)
        shownAngle = Float.NaN
        setAngle(0f, 0f)
    }

    /** Redraws the swept band and the current-position marker when the angle moved visibly. */
    fun setAngle(towardDeg: Float, rollDeg: Float) {
        val d = direction ?: return
        needle?.components?.get(TransformComponent::class.java)
            ?.setQuaternion(Quat(Vector3(0f, 0f, 1f), Math.toRadians(-rollDeg.toDouble()).toFloat()))
        val clamped = towardDeg.coerceIn(0f, 95f)
        if (!shownAngle.isNaN() && abs(clamped - shownAngle) < 0.5f) return
        shownAngle = clamped
        sweep.forEach { it.destroy() }
        val color = colorOf(d.side)
        val path = (0..maxOf(1, (clamped / 1.5f).toInt())).map { i ->
            point(d.axis, d.sign * clamped * i / maxOf(1, (clamped / 1.5f).toInt()))
        }
        val created = ArrayList<Entity>()
        if (clamped >= 0.5f) {
            SceneKit.model(MeshData().tube(path, 6.5f * PX, 8), SceneKit.material(color, 0.20f, additive = true), "sweep_glow")?.let(created::add)
            SceneKit.model(MeshData().tube(path, 3.5f * PX, 8), SceneKit.material(color), "sweep")?.let(created::add)
        }
        // T-04: amber dot with a 22 % halo at the current position, and a thin line to the reticle.
        val here = point(d.axis, d.sign * clamped)
        val marker = MeshData().sphere(here, 8f * PX, 14, 10)
        SceneKit.model(MeshData().sphere(here, 16f * PX, 14, 10), SceneKit.material(SceneColor.AMBER, 0.22f, additive = true), "marker_halo")?.let(created::add)
        SceneKit.model(marker, SceneKit.material(SceneColor.AMBER), "marker")?.let(created::add)
        if (d.axis == Axis.ROTATION) {
            val reticle = directionPoint(d.sign * clamped, 0f)
            SceneKit.model(MeshData().tube(listOf(here, reticle), 0.7f * PX, 5), SceneKit.material(SceneColor.AMBER, 0.6f), "marker_line")?.let(created::add)
        }
        created.forEach(::addChild)
        sweep = created
    }

    /** Position on the active scale, anchor-local, for placing the "上次" tag. */
    fun lastTagPosition(direction: Direction, angleDeg: Int): Vector3 {
        val p = point(direction.axis, (direction.sign * angleDeg).toFloat())
        return when (direction.axis) {
            Axis.ROTATION -> (p + Vec3(0f, 0.30f, 0f)).toVector3()
            Axis.FLEXION -> (p + Vec3(0.42f, 0f, 0f)).toVector3()
            Axis.LATERAL -> (p + (p - dialCentre()).normalized() * 0.22f).toVector3()
        }
    }

    private fun point(axis: Axis, signedDeg: Float): Vec3 =
        when (axis) {
            Axis.ROTATION -> directionPoint(signedDeg, RING_ELEVATION_DEG)
            Axis.FLEXION -> directionPoint(ARC_AZIMUTH_DEG, signedDeg)
            Axis.LATERAL -> {
                val r = Math.toRadians(signedDeg.toDouble())
                dialCentre() + Vec3(sin(r).toFloat(), cos(r).toFloat(), 0f) * DIAL_RADIUS_M
            }
        }

    private fun directionPoint(azimuthDeg: Float, elevationDeg: Float): Vec3 {
        val az = Math.toRadians(azimuthDeg.toDouble())
        val el = Math.toRadians(elevationDeg.toDouble())
        return eye + Vec3((cos(el) * sin(az)).toFloat(), sin(el).toFloat(), (-cos(el) * cos(az)).toFloat()) * RADIUS_M
    }

    private fun dialCentre() = directionPoint(0f, 0f)

    private fun buildScale(axis: Axis): Entity? =
        when (axis) {
            Axis.ROTATION -> {
                val degrees = (-RANGE_DEG..RANGE_DEG)
                val centres = degrees.map { directionPoint(it.toFloat(), RING_ELEVATION_DEG) }
                val mesh = MeshData().ribbon(centres, centres.map { Vec3.Y }, BAND_HALF_WIDTH_M)
                SceneKit.model(mesh, SceneKit.textured(paintBand(vertical = false)), "scale_ring")
            }
            Axis.FLEXION -> {
                val degrees = (-RANGE_DEG..RANGE_DEG)
                val centres = degrees.map { directionPoint(ARC_AZIMUTH_DEG, it.toFloat()) }
                val az = Math.toRadians(ARC_AZIMUTH_DEG.toDouble())
                // Ticks point left, toward the reticle; numerals sit to the right of the arc.
                val left = Vec3(-cos(az).toFloat(), 0f, -sin(az).toFloat())
                val mesh = MeshData().ribbon(centres, centres.map { left }, BAND_HALF_WIDTH_M)
                SceneKit.model(mesh, SceneKit.textured(paintBand(vertical = true)), "scale_arc")
            }
            Axis.LATERAL -> {
                val half = DIAL_RADIUS_M * DIAL_TEXTURE_MARGIN
                val mesh = MeshData().quad(dialCentre(), Vec3.X * half, Vec3.Y * half)
                SceneKit.model(mesh, SceneKit.textured(paintDial()), "scale_dial")
            }
        }

    /** The line across the dial that tilts with the head. */
    private fun buildNeedle(): Entity {
        val half = DIAL_RADIUS_M * 0.82f
        val mesh = MeshData().tube(listOf(Vec3(-half, 0f, 0f), Vec3(half, 0f, 0f)), 1.1f * PX, 6)
        return SceneKit.model(mesh, SceneKit.material(SceneColor.PAPER, 0.85f), "dial_needle")!!.apply {
            components[TransformComponent::class.java]?.setPosition(dialCentre().toVector3())
        }
    }

    /** T-05: a dashed paper-white tick (1.6 px, 4/5 dash) across the scale at the last reading. */
    private fun buildLastMark(direction: Direction, angleDeg: Int): Entity? {
        val p = point(direction.axis, (direction.sign * angleDeg).toFloat())
        val along =
            when (direction.axis) {
                Axis.ROTATION -> Vec3.Y
                Axis.FLEXION -> Vec3.X
                Axis.LATERAL -> (p - dialCentre()).normalized()
            }
        val mesh = MeshData()
        var t = -28f
        while (t < 28f) {
            mesh.tube(listOf(p + along * (t * PX), p + along * ((t + 4f) * PX)), 0.8f * PX, 5)
            t += 9f
        }
        return SceneKit.model(mesh, SceneKit.material(SceneColor.PAPER, 0.9f), "last_mark")
    }

    /**
     * Ring line 1.6 px at 50 %, short ticks 1.2 px at 40 % every 5°, long ticks 1.6 px at 75 %
     * every 10° with Instrument Serif 26 px numerals in mist, and a 2.4 px mark at 0°.
     */
    private fun paintBand(vertical: Boolean): Bitmap {
        val pxPerDeg = BAND_TEXTURE_WIDTH / (2f * RANGE_DEG)
        // One artboard pixel in texture pixels: 3.125 mm against 43.63 mm per degree at 2.5 m.
        val u = pxPerDeg * (PX / (RADIUS_M * tan(Math.toRadians(1.0)).toFloat()))
        val height = BAND_TEXTURE_HEIGHT
        val bitmap = Bitmap.createBitmap(BAND_TEXTURE_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val lineY = height * 0.46f
        val paper = { alpha: Float -> Color.argb((alpha * 255).toInt(), 0xEF, 0xE9, 0xDC) }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        line.color = paper(0.5f); line.strokeWidth = 1.6f * u
        canvas.drawLine(0f, lineY, BAND_TEXTURE_WIDTH.toFloat(), lineY, line)
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = numerals
                textSize = 26f * u
                color = Color.argb(255, 0xA9, 0xB5, 0xB4)
                textAlign = Paint.Align.CENTER
            }
        for (deg in -RANGE_DEG..RANGE_DEG step 5) {
            val x = (deg + RANGE_DEG) * pxPerDeg
            when {
                deg == 0 -> { line.color = paper(1f); line.strokeWidth = 2.4f * u; canvas.drawLine(x, lineY, x, lineY - 46f * u, line) }
                deg % 10 == 0 -> { line.color = paper(0.75f); line.strokeWidth = 1.6f * u; canvas.drawLine(x, lineY, x, lineY - 30f * u, line) }
                else -> { line.color = paper(0.4f); line.strokeWidth = 1.2f * u; canvas.drawLine(x, lineY, x, lineY - 16f * u, line) }
            }
            if (deg % 10 == 0) {
                val label = abs(deg).toString()
                if (vertical) {
                    // The ribbon runs bottom to top, so turn numerals back upright.
                    canvas.save()
                    canvas.rotate(90f, x, lineY + 34f * u)
                    canvas.drawText(label, x, lineY + 34f * u + 9f * u, text)
                    canvas.restore()
                } else canvas.drawText(label, x, lineY + 44f * u, text)
            }
        }
        return bitmap
    }

    private fun paintDial(): Bitmap {
        val size = DIAL_TEXTURE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val radius = c / DIAL_TEXTURE_MARGIN
        val u = radius / (DIAL_RADIUS_M / PX)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        paint.color = Color.argb(128, 0xEF, 0xE9, 0xDC); paint.strokeWidth = 1.6f * u
        canvas.drawCircle(c, c, radius, paint)
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = numerals; textSize = 22f * u; textAlign = Paint.Align.CENTER
                color = Color.argb(255, 0xA9, 0xB5, 0xB4)
            }
        for (deg in -90..90 step 5) {
            val a = Math.toRadians(deg.toDouble())
            val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
            val length = when { deg == 0 -> 30f; deg % 10 == 0 -> 20f; else -> 11f } * u
            paint.color = Color.argb(if (deg == 0) 255 else if (deg % 10 == 0) 190 else 102, 0xEF, 0xE9, 0xDC)
            paint.strokeWidth = (if (deg == 0) 2.4f else if (deg % 10 == 0) 1.6f else 1.2f) * u
            canvas.drawLine(c + dx * radius, c + dy * radius, c + dx * (radius - length), c + dy * (radius - length), paint)
            if (deg % 20 == 0 && deg != 0) {
                canvas.drawText(abs(deg).toString(), c + dx * (radius + 24f * u), c + dy * (radius + 24f * u) + 8f * u, text)
            }
        }
        return bitmap
    }

    private fun colorOf(side: Side) =
        when (side) {
            Side.LEFT -> SceneColor.JADE
            Side.RIGHT -> SceneColor.AMBER
            Side.CENTER -> SceneColor.PAPER
        }

    companion object {
        const val RADIUS_M = 2.5f
        const val RING_ELEVATION_DEG = -8f
        const val ARC_AZIMUTH_DEG = 8f
        private const val PX = 0.003125f
        private const val RANGE_DEG = 100
        private const val BAND_HALF_WIDTH_M = 0.17f
        private const val BAND_TEXTURE_WIDTH = 4096
        private const val BAND_TEXTURE_HEIGHT = 160
        private const val DIAL_TEXTURE = 1024
        private const val DIAL_TEXTURE_MARGIN = 1.35f
        val DIAL_RADIUS_M = RADIUS_M * tan(Math.toRadians(8.0)).toFloat()
    }
}
