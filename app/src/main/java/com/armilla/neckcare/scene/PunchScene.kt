package com.armilla.neckcare.scene

import com.armilla.neckcare.domain.usecase.Point3
import com.armilla.neckcare.domain.usecase.PunchExercise
import com.armilla.neckcare.domain.usecase.PunchSide
import com.armilla.neckcare.domain.usecase.PunchSnapshot
import com.armilla.neckcare.domain.usecase.TargetState
import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3

/**
 * 出拳: glowing targets fly in along a left (jade) and a right (amber) lane and are met with a
 * fist. Targets and the two fist lights are pooled entities moved every frame; a hit target swells
 * and is gone, a passed one just shrinks away.
 */
class PunchScene(private val eyeHeightM: Float) : Entity() {
    private class Pooled(val entity: Entity, var id: Int = -1)

    private val pools =
        mapOf(
            PunchSide.LEFT to List(POOL) { Pooled(target(SceneColor.JADE)) },
            PunchSide.RIGHT to List(POOL) { Pooled(target(SceneColor.AMBER)) },
        )
    private val leftFist = fist(SceneColor.JADE)
    private val rightFist = fist(SceneColor.AMBER)

    init {
        pools.values.flatten().forEach {
            it.entity.enabled = false
            addChild(it.entity)
        }
        addChild(leftFist)
        addChild(rightFist)
        // Faint dotted lanes so the eye can find where the next target comes from.
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

    fun update(snap: PunchSnapshot, leftHand: Point3?, rightHand: Point3?) {
        place(leftFist, leftHand)
        place(rightFist, rightHand)
        for ((side, pool) in pools) {
            val wanted = snap.targets.filter { it.side == side }
            pool.forEach { slot -> if (wanted.none { it.id == slot.id }) { slot.id = -1; slot.entity.enabled = false } }
            for (target in wanted) {
                val slot = pool.firstOrNull { it.id == target.id } ?: pool.firstOrNull { it.id == -1 } ?: continue
                slot.id = target.id
                slot.entity.enabled = true
                val fade = (target.stateAge / PunchExercise.FADE_SECONDS).coerceIn(0f, 1f)
                val scale =
                    when (target.state) {
                        TargetState.FLYING -> 1f
                        TargetState.HIT -> 1f + fade * 1.6f
                        TargetState.PASSED -> 1f - fade
                    }.coerceAtLeast(0.01f)
                slot.entity.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(target.position.x, eyeHeightM + target.position.y, target.position.z))
                    setScaleVector(Vector3(scale, scale, scale))
                }
            }
        }
    }

    private fun place(entity: Entity, hand: Point3?) {
        entity.enabled = hand != null
        if (hand != null) entity.components[TransformComponent::class.java]?.setPosition(Vector3(hand.x, eyeHeightM + hand.y, hand.z))
    }

    private fun target(color: SceneColor): Entity =
        Entity().apply {
            SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.17f, 14, 10), SceneKit.material(color, 0.16f, additive = true), "target_halo")?.let(::addChild)
            SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.085f, 18, 12), SceneKit.material(color), "target_core")?.let(::addChild)
        }

    private fun fist(color: SceneColor): Entity =
        Entity().apply {
            SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.09f, 12, 8), SceneKit.material(color, 0.18f, additive = true), "fist_halo")?.let(::addChild)
            SceneKit.model(MeshData().sphere(Vec3.ZERO, 0.04f, 14, 10), SceneKit.material(color, 0.9f), "fist_core")?.let(::addChild)
        }

    private companion object {
        const val POOL = 5
        const val LANE_M = 0.26f
    }
}
