package com.armilla.neckcare.ui.stage

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armilla.neckcare.data.repository.DesignSampleSeeder
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.platform.AppContainer
import com.armilla.neckcare.scene.StageScene
import com.armilla.neckcare.ui.components.ConsoleBar
import com.armilla.neckcare.ui.components.ConsoleIcon
import com.armilla.neckcare.ui.components.ConsoleItem
import com.armilla.neckcare.ui.components.ReadingTag
import com.armilla.neckcare.ui.lobby.LobbyEvent
import com.armilla.neckcare.ui.lobby.LobbyViewModel
import com.armilla.neckcare.ui.lobby.components.ArmillaryCaption
import com.armilla.neckcare.ui.lobby.components.LobbyActions
import com.armilla.neckcare.ui.lobby.components.TodayPanel
import com.armilla.neckcare.ui.lobby.components.TrendPanel
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.WithDesignScale
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import kotlinx.coroutines.flow.first

private const val TAG = "ArmillaStage"

/**
 * The one Full-space stage of the app. 2D panels are AttachmentPanels placed in metres by
 * [PanelSpec]; everything three-dimensional is owned by [StageScene] on the ECS side.
 */
@Composable
fun ArmillaStage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lobby: LobbyViewModel =
        viewModel(factory = AppContainer.factory { LobbyViewModel(AppContainer.sessions, AppContainer.settings) })
    val lobbyState by lobby.state.collectAsStateWithLifecycle()

    val hmd = remember { HMDTrackingProvider() }
    DisposableEffect(hmd) {
        hmd.start()
        onDispose { hmd.stop() }
    }
    val scene = remember { StageScene(density) }
    DisposableEffect(scene) { onDispose { scene.destroy() } }

    LaunchedEffect(Unit) {
        DesignSampleSeeder.seedIfRequested(context, AppContainer.sessions)
        lobby.onEvent(LobbyEvent.Refresh)
    }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            fun panel(spec: PanelSpec, content: @Composable () -> Unit) =
                AttachmentPanel(spec.id, size = spec.pixelSize(density)) {
                    WithDesignScale(spec.distanceM, spec.contentDpPerPx(density)) { Box(Modifier.fillMaxSize(), Alignment.Center) { content() } }
                }

            panel(LobbyPanels.Caption) { ArmillaryCaption(lobbyState.armillaryTitle, lobbyState.armillarySubtitle) }
            panel(LobbyPanels.Actions) { LobbyActions(onStart = {}, onTestOnly = {}) }
            panel(LobbyPanels.Today) { TodayPanel(lobbyState) }
            panel(LobbyPanels.Trend) { TrendPanel(lobbyState) }
            panel(LobbyPanels.Console) {
                ConsoleBar(
                    listOf(
                        ConsoleItem(ConsoleIcon.LOBBY, "大厅", selected = true) {},
                        ConsoleItem(ConsoleIcon.RECORDS, "记录", selected = false) {},
                        ConsoleItem(ConsoleIcon.COURSES, "课程", selected = false) {},
                        ConsoleItem(ConsoleIcon.SETTINGS, "设置", selected = false) {},
                    )
                )
            }
            Direction.entries.forEach { direction ->
                panel(LobbyPanels.tag(direction.key)) {
                    lobbyState.angles[direction]?.let { angle ->
                        ReadingTag(
                            label = direction.label,
                            value = "$angle°",
                            valueColor =
                                when (direction.side) {
                                    Side.LEFT -> ArmillaColors.Jade
                                    Side.RIGHT -> ArmillaColors.Amber
                                    Side.CENTER -> ArmillaColors.Paper
                                },
                        )
                    }
                }
            }
        },
        update = { _, attachments -> scene.showLobby(lobbyState.angles, attachments::entity) },
    ) { content, attachments ->
        content.addEntity(scene.anchor)
        val first = hmd.dataFlow.first { it.hmdPose.position.y > 0.2f }
        scene.calibrate(first.hmdPose.position, first.hmdPose.rotation)
        scene.buildEnvironment()
        scene.place(
            listOf(LobbyPanels.Caption, LobbyPanels.Actions, LobbyPanels.Today, LobbyPanels.Trend, LobbyPanels.Console),
            attachments::entity,
        )
        scene.showLobby(lobbyState.angles, attachments::entity)
        Log.i(TAG, "stage ready, eye height ${scene.anchor.eyeHeightM} m")
    }
}
