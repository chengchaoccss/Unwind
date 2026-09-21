package com.armilla.neckcare.scene.frame

import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System

/**
 * Per-frame entry point on the ECS side. Systems need a public no-argument constructor, so the
 * system itself is stateless and forwards to whoever currently listens.
 */
object FrameLoop {
    @Volatile var onFrame: ((deltaSeconds: Float) -> Unit)? = null
}

class FrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        FrameLoop.onFrame?.invoke(context.deltaTime)
    }
}
