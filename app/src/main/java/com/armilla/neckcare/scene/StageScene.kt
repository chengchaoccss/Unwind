package com.armilla.neckcare.scene

import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.scene.environment.SkyDome
import com.armilla.neckcare.scene.environment.SkyPanorama
import com.armilla.neckcare.ui.stage.LobbyPanels
import com.armilla.neckcare.ui.stage.PanelSpec
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns every entity the app creates. Compose only hands it state; placement, the armillary and
 * the environment live here so per-frame work never goes through recomposition.
 */
class StageScene(private val density: androidx.compose.ui.unit.Density) {
    val anchor = StageAnchor()
    private var sky: SkyDome? = null
    private var armillary: Armillary? = null
    private var shownAngles: Map<Direction, Int>? = null
    private var ready = false

    fun calibrate(headPosition: Vector3, headRotation: Quat) {
        anchor.calibrate(headPosition, headRotation)
        sky?.setEyeHeight(anchor.eyeHeightM)
    }

    /** Sky, dais and the armillary. The panorama is painted off the main thread. */
    suspend fun buildEnvironment() {
        val bitmap = withContext(Dispatchers.Default) { SkyPanorama.render() }
        sky = SkyDome(TextureResource.create(bitmap)).also {
            it.setEyeHeight(anchor.eyeHeightM)
            anchor.addChild(it)
        }
        anchor.addChild(Dais())
        armillary =
            Armillary().also {
                it.components[TransformComponent::class.java]?.setPosition(anchor.board(2.5f, 800f, ARMILLARY_BOARD_Y))
                anchor.addChild(it)
            }
        ready = true
    }

    /** Parents each panel entity to the anchor at the pose its spec describes. */
    fun place(specs: List<PanelSpec>, entityOf: (Any) -> Entity?) {
        specs.forEach { spec ->
            val position =
                spec.boardCenterPx?.let { anchor.board(spec.distanceM, it.first, it.second) }
                    ?: anchor.polar(spec.distanceM, spec.azimuthDeg, spec.elevationDeg)
            entityOf(spec.id)?.let { place(it, spec, position) }
        }
    }

    private fun place(entity: Entity, spec: PanelSpec, position: Vector3) {
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(position)
            setQuaternion(StageAnchor.yaw(spec.yawDeg) * StageAnchor.pitch(spec.pitchDeg))
            val scale = spec.entityScale(density)
            setScaleVector(Vector3(scale, scale, scale))
        }
        if (entity.getParent() == null) anchor.addChild(entity)
    }

    /** Lobby content: arcs for the last measurement and a tag at the end of each arc. */
    fun showLobby(angles: Map<Direction, Int>, entityOf: (Any) -> Entity?) {
        val model = armillary ?: return
        if (!ready || angles == shownAngles) return
        shownAngles = angles
        model.show(angles)
        val centre = anchor.board(2.5f, 800f, ARMILLARY_BOARD_Y)
        Direction.entries.forEach { direction ->
            val spec = LobbyPanels.tag(direction.key)
            val entity = entityOf(spec.id) ?: return@forEach
            // Tag centres as drawn on the lobby board, in artboard px from the armillary centre.
            val (dx, dy) = TAG_SLOTS.getValue(direction)
            val m = 0.003125f
            // Slightly in front of the rings so a ring never cuts through a label.
            place(entity, spec, Vector3(centre.x + dx * m, centre.y - dy * m, centre.z + TAG_LIFT_M))
            entity.enabled = angles[direction] != null
        }
    }

    fun destroy() {
        anchor.destroy()
    }

    private companion object {
        /** Armillary centre on the lobby board. */
        const val ARMILLARY_BOARD_Y = 392f
        const val TAG_LIFT_M = 0.05f
        val TAG_SLOTS =
            mapOf(
                Direction.EXTENSION to (-25f to -182f),
                Direction.LEFT_BEND to (-175f to -122f),
                Direction.RIGHT_BEND to (180f to -137f),
                Direction.LEFT_ROTATION to (-230f to -2f),
                Direction.RIGHT_ROTATION to (188f to -20f),
                Direction.FLEXION to (-50f to 196f),
            )
    }
}
