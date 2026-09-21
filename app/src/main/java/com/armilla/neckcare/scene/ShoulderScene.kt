package com.armilla.neckcare.scene

import com.armilla.neckcare.domain.usecase.HandSample
import com.armilla.neckcare.domain.usecase.HandState
import com.armilla.neckcare.domain.usecase.ShoulderSnapshot
import com.armilla.neckcare.scene.SceneKit.toVector3
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 肩部环绕 (PRD §8): one guide ring per arm. The ring centre is 0.55 m from the shoulder along a
 * direction 45° outward from straight ahead, the ring plane is perpendicular to that arm direction,
 * and the ring radius is 0.28 m. Angles run from the bottom of the ring toward the back, which is
 * the direction of backward circling.
 */
class ShoulderScene(private val eyeHeightM: Float, private val radiusM: Float = 0.28f) : Entity() {
    private inner class Ring(val sign: Float, val color: SceneColor) {
        val normal = Vec3(sign * SIN45, 0f, -SIN45)
        val back: Vec3 = Vec3.Y.cross(normal).normalized().let { if (it.z > 0f) it else it * -1f }
        val centre = Vec3(sign * SHOULDER_HALF_WIDTH_M, eyeHeightM - SHOULDER_DROP_M, 0.02f) + normal * REACH_M
        var arc: List<Entity> = emptyList()
        var shownDeg = -1000f
        val hand = dot(17f, 46f, color)
        val pacer = hollow()

        fun point(deg: Float): Vec3 {
            val a = Math.toRadians(deg.toDouble())
            return centre + Vec3.Y * (-cos(a).toFloat() * radiusM) + back * (sin(a).toFloat() * radiusM)
        }

        fun sample(position: Vec3): HandSample {
            val rel = position - centre
            val deg = Math.toDegrees(atan2(rel.dot(back).toDouble(), -rel.dot(Vec3.Y).toDouble())).toFloat()
            return HandSample((deg + 360f) % 360f, rel.dot(normal))
        }

        private fun hollow(): Entity {
            // S-05: hollow ring, radius 10 px, paper-white 2 px stroke.
            val circle = MeshData.arc(Vec3.ZERO, 10f * PX, Vec3.Y, back, 0f, 360f, 15f)
            return SceneKit.model(MeshData().tube(circle, 1f * PX, 6, closed = true), SceneKit.material(SceneColor.PAPER), "pacer")!!
        }
    }

    private val left = Ring(-1f, SceneColor.JADE)
    private val right = Ring(1f, SceneColor.AMBER)
    private var lastDirection = 1

    init {
        listOf(left, right).forEach { ring ->
            // S-02: the outer half brighter (55 %, 2.4 px) than the inner half (30 %, 1.3 px), ticks every 15°.
            val outer = MeshData()
            val inner = MeshData()
            val split = (0..120).map { it * 3f }
            val runs = split.zipWithNext()
            runs.forEach { (a, b) ->
                val mid = ring.point((a + b) / 2)
                val isOuter = abs(mid.x) > abs(ring.centre.x)
                (if (isOuter) outer else inner).tube(listOf(ring.point(a), ring.point(b)), (if (isOuter) 1.2f else 0.65f) * PX, 5)
            }
            for (deg in 0 until 360 step 15) {
                val p = ring.point(deg.toFloat())
                val inward = (ring.centre - p).normalized()
                inner.tube(listOf(p, p + inward * (10f * PX)), 0.6f * PX, 4)
            }
            SceneKit.model(inner, SceneKit.material(SceneColor.PAPER, 0.32f), "guide_inner")?.let(::addChild)
            SceneKit.model(outer, SceneKit.material(SceneColor.PAPER, 0.55f), "guide_outer")?.let(::addChild)
            addChild(ring.hand)
            addChild(ring.pacer)
        }
    }

    /** Converts anchor-local hand positions into ring samples for the exercise. */
    fun samples(leftHand: Vec3?, rightHand: Vec3?): Pair<HandSample?, HandSample?> =
        leftHand?.let(left::sample) to rightHand?.let(right::sample)

    fun update(snap: ShoulderSnapshot, leftAngle: Float?, rightAngle: Float?) {
        if (snap.direction != lastDirection) {
            lastDirection = snap.direction
            listOf(left, right).forEach { it.shownDeg = -1000f }
        }
        draw(left, snap.left, leftAngle, snap)
        draw(right, snap.right, rightAngle, snap)
    }

    private fun draw(ring: Ring, state: HandState, handAngle: Float?, snap: ShoulderSnapshot) {
        val dir = snap.direction.toFloat()
        // S-03: progress arc from the bottom of the ring, in the direction of circling.
        if (abs(state.progressDeg - ring.shownDeg) >= 3f) {
            ring.shownDeg = state.progressDeg
            ring.arc.forEach { it.destroy() }
            val created = ArrayList<Entity>()
            if (state.progressDeg >= 3f) {
                val path = (0..(state.progressDeg / 3f).toInt()).map { ring.point(dir * it * 3f) }
                SceneKit.model(MeshData().tube(path, 8f * PX, 8), SceneKit.material(ring.color, 0.2f, additive = true), "lap_glow")?.let(created::add)
                SceneKit.model(MeshData().tube(path, 4.5f * PX, 8), SceneKit.material(ring.color), "lap_arc")?.let(created::add)
            }
            created.forEach(::addChild)
            ring.arc = created
        }
        // S-04: the hand's place on the ring; a hand lost for long turns mist grey by being hidden.
        val shown = handAngle ?: (dir * state.progressDeg)
        ring.hand.enabled = !state.lostLong
        ring.hand.components[TransformComponent::class.java]?.setPosition(ring.point(shown).toVector3())
        ring.pacer.components[TransformComponent::class.java]?.setPosition(ring.point(dir * snap.pacerDeg).toVector3())
    }

    private fun dot(radiusPx: Float, haloPx: Float, color: SceneColor): Entity =
        Entity().apply {
            SceneKit.model(MeshData().sphere(Vec3.ZERO, haloPx * PX, 14, 10), SceneKit.material(color, 0.18f, additive = true), "hand_halo")?.let(::addChild)
            SceneKit.model(MeshData().sphere(Vec3.ZERO, radiusPx * PX, 16, 12), SceneKit.material(color), "hand_dot")?.let(::addChild)
        }

    private companion object {
        /** Ring radius 0.28 m is drawn about 200 px across on the board: 1 px = 1.4 mm here. */
        const val PX = 0.0014f
        const val SIN45 = 0.70710677f
        const val REACH_M = 0.55f
        const val SHOULDER_HALF_WIDTH_M = 0.19f
        const val SHOULDER_DROP_M = 0.22f
    }
}
