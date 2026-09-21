package com.armilla.neckcare.scene

import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.scene.SceneKit.toVector3
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * 三环仪: three rings for the three rotation axes of the neck, with a bright arc from each axis'
 * zero to the measured angle. The model faces the same way as the user (its back to them), so
 * left is left. It is shown yawed 24° and seen 15° from above so all three rings read.
 *
 * Sizes are artboard pixels at 2.5 m (1 px = 3.125 mm) times [lineScale].
 */
class Armillary(private val radiusM: Float = 0.47f, private val lineScale: Float = 1f) : Entity() {
    private val px = 0.003125f * lineScale
    private var arcs: List<Entity> = emptyList()

    init {
        buildRings()
    }

    /** Replaces the bright arcs. Pass an empty map for the first-run state (rings only). */
    fun show(angles: Map<Direction, Int>) {
        arcs.forEach { it.destroy() }
        val created = ArrayList<Entity>()
        for (side in Side.entries) {
            val core = MeshData()
            val glow = MeshData()
            Direction.entries.filter { it.side == side }.forEach { direction ->
                val angle = angles[direction] ?: return@forEach
                val path = arcPath(direction, angle.toFloat())
                core.tube(path, 2.5f * px, sides = 10)
                core.sphere(path.last(), 5.5f * px)
                glow.tube(path, 6.5f * px, sides = 10)
            }
            val color = colorOf(side)
            SceneKit.model(glow, SceneKit.material(color, alpha = 0.22f, additive = true), "arc_glow")?.let(created::add)
            SceneKit.model(core, SceneKit.material(color), "arc_core")?.let(created::add)
        }
        created.forEach(::addChild)
        arcs = created
    }

    /** Where the reading tag of [direction] goes: just outside the arc's end point, local space. */
    fun tagAnchor(direction: Direction, angleDeg: Int?): Vector3 {
        val end = display(pointOn(direction, (angleDeg ?: DEFAULT_TAG_ANGLE).toFloat(), radiusM * TAG_RADIUS_FACTOR))
        return end.toVector3()
    }

    /** End point of the arc itself, for the leader line of a tag that is pushed away. */
    fun arcEnd(direction: Direction, angleDeg: Int): Vector3 =
        display(pointOn(direction, angleDeg.toFloat(), radiusM)).toVector3()

    private fun buildRings() {
        val near = MeshData()
        val far = MeshData()
        for (axis in Axis.entries) {
            val (zero, positive) = planeOf(axis)
            // Ring line, split where it passes behind the centre as seen by the user.
            val points = MeshData.arc(Vec3.ZERO, radiusM, zero, positive, 0f, 360f, stepDeg = 3f).map(::display)
            var run = ArrayList<Vec3>()
            var runIsNear = points.first().z >= 0f
            for (p in points) {
                val isNear = p.z >= 0f
                if (isNear != runIsNear && run.size > 1) {
                    run.add(p)
                    addRun(if (runIsNear) near else far, run, runIsNear)
                    run = arrayListOf(p)
                    runIsNear = isNear
                } else run.add(p)
            }
            addRun(if (runIsNear) near else far, run, runIsNear)

            // Ticks: short every 15°, long every 45°, pointing inward in the ring's plane.
            for (deg in 0 until 360 step 15) {
                val length = (if (deg % 45 == 0) 11f else 6f) * px
                val a = Math.toRadians(deg.toDouble())
                val dir = zero * cos(a).toFloat() + positive * sin(a).toFloat()
                val outer = display(dir * radiusM)
                val inner = display(dir * (radiusM - length))
                (if (outer.z >= 0f) near else far).tube(listOf(outer, inner), 0.5f * px, sides = 5)
            }
        }
        // Dashed guides from the head toward "front" and "up".
        for (dir in listOf(FORWARD, UP)) {
            var t = 0.16f
            while (t < 0.98f) {
                far.tube(listOf(display(dir * (radiusM * t)), display(dir * (radiusM * (t + 0.035f)))), 0.45f * px, sides = 5)
                t += 0.07f
            }
        }
        SceneKit.model(far, SceneKit.material(SceneColor.PAPER, alpha = 0.24f), "armillary_far")?.let(::addChild)
        SceneKit.model(near, SceneKit.material(SceneColor.PAPER, alpha = 0.62f), "armillary_near")?.let(::addChild)
        val head = MeshData().sphere(Vec3.ZERO, radiusM * 0.115f, 24, 16)
        SceneKit.model(head, SceneKit.material(SceneColor.HEAD, alpha = 0.92f), "armillary_head")?.let(::addChild)
    }

    private fun addRun(target: MeshData, run: List<Vec3>, isNear: Boolean) {
        if (run.size < 2) return
        target.tube(run, (if (isNear) 0.85f else 0.6f) * px, sides = 6)
    }

    private fun arcPath(direction: Direction, angleDeg: Float): List<Vec3> {
        val (zero, positive) = planeOf(direction.axis)
        return MeshData.arc(Vec3.ZERO, radiusM, zero, positive, 0f, direction.sign * angleDeg, stepDeg = 2f)
            .map(::display)
    }

    private fun pointOn(direction: Direction, angleDeg: Float, radius: Float): Vec3 {
        val (zero, positive) = planeOf(direction.axis)
        val a = Math.toRadians((direction.sign * angleDeg).toDouble())
        return zero * (radius * cos(a).toFloat()) + positive * (radius * sin(a).toFloat())
    }

    /** Each ring's zero direction and the direction of its positive angles (PRD §11 signs). */
    private fun planeOf(axis: Axis): Pair<Vec3, Vec3> =
        when (axis) {
            Axis.ROTATION -> FORWARD to RIGHT // right rotation positive
            Axis.FLEXION -> FORWARD to UP // extension positive
            Axis.LATERAL -> UP to RIGHT // right bend positive
        }

    private fun colorOf(side: Side) =
        when (side) {
            Side.LEFT -> SceneColor.JADE
            Side.RIGHT -> SceneColor.AMBER
            Side.CENTER -> SceneColor.PAPER
        }

    /** Model space to display space: yaw 24° to the left, then tip the top 15° toward the user. */
    private fun display(p: Vec3): Vec3 {
        val yawed = MeshData.rotate(p, Vec3.Y, YAW_RAD)
        return MeshData.rotate(yawed, Vec3.X, PITCH_RAD)
    }

    companion object {
        private val FORWARD = Vec3(0f, 0f, -1f)
        private val UP = Vec3.Y
        private val RIGHT = Vec3.X
        private val YAW_RAD = Math.toRadians(24.0).toFloat()
        private val PITCH_RAD = Math.toRadians(15.0).toFloat()
        private const val TAG_RADIUS_FACTOR = 1.32f
        private const val DEFAULT_TAG_ANGLE = 60
    }
}
