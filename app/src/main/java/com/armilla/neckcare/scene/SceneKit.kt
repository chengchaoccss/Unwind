package com.armilla.neckcare.scene

import com.armilla.neckcare.scene.geometry.MeshData
import com.armilla.neckcare.scene.geometry.Vec3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3

/** The design's scene colours as linear-free RGB triples; alpha is supplied per use. */
enum class SceneColor(val r: Float, val g: Float, val b: Float) {
    PAPER(0xEF / 255f, 0xE9 / 255f, 0xDC / 255f),
    AMBER(0xF0 / 255f, 0xB4 / 255f, 0x5A / 255f),
    JADE(0x7F / 255f, 0xD1 / 255f, 0xC4 / 255f),
    MIST(0xA9 / 255f, 0xB5 / 255f, 0xB4 / 255f),
    INK(0x09 / 255f, 0x14 / 255f, 0x19 / 255f),
    HEAD(0xC9 / 255f, 0xCF / 255f, 0xCB / 255f),

    /** Orb core gradient of the board: #fff7e2 at the centre, #f8cd82 around it. */
    ORB_LIGHT(0xF8 / 255f, 0xCD / 255f, 0x82 / 255f),
    WHITE_HOT(0xFF / 255f, 0xF7 / 255f, 0xE2 / 255f);

    fun color4(alpha: Float = 1f) = Color4(r, g, b, alpha)
}

object SceneKit {
    /**
     * Unlit material in an exact design colour. Tone mapping is switched off because it darkens
     * and shifts the palette (measured on device against the colour tokens).
     */
    fun material(color: SceneColor, alpha: Float = 1f, additive: Boolean = false): UnlitMaterial {
        val blending =
            when {
                additive -> BlendingMode.ADD
                alpha < 1f -> BlendingMode.TRANSPARENT
                else -> BlendingMode.OPAQUE
            }
        return UnlitMaterial.create(blending).apply {
            // Measured with BlendProbe: an untextured ADD material blends like a translucent tint of
            // its colour (alpha 0 is invisible, alpha 1 is solid), so glows keep full colour, low alpha.
            setBaseColor(color.color4(alpha))
            setApplyToneMapping(false)
            setCullingMode(MaterialCullingMode.NONE)
            if (blending != BlendingMode.OPAQUE) setDepthWrite(false)
        }
    }

    /** Unlit material showing [bitmap] with its own alpha; used for drawn scales and halos. */
    fun textured(bitmap: android.graphics.Bitmap, additive: Boolean = false): UnlitMaterial =
        UnlitMaterial.create(if (additive) BlendingMode.ADD else BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(com.pico.spatial.core.ecs.resource.TextureResource.create(bitmap))
            // Measured with BlendProbe: a textured ADD card painted as brightness on OPAQUE black,
            // with base alpha 1, adds light and leaves the background alone. Alpha 0 hides it.
            setApplyToneMapping(false)
            setCullingMode(MaterialCullingMode.NONE)
            setDepthWrite(false)
        }

    fun mesh(data: MeshData, name: String): MeshResource =
        MeshResource.createWithMeshModel(
            MeshModel(
                positions = data.positions.map { Vector3(it.x, it.y, it.z) },
                triangleIndices = data.indices,
                // Texture V runs bottom-up in this engine (measured on device).
                uv0 = data.uvs.takeIf { it.size == data.positions.size }?.map { Vector2(it.first, 1f - it.second) },
            ),
            null,
            name,
        )

    /** An entity showing [data], or null when there is nothing to draw. */
    fun model(data: MeshData, material: UnlitMaterial, name: String): Entity? {
        if (data.isEmpty) return null
        return Entity().apply { components.set(ModelComponent(mesh(data, name), material)) }
    }

    fun Vec3.toVector3() = Vector3(x, y, z)
}
