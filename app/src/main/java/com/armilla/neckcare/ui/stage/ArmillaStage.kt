package com.armilla.neckcare.ui.stage

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armilla.neckcare.R
import com.armilla.neckcare.data.repository.DesignSampleSeeder
import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.platform.AppContainer
import com.armilla.neckcare.scene.StageScene
import com.armilla.neckcare.scene.frame.FrameLoop
import com.armilla.neckcare.scene.frame.FrameSystem
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
import com.armilla.neckcare.ui.result.ResultPresenter
import com.armilla.neckcare.ui.result.ResultUiState
import com.armilla.neckcare.ui.result.components.ArmillaryUpdatedTag
import com.armilla.neckcare.ui.result.components.NextWeekPanel
import com.armilla.neckcare.ui.result.components.ResultPanel
import com.armilla.neckcare.platform.CuePlayer
import com.armilla.neckcare.ui.session.SessionCue
import com.armilla.neckcare.ui.session.SessionEvent
import com.armilla.neckcare.ui.session.components.ShoulderCentrePanel
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.armilla.neckcare.ui.session.SessionStage
import com.armilla.neckcare.ui.session.SessionViewModel
import com.armilla.neckcare.ui.session.components.BigReading
import com.armilla.neckcare.ui.session.components.ExerciseStatusBar
import com.armilla.neckcare.ui.session.components.GentleHint
import com.armilla.neckcare.ui.session.components.GazeReticle
import com.armilla.neckcare.ui.session.components.InstructionBar
import com.armilla.neckcare.ui.session.components.PausePanel
import com.armilla.neckcare.ui.session.components.TestStepBar
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.WithDesignScale
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import kotlinx.coroutines.flow.first

private const val TAG = "ArmillaStage"

/**
 * The one Full-space stage of the app. 2D panels are AttachmentPanels placed in metres by
 * [PanelSpec]; everything three-dimensional, and all per-frame work, is owned by [StageScene].
 */
@Composable
fun ArmillaStage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lobby: LobbyViewModel =
        viewModel(factory = AppContainer.factory { LobbyViewModel(AppContainer.sessions, AppContainer.settings) })
    val session: SessionViewModel = viewModel(
            factory =
                AppContainer.factory {
                    SessionViewModel(AppContainer.sessions, autoAdjust = { AppContainer.settings.settings.value.autoAdjust })
                }
        )
    val lobbyState by lobby.state.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val settings by AppContainer.settings.settings.collectAsStateWithLifecycle()
    val presenter = remember { ResultPresenter() }
    val resultState by
        produceState(ResultUiState(), sessionState.stage, sessionState.savedSessionId, sessionState.saveFailed, settings) {
            val result = session.lastResult
            if (sessionState.stage == SessionStage.RESULT && result != null) {
                val history = AppContainer.sessions.history()
                value = presenter.present(result, history, settings.autoAdjust, settings.reminderTimes)
            }
        }

    val hmd = remember { HMDTrackingProvider() }
    val handTracking = remember { HandTrackingProvider() }
    val cues = remember { CuePlayer() }
    val scene = remember {
        StageScene(density, ResourcesCompat.getFont(context, R.font.instrument_serif_regular)!!)
    }
    DisposableEffect(hmd, scene) {
        hmd.start()
        handTracking.start()
        registerSystem<FrameSystem>()
        FrameLoop.onFrame = { dt ->
            val pose = hmd.latestData.hmdPose
            val handData = handTracking.latestData
            scene.leftHand = handData.left?.joint(HandJoint.Index.PALM)?.position
            scene.rightHand = handData.right?.joint(HandJoint.Index.PALM)?.position
            scene.onFrame(dt, pose.position, pose.rotation, session)
        }
        onDispose {
            FrameLoop.onFrame = null
            unregisterSystem<FrameSystem>()
            hmd.stop()
            handTracking.stop()
            cues.release()
            scene.destroy()
        }
    }

    LaunchedEffect(Unit) {
        DesignSampleSeeder.seedIfRequested(context, AppContainer.sessions)
        lobby.onEvent(LobbyEvent.Refresh)
    }
    LaunchedEffect(sessionState.cueSerial) {
        when (sessionState.cue) {
            SessionCue.RECORDED -> cues.play(CuePlayer.Cue.RECORDED)
            SessionCue.ORB_CAUGHT -> cues.play(CuePlayer.Cue.ORB_CAUGHT)
            SessionCue.LAP -> cues.play(CuePlayer.Cue.LAP)
            null -> Unit
        }
    }
    // Back in the lobby after a session: show the new measurement.
    LaunchedEffect(sessionState.stage) {
        if (sessionState.stage == SessionStage.LOBBY) lobby.onEvent(LobbyEvent.Refresh)
    }

    SpatialView(
        modifier = Modifier.fillMaxSize(),
        attachments = {
            fun panel(spec: PanelSpec, content: @Composable () -> Unit) =
                AttachmentPanel(spec.id, size = spec.pixelSize(density)) {
                    WithDesignScale(spec.distanceM, spec.contentDpPerPx(density)) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
                    }
                }

            // 大厅
            panel(LobbyPanels.Caption) { ArmillaryCaption(lobbyState.armillaryTitle, lobbyState.armillarySubtitle) }
            panel(LobbyPanels.Actions) {
                LobbyActions(
                    onStart = { session.onEvent(SessionEvent.Start(SessionMode.FULL)) },
                    onTestOnly = { session.onEvent(SessionEvent.Start(SessionMode.TEST_ONLY)) },
                )
            }
            panel(LobbyPanels.Today) { TodayPanel(lobbyState) }
            panel(LobbyPanels.Trend) { TrendPanel(lobbyState) }
            panel(LobbyPanels.Console) {
                val inResult = sessionState.stage == SessionStage.RESULT
                ConsoleBar(
                    listOf(
                        ConsoleItem(ConsoleIcon.LOBBY, "大厅", selected = !inResult) { session.onEvent(SessionEvent.Done) },
                        ConsoleItem(ConsoleIcon.RECORDS, "记录", selected = inResult) {},
                        ConsoleItem(ConsoleIcon.COURSES, "课程", selected = false) {},
                        ConsoleItem(ConsoleIcon.SETTINGS, "设置", selected = false) {},
                    )
                )
            }
            Direction.entries.forEach { direction ->
                panel(LobbyPanels.tag(direction.key)) {
                    lobbyState.angles[direction]?.let { angle ->
                        ReadingTag(direction.label, "$angle°", valueColor = sideColor(direction.side))
                    }
                }
            }

            // 校准与活动度测试
            panel(SessionPanels.Steps) { TestStepBar(sessionState) }
            panel(SessionPanels.Instruction) {
                InstructionBar(sessionState, showSpeed = sessionState.stage == SessionStage.TESTING)
            }
            panel(SessionPanels.Reading) {
                sessionState.current?.let {
                    BigReading(it.label, sessionState.displayAngleDeg, alignEnd = it.axis == Axis.FLEXION)
                }
            }
            panel(SessionPanels.Reticle) { GazeReticle(sessionState.reticle, sessionState.dwellProgress) }
            panel(SessionPanels.LastTag) {
                sessionState.lastAngleDeg?.let { ReadingTag("上次", "$it°") }
            }
            panel(SessionPanels.Console) {
                ConsoleBar(
                    listOf(
                        if (sessionState.paused)
                            ConsoleItem(ConsoleIcon.PLAY, "继续", selected = true) { session.onEvent(SessionEvent.Resume) }
                        else ConsoleItem(ConsoleIcon.PAUSE, "暂停", selected = true) { session.onEvent(SessionEvent.Pause) },
                        ConsoleItem(ConsoleIcon.NEXT, "下一项", selected = false) { session.onEvent(SessionEvent.Next) },
                    ),
                    itemPaddingPx = 56,
                )
            }
            // 视线接光球
            panel(OrbPanels.Status) { ExerciseStatusBar("视线接光球", sessionState.orbTimeProgress, sessionState.orbRemaining) }
            panel(OrbPanels.Hint) { GentleHint(sessionState.orbHint) }
            panel(OrbPanels.Count) {
                ReadingTag("已接住", "${sessionState.orbCaught}", valueSizePx = 34, trailing = "共 ${sessionState.orbTotal} 个")
            }
            panel(OrbPanels.Boundary) {
                ReadingTag("你的活动边界，光球不会越过", value = null, labelColor = ArmillaColors.Jade)
            }

            // 肩部环绕
            panel(ShoulderPanels.Centre) { ShoulderCentrePanel(sessionState) }

            // 今日数据
            panel(ResultPanels.Main) {
                ResultPanel(
                    resultState,
                    saveFailed = sessionState.saveFailed,
                    onRetest = { session.onEvent(SessionEvent.Retest) },
                    onDone = { session.onEvent(SessionEvent.Done) },
                    onRetrySave = { session.onEvent(SessionEvent.RetrySave) },
                )
            }
            panel(ResultPanels.NextWeek) {
                NextWeekPanel(resultState, settings.autoAdjust, AppContainer.settings::setAutoAdjust)
            }
            panel(ResultPanels.ArmillaryTag) { ArmillaryUpdatedTag() }

            panel(SessionPanels.Pause) {
                PausePanel(
                    onResume = { session.onEvent(SessionEvent.Resume) },
                    onRestart = { session.onEvent(SessionEvent.Retest) },
                    onEndAndSave = { session.onEvent(SessionEvent.EndAndSave) },
                    onQuit = { session.onEvent(SessionEvent.QuitWithoutSaving) },
                )
            }
        },
        update = { _, _ ->
            scene.showLobby(lobbyState.angles)
            scene.showResult(resultState.angles)
        },
    ) { content, attachments ->
        content.addEntity(scene.anchor)
        scene.bindPanels(attachments::entity)
        val first = hmd.dataFlow.first { it.hmdPose.position.y > 0.2f }
        scene.buildEnvironment()
        scene.calibrate(first.hmdPose.position, first.hmdPose.rotation)
        scene.showLobby(lobbyState.angles)
        Log.i(TAG, "stage ready, eye height ${scene.anchor.eyeHeightM} m")
        // Debug builds: a marker file starts a test straight away, for unattended captures.
        val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && java.io.File(context.getExternalFilesDir(null), "autostart_result").exists()) {
            session.finishWithReadingsForCapture(
                SessionMode.FULL,
                mapOf(
                    Direction.LEFT_ROTATION to 64, Direction.RIGHT_ROTATION to 71,
                    Direction.FLEXION to 47, Direction.EXTENSION to 59,
                    Direction.LEFT_BEND to 37, Direction.RIGHT_BEND to 42,
                ),
            )
        } else if (debuggable && java.io.File(context.getExternalFilesDir(null), "autostart_orb").exists() || java.io.File(context.getExternalFilesDir(null), "autostart_shoulder").exists()) {
            session.finishWithReadingsForCapture(
                SessionMode.FULL,
                mapOf(
                    Direction.LEFT_ROTATION to 64, Direction.RIGHT_ROTATION to 71,
                    Direction.FLEXION to 47, Direction.EXTENSION to 59,
                    Direction.LEFT_BEND to 37, Direction.RIGHT_BEND to 42,
                ),
                thenExercise = true,
            )
            if (java.io.File(context.getExternalFilesDir(null), "autostart_shoulder").exists()) {
                kotlinx.coroutines.delay(1500)
                session.onEvent(SessionEvent.Next)
            }
        } else if (debuggable && java.io.File(context.getExternalFilesDir(null), "autostart_test").exists()) {
            session.calibrationAimDeg = 45f
            session.onEvent(SessionEvent.Start(SessionMode.TEST_ONLY))
            // Optional: skip ahead to a later direction, e.g. "2" lands on 前屈.
            val skip = java.io.File(context.getExternalFilesDir(null), "autostart_test").readText().trim().toIntOrNull() ?: 0
            if (skip > 0) {
                kotlinx.coroutines.delay(3500)
                repeat(skip) {
                    session.onEvent(SessionEvent.Next)
                    kotlinx.coroutines.delay(300)
                }
            }
        }
    }
}

private fun sideColor(side: Side) =
    when (side) {
        Side.LEFT -> ArmillaColors.Jade
        Side.RIGHT -> ArmillaColors.Amber
        Side.CENTER -> ArmillaColors.Paper
    }
