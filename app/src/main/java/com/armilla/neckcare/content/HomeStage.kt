package com.armilla.neckcare.content

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armilla.neckcare.scene.StageAnchor
import com.armilla.neckcare.scene.environment.SkyDome
import com.armilla.neckcare.scene.environment.SkyPanorama
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingData
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import java.io.File
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val TAG = "ArmillaSpike"

/**
 * Risk spike, replaced by the real lobby once verified on device: sky dome, one panel at 1.4 m
 * in the three design typefaces, a torus at 2.5 m, and live head angles.
 */
@Composable
fun HomeStage() {
    val context = LocalContext.current
    val hmd = remember { HMDTrackingProvider() }
    val data by
        hmd.dataFlow.collectAsState(
            initial = HMDTrackingData(HMDPose(Vector3.ZERO, Quat.identity()), 0L)
        )
    DisposableEffect(hmd) {
        val result = hmd.start()
        Log.i(TAG, "HMD start=$result support=${hmd.supportState} state=${hmd.state}")
        onDispose { hmd.stop() }
    }

    val q = data.hmdPose.rotation
    val p = data.hmdPose.position
    // Forward is -Z in stage space; PRD §11 uses x right, y up, z forward.
    val f = q.rotateVector(Vector3(0f, 0f, -1f))
    val r = q.rotateVector(Vector3(1f, 0f, 0f))
    val yaw = Math.toDegrees(atan2(f.x, -f.z).toDouble())
    val pitch = Math.toDegrees(asin(f.y.coerceIn(-1f, 1f)).toDouble())
    val roll = -Math.toDegrees(asin(r.y.coerceIn(-1f, 1f)).toDouble())

    val converter = LocalPhysicalLengthConverter.current
    val density = LocalDensity.current
    val panelWidthDp = converter.lengthToDp(0.77f, LengthUnit.Meters)
    val panelHeightDp = converter.lengthToDp(0.49f, LengthUnit.Meters)
    val panelPx = with(density) { IntSize(panelWidthDp.roundToPx(), panelHeightDp.roundToPx()) }
    Log.i(TAG, "0.77 m = $panelWidthDp = ${panelPx.width}px, density=${density.density}")

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            AttachmentPanel("spike", size = panelPx) {
                SpikePanel(
                    yaw = yaw.roundToInt(),
                    pitch = pitch.roundToInt(),
                    roll = roll.roundToInt(),
                    detail =
                        "位置 %.2f, %.2f, %.2f · %s".format(p.x, p.y, p.z, hmd.supportState.name),
                )
            }
        },
    ) { content, attachments ->
        val root = StageAnchor()
        content.addEntity(root)
        val first = hmd.dataFlow.first { it.hmdPose.position.y > 0.2f }
        root.calibrate(first.hmdPose.position, first.hmdPose.rotation)
        Log.i(TAG, "anchored at ${first.hmdPose.position}, eye=${root.eyeHeightM}")

        val bitmap = withContext(Dispatchers.Default) { SkyPanorama.render() }
        withContext(Dispatchers.IO) {
            runCatching {
                val out = File(context.getExternalFilesDir(null), "sky_panorama.png")
                out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                Log.i(TAG, "panorama saved to ${out.absolutePath}")
            }
        }
        val texture = TextureResource.create(bitmap)

        root.addChild(SkyDome(texture))

        // Ruler: 0.77 m wide, just under the panel, to check the panel's physical width.
        Entity().apply {
            components.set(
                ModelComponent(
                    MeshResource.createBox(Vector3(0.77f, 0.01f, 0.01f)),
                    UnlitMaterial.create().apply {
                        setBaseColor(Color4(0.94f, 0.71f, 0.35f, 1f))
                        setApplyToneMapping(false)
                    },
                )
            )
            components[TransformComponent::class.java]?.setPosition(root.polar(1.39f, 0f, -8f))
            root.addChild(this)
        }

        attachments.entity("spike")?.let { panel ->
            panel.components[TransformComponent::class.java]?.setPosition(root.polar(1.4f, 0f, -8f))
            root.addChild(panel)
            Log.i(TAG, "panel bounds=${panel.getVisualBounds(null)}")
        }
    }
}

@Composable
private fun SpikePanel(yaw: Int, pitch: Int, roll: Int, detail: String) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier =
            Modifier.fillMaxSize()
                .clip(shape)
                .background(ArmillaColors.PanelFillSolid)
                .border(1.dp, ArmillaColors.PanelStroke, shape)
                .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("今天的活动度", color = ArmillaColors.Paper, fontFamily = ArmillaType.Title, fontSize = 46.sp)
        Text(
            "旋转 $yaw°   屈伸 $pitch°   侧屈 $roll°",
            color = ArmillaColors.Amber,
            fontFamily = ArmillaType.Reading,
            fontSize = 84.sp,
        )
        Text(
            "慢慢向右转头，转到自然停住的位置",
            color = ArmillaColors.Paper,
            fontFamily = ArmillaType.Body,
            fontSize = 24.sp,
        )
        Text(detail, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.sp)
    }
}
