package com.armilla.neckcare.scene.environment

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The world-fixed environment: a large sphere seen from inside, carrying an equirectangular
 * panorama. Without a sky a Full stage renders black.
 *
 * The mesh is built here rather than with MeshResource.createSphere, whose UVs are not a plain
 * latitude/longitude mapping (measured on device with [CalibrationGrid]). Azimuth 0, the middle
 * of the panorama, faces -Z (forward); positive azimuth is toward +X, the user's right.
 */
class SkyDome(texture: TextureResource, radiusM: Float = RADIUS_M) : Entity() {
    private val material: UnlitMaterial

    init {
        material =
            UnlitMaterial.create().apply {
                setBaseColorTexture(texture)
                setCullingMode(MaterialCullingMode.NONE)
                setApplyToneMapping(false)
            }
        components.set(ModelComponent(buildMesh(radiusM), material))
        setEyeHeight(DEFAULT_EYE_HEIGHT_M)
    }

    /** 1 is the design's sky; 三环呼吸 dims it to a half so the rings carry the scene. */
    fun setBrightness(level: Float) {
        val v = level.coerceIn(0f, 1f)
        material.setBaseColor(com.pico.spatial.core.math.Color4(v, v, v, 1f))
    }

    /** Keeps the horizon on the line of sight: every height in the design is relative to it. */
    fun setEyeHeight(eyeHeightM: Float) {
        components[TransformComponent::class.java]?.setPosition(Vector3(0f, eyeHeightM, 0f))
    }

    companion object {
        /** Far enough to read as scenery; a 60 m sphere did not render on device, 45 m does. */
        const val RADIUS_M = 45f
        const val DEFAULT_EYE_HEIGHT_M = 1.6f
        private const val COLUMNS = 96
        private const val ROWS = 48

        private fun buildMesh(radius: Float): MeshResource {
            val positions = ArrayList<Vector3>((COLUMNS + 1) * (ROWS + 1))
            val uvs = ArrayList<Vector2>((COLUMNS + 1) * (ROWS + 1))
            for (row in 0..ROWS) {
                val v = row / ROWS.toFloat()
                val elevation = Math.toRadians(90.0 - 180.0 * v)
                for (column in 0..COLUMNS) {
                    val u = column / COLUMNS.toFloat()
                    val azimuth = Math.toRadians(-180.0 + 360.0 * u)
                    positions +=
                        Vector3(
                            (radius * cos(elevation) * sin(azimuth)).toFloat(),
                            (radius * sin(elevation)).toFloat(),
                            (-radius * cos(elevation) * cos(azimuth)).toFloat(),
                        )
                    // Texture V runs bottom-up in this engine (measured with CalibrationGrid).
                    uvs += Vector2(u, 1f - v)
                }
            }
            val indices = ArrayList<Int>(COLUMNS * ROWS * 6)
            val stride = COLUMNS + 1
            for (row in 0 until ROWS) for (column in 0 until COLUMNS) {
                val a = row * stride + column
                val b = a + 1
                val c = a + stride
                val d = c + 1
                indices += listOf(a, c, b, b, c, d)
            }
            return MeshResource.createWithMeshModel(
                MeshModel(positions = positions, triangleIndices = indices, uv0 = uvs),
                null,
                "sky_dome",
            )
        }
    }
}
