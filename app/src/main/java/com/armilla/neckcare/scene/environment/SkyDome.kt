package com.armilla.neckcare.scene.environment

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/**
 * The world-fixed environment: a large inward-facing sphere carrying [SkyPanorama]. Without a sky
 * a Full stage renders black. Unlit, so it needs no image-based lighting of its own.
 */
class SkyDome(texture: TextureResource) : Entity() {
    init {
        val material =
            UnlitMaterial.create().apply {
                setBaseColorTexture(texture)
                // We look at the sphere from inside, so drop the faces that point outward.
                setCullingMode(MaterialCullingMode.FRONT)
                setDepthWrite(false)
                setApplyToneMapping(false)
            }
        components.set(ModelComponent(MeshResource.createSphere(RADIUS_M), material))
        components[TransformComponent::class.java]?.apply {
            // Horizon at eye height: every height in the design is relative to the line of sight.
            setPosition(Vector3(0f, DEFAULT_EYE_HEIGHT_M, 0f))
            setEulerAngles(EulerAngles(0f, YAW_OFFSET_DEG, 0f))
        }
    }

    fun setEyeHeight(eyeHeightM: Float) {
        components[TransformComponent::class.java]?.setPosition(Vector3(0f, eyeHeightM, 0f))
    }

    companion object {
        /** Environment sits "50 m and beyond" in the spatial layout board. */
        const val RADIUS_M = 60f
        const val DEFAULT_EYE_HEIGHT_M = 1.6f

        /** Turns the sphere so the middle of the panorama lands straight ahead (-Z). Device-tuned. */
        var YAW_OFFSET_DEG = 0f
    }
}
