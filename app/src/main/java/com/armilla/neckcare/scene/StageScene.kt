package com.armilla.neckcare.scene

import android.graphics.Typeface
import androidx.compose.ui.unit.Density
import com.armilla.neckcare.domain.model.Axis
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.usecase.HeadAngleCalculator
import com.armilla.neckcare.domain.usecase.Quaternion
import com.armilla.neckcare.scene.environment.MeteorShower
import com.armilla.neckcare.scene.environment.SkyDome
import com.armilla.neckcare.scene.environment.SkyPanorama
import com.armilla.neckcare.ui.session.SessionStage
import com.armilla.neckcare.ui.session.SessionViewModel
import com.armilla.neckcare.domain.usecase.GazePoint
import com.armilla.neckcare.ui.stage.LobbyPanels
import com.armilla.neckcare.ui.stage.OrbPanels
import com.armilla.neckcare.ui.stage.PanelGroup
import com.armilla.neckcare.domain.usecase.Point3
import com.armilla.neckcare.ui.stage.PagePanels
import com.armilla.neckcare.ui.stage.PanelSpec
import com.armilla.neckcare.ui.stage.PunchPanels
import com.armilla.neckcare.ui.stage.ResultPanels
import com.armilla.neckcare.ui.stage.SessionPanels
import com.armilla.neckcare.ui.stage.ShoulderPanels
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns every entity the app creates and runs the per-frame work. Compose hands it state and the
 * panel entities; head-driven motion never goes through recomposition.
 */
class StageScene(private val density: Density, private val numerals: Typeface) {
    val anchor = StageAnchor()
    private var sky: SkyDome? = null
    private var meteors: MeteorShower? = null
    private var armillary: Armillary? = null
    private var gauge: TestGauge? = null
    private var calibrationPoint: Entity? = null
    private var orbScene: OrbScene? = null
    private var shoulderScene: ShoulderScene? = null
    private var shoulderLogTimer = 0f
    private var punchScene: PunchScene? = null
    private var trackingLostSeconds = 0f

    /** Radius of the shoulder guide rings, from settings. */
    var ringRadiusM = 0.19f

    /** True while onboarding or one of 记录 / 课程 / 设置 covers the centre of the lobby. */
    var pageOpen = false
        set(value) {
            if (field != value) {
                field = value
                applyVisibility(shownStage ?: SessionStage.LOBBY, shownPaused)
            }
        }

    /** Palm positions in stage space, supplied by the stage every frame; null while untracked. */
    var leftHand: Vector3? = null
    var rightHand: Vector3? = null
    private var shownAngles: Map<Direction, Int>? = null
    private var resultAngles: Map<Direction, Int> = emptyMap()
    private var ready = false

    private val followGroup = Entity()
    private val readingGroup = Entity()
    private val reticleGroup = Entity()
    private val panels = HashMap<String, Entity>()
    private var entityOf: ((Any) -> Entity?)? = null

    private var anchorRotation: Quat = Quat.identity()
    private var neutral: Quaternion = Quaternion.IDENTITY
    private var lastForward: Vector3? = null
    private var followYawDeg = 0f
    private var followFromDeg = 0f
    private var followToDeg = 0f
    private var followEase = -1f
    private var awaySeconds = 0f
    private var readingYawDeg = 0f
    private var readingPitchDeg = 0f
    private var shownStage: SessionStage? = null
    private var shownPaused = false
    private var shownDirection: Direction? = null

    init {
        listOf(followGroup, readingGroup, reticleGroup).forEach(anchor::addChild)
    }

    /** Sky, dais and the armillary. The panorama is painted off the main thread. */
    suspend fun buildEnvironment() {
        val bitmap = withContext(Dispatchers.Default) { SkyPanorama.render() }
        sky = SkyDome(TextureResource.create(bitmap)).also(anchor::addChild)
        anchor.addChild(Dais())
        meteors = MeteorShower(anchor) { anchor.eyeHeightM }
        armillary = Armillary().also(anchor::addChild)
        ready = true
    }

    fun bindPanels(entityOf: (Any) -> Entity?) {
        this.entityOf = entityOf
    }

    /** Centres the layout on the head pose and lays every panel out again for the new eye height. */
    fun calibrate(headPosition: Vector3, headRotation: Quat) {
        anchor.calibrate(headPosition, headRotation)
        anchorRotation = anchor.components[TransformComponent::class.java]?.quaternion ?: Quat.identity()
        neutral = headRotation.toDomain()
        followYawDeg = 0f
        followEase = -1f
        readingYawDeg = 0f
        readingPitchDeg = 0f
        val eye = Vector3(0f, anchor.eyeHeightM, 0f)
        listOf(followGroup, readingGroup, reticleGroup).forEach {
            it.components[TransformComponent::class.java]?.apply {
                setPosition(eye)
                setQuaternion(Quat.identity())
            }
        }
        sky?.setEyeHeight(anchor.eyeHeightM)
        armillary?.components?.get(TransformComponent::class.java)?.setPosition(anchor.board(2.5f, 800f, ARMILLARY_BOARD_Y))
        gauge?.destroy()
        gauge = TestGauge(anchor.eyeHeightM, numerals).also {
            it.enabled = false
            anchor.addChild(it)
        }
        // The light the user looks at to calibrate: straight ahead on the line of sight, 2.5 m away.
        calibrationPoint?.destroy()
        calibrationPoint =
            Entity().also { point ->
                val at = anchor.board(2.5f, 800f, 450f)
                val centre = com.armilla.neckcare.scene.geometry.Vec3(at.x, at.y, at.z)
                val data = com.armilla.neckcare.scene.geometry.MeshData()
                SceneKit.model(data.sphere(centre, 0.028f), SceneKit.material(SceneColor.AMBER), "calibration_point")?.let(point::addChild)
                SceneKit.model(
                    com.armilla.neckcare.scene.geometry.MeshData().sphere(centre, 0.075f),
                    SceneKit.material(SceneColor.AMBER, 0.22f, additive = true),
                    "calibration_halo",
                )?.let(point::addChild)
                point.enabled = shownStage == SessionStage.CALIBRATING
                anchor.addChild(point)
            }
        shownDirection = null
        // Panels go back to the centred layout; layoutForAxis moves them again when needed.
        (LobbyPanels.fixed + SessionPanels.all + ResultPanels.all + listOf(OrbPanels.Status, OrbPanels.Hint) + ShoulderPanels.all + PunchPanels.all + PagePanels.Page).forEach(::place)
        shownAngles?.let { angles ->
            shownAngles = null
            showLobby(angles)
        }
    }

    private fun panel(spec: PanelSpec): Entity? =
        panels[spec.id] ?: entityOf?.invoke(spec.id)?.also { panels[spec.id] = it }

    private fun place(spec: PanelSpec) {
        val position =
            spec.boardCenterPx?.let { anchor.board(spec.distanceM, it.first, it.second) }
                ?: anchor.polar(spec.distanceM, spec.azimuthDeg, spec.elevationDeg)
        place(spec, position)
    }

    /** Parents a panel under any entity, e.g. the count tag that travels with the orb. */
    private fun placeUnder(spec: PanelSpec, parent: Entity, local: Vector3, yawDeg: Float = 0f) {
        val entity = panel(spec) ?: return
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(local)
            setQuaternion(StageAnchor.yaw(yawDeg))
            val scale = spec.entityScale(density)
            setScaleVector(Vector3(scale, scale, scale))
        }
        if (entity.getParent() !== parent) parent.addChild(entity)
    }

    private fun startOrb(session: SessionViewModel) {
        val path = session.orbPath ?: return
        orbScene?.destroy()
        val scene = OrbScene(anchor.eyeHeightM, path)
        anchor.addChild(scene)
        orbScene = scene
        placeUnder(OrbPanels.Count, scene.pivot, scene.countTagLocal())
        placeUnder(OrbPanels.Boundary, anchor, scene.boundaryTagPosition(), scene.boundaryTagYawDeg())
    }

    private fun place(spec: PanelSpec, anchorLocal: Vector3) {
        val entity = panel(spec) ?: return
        val parent =
            when (spec.group) {
                PanelGroup.WORLD -> anchor
                PanelGroup.FOLLOW -> followGroup
                PanelGroup.READING -> readingGroup
                PanelGroup.RETICLE -> reticleGroup
            }
        // Groups sit at the eyes, so their children are placed relative to the eye point.
        val local =
            if (parent === anchor) anchorLocal
            else Vector3(anchorLocal.x, anchorLocal.y - anchor.eyeHeightM, anchorLocal.z)
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(local)
            setQuaternion(StageAnchor.yaw(spec.yawDeg) * StageAnchor.pitch(spec.pitchDeg))
            val scale = spec.entityScale(density)
            setScaleVector(Vector3(scale, scale, scale))
        }
        if (entity.getParent() !== parent) parent.addChild(entity)
    }

    /** Lobby content: arcs for the last measurement and a tag in each of the six design slots. */
    fun showLobby(angles: Map<Direction, Int>) {
        val model = armillary ?: return
        if (!ready || angles == shownAngles) return
        shownAngles = angles
        model.show(angles)
        val centre = anchor.board(2.5f, 800f, ARMILLARY_BOARD_Y)
        Direction.entries.forEach { direction ->
            val (dx, dy) = TAG_SLOTS.getValue(direction)
            place(
                LobbyPanels.tag(direction.key),
                Vector3(centre.x + dx * PX_M, centre.y - dy * PX_M, centre.z + TAG_LIFT_M),
            )
        }
        applyVisibility(shownStage ?: SessionStage.LOBBY, shownPaused)
    }

    /** "今日数据": the armillary moves to the left, smaller, and shows today's six readings. */
    fun showResult(angles: Map<Direction, Int>) {
        if (angles == resultAngles) return
        resultAngles = angles
        if (shownStage == SessionStage.RESULT) applyArmillary(SessionStage.RESULT)
    }

    private fun applyArmillary(stage: SessionStage) {
        val model = armillary ?: return
        val result = stage == SessionStage.RESULT
        model.components[TransformComponent::class.java]?.apply {
            // R-02: radius 112 px instead of 150 px, centred at (222, 390) on the result board.
            setPosition(if (result) anchor.board(2.5f, 222f, 390f) else anchor.board(2.5f, 800f, ARMILLARY_BOARD_Y))
            val scale = if (result) 112f / 150f else 1f
            setScaleVector(Vector3(scale, scale, scale))
        }
        model.show(if (result) resultAngles else shownAngles.orEmpty())
    }

    /** One frame: follow groups, calibration and test sampling, gauge redraw. */
    fun onFrame(dt: Float, headPosition: Vector3, headRotation: Quat, session: SessionViewModel) {
        if (!ready) return
        meteors?.update(dt)
        if (headPosition.y < 0.2f) {
            trackingLostSeconds += dt
            if (trackingLostSeconds > 3f) session.pauseForTrackingLoss()
            return
        }
        trackingLostSeconds = 0f
        val state = session.state.value

        if (state.stage != shownStage || state.paused != shownPaused) {
            if (state.stage == SessionStage.CALIBRATING && shownStage != SessionStage.CALIBRATING) {
                // Put the calibration point wherever the user is facing now.
                calibrate(headPosition, headRotation)
            }
            if (state.stage == SessionStage.ORB && shownStage != SessionStage.ORB) startOrb(session)
            if (state.stage != SessionStage.ORB && shownStage == SessionStage.ORB) {
                // Hand the travelling tag back before the orb is torn down.
                panel(OrbPanels.Count)?.let(anchor::addChild)
                orbScene?.destroy()
                orbScene = null
            }
            if (state.stage == SessionStage.SHOULDER && shoulderScene == null) {
                shoulderScene = ShoulderScene(anchor.eyeHeightM, ringRadiusM).also(anchor::addChild)
            }
            if (state.stage == SessionStage.PUNCH && punchScene == null) punchScene = PunchScene(anchor.eyeHeightM).also(anchor::addChild)
            if (state.stage != SessionStage.PUNCH && shownStage == SessionStage.PUNCH) {
                punchScene?.destroy()
                punchScene = null
            }
            if (state.stage != SessionStage.SHOULDER && shownStage == SessionStage.SHOULDER) {
                shoulderScene?.destroy()
                shoulderScene = null
            }
            shownStage = state.stage
            shownPaused = state.paused
            applyVisibility(state.stage, state.paused)
        }

        val local = anchorRotation.conjugate() * headRotation
        val forward = local.rotateVector(Vector3(0f, 0f, -1f))
        val yawDeg = Math.toDegrees(atan2(forward.x, -forward.z).toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(asin(forward.y.coerceIn(-1f, 1f)).toDouble()).toFloat()
        val speedDps = lastForward?.let { angleBetween(it, forward) / dt.coerceAtLeast(1e-4f) } ?: 0f
        lastForward = forward
        if (state.stage == SessionStage.LOBBY || state.stage == SessionStage.RESULT) return

        reticleGroup.components[TransformComponent::class.java]?.setQuaternion(gaze(yawDeg, pitchDeg))
        val blend = (dt / READING_SMOOTHING_S).coerceIn(0f, 1f)
        readingYawDeg += (yawDeg - readingYawDeg) * blend
        readingPitchDeg += (pitchDeg - readingPitchDeg) * blend
        readingGroup.components[TransformComponent::class.java]?.setQuaternion(gaze(readingYawDeg, readingPitchDeg))
        lazyFollow(dt, yawDeg)
        if (state.paused) return

        when (state.stage) {
            SessionStage.CALIBRATING -> {
                val aim = angleBetween(forward, Vector3(0f, 0f, -1f))
                if (session.onCalibrationFrame(dt, aim, speedDps)) calibrate(headPosition, headRotation)
            }
            SessionStage.TESTING -> {
                val angles = HeadAngleCalculator.angles(neutral, headRotation.toDomain())
                session.onSweep(angles)
                session.onTestFrame(dt, angles)
                val now = session.state.value
                val direction = now.current ?: return
                if (direction != shownDirection) {
                    if (direction.axis != shownDirection?.axis) layoutForAxis(direction.axis)
                    shownDirection = direction
                    gauge?.configure(direction, now.lastAngleDeg)
                    val last = now.lastAngleDeg
                    panel(SessionPanels.LastTag)?.enabled = last != null
                    if (last != null) gauge?.lastTagPosition(direction, last)?.let { place(SessionPanels.LastTag, it) }
                }
                gauge?.setAngle(session.sweepAngleDeg, angles.lateralDeg)
            }
            SessionStage.ORB -> {
                session.onOrbFrame(dt, GazePoint(yawDeg, pitchDeg), speedDps)
                session.orbSnapshot?.let { orbScene?.update(dt, it) }
            }
            SessionStage.SHOULDER -> {
                val scene = shoulderScene ?: return
                fun local(p: Vector3?) = p?.let { anchor.convertPositionFrom(it, null) }?.let { com.armilla.neckcare.scene.geometry.Vec3(it.x, it.y, it.z) }
                val (l, r) = scene.samples(local(leftHand), local(rightHand), dt)
                session.onShoulderFrame(dt, l, r)
                session.shoulderSnapshot?.let { snap ->
                    scene.update(snap, l?.ringAngleDeg, r?.ringAngleDeg)
                    snap.lapReports.forEach { android.util.Log.i("ArmillaShoulder", "lap $it") }
                    shoulderLogTimer += dt
                    if (shoulderLogTimer >= 1f) {
                        shoulderLogTimer = 0f
                        android.util.Log.i("ArmillaShoulder", "phase=${snap.phase} laps=${snap.laps} L=$l ${snap.left} R=$r ${snap.right} pacer=${snap.pacerDeg.toInt()}")
                    }
                }
            }
            SessionStage.PUNCH -> {
                fun fist(p: Vector3?) =
                    p?.let { anchor.convertPositionFrom(it, null) }?.let { Point3(it.x, it.y - anchor.eyeHeightM, it.z) }
                val l = fist(leftHand)
                val r = fist(rightHand)
                session.onPunchFrame(dt, l, r)
                session.punchSnapshot?.let { punchScene?.update(it, l, r) }
            }
            else -> Unit
        }
    }

    /**
     * Keeps the path of the gaze and the active scale clear. Looking down or up sweeps through
     * where the bars and the console normally sit, and the vertical arc is 8° right of centre, so
     * for flexion and extension everything moves to the left; for lateral bending the big reading
     * moves further right, clear of the dial.
     */
    private fun layoutForAxis(axis: Axis) {
        val vertical = axis == Axis.FLEXION
        place(SessionPanels.Steps.copy(boardCenterPx = (if (vertical) 330f else 800f) to 102f))
        place(SessionPanels.Instruction.copy(boardCenterPx = (if (vertical) 270f else 800f) to 722f))
        place(SessionPanels.Console.copy(azimuthDeg = if (vertical) -42f else 0f, yawDeg = if (vertical) 42f else 0f))
        val readingX =
            when (axis) {
                Axis.ROTATION -> 1025f
                Axis.FLEXION -> 560f
                Axis.LATERAL -> 1150f
            }
        place(SessionPanels.Reading.copy(boardCenterPx = readingX to 412f))
    }

    private fun lazyFollow(dt: Float, yawDeg: Float) {
        if (followEase >= 0f) {
            followEase = (followEase + dt / FOLLOW_EASE_S).coerceAtMost(1f)
            val t = followEase * followEase * (3f - 2f * followEase)
            followYawDeg = followFromDeg + (followToDeg - followFromDeg) * t
            if (followEase >= 1f) followEase = -1f
        } else {
            awaySeconds = if (abs(yawDeg - followYawDeg) > FOLLOW_THRESHOLD_DEG) awaySeconds + dt else 0f
            if (awaySeconds >= FOLLOW_DELAY_S) {
                awaySeconds = 0f
                followFromDeg = followYawDeg
                followToDeg = yawDeg
                followEase = 0f
            }
        }
        followGroup.components[TransformComponent::class.java]?.setQuaternion(StageAnchor.yaw(-followYawDeg))
    }

    private fun applyVisibility(stage: SessionStage, paused: Boolean) {
        val lobby = stage == SessionStage.LOBBY
        val testing = stage == SessionStage.TESTING
        val calibrating = stage == SessionStage.CALIBRATING
        val centre = lobby && !pageOpen
        panel(LobbyPanels.Caption)?.enabled = centre
        panel(LobbyPanels.Actions)?.enabled = centre
        panel(LobbyPanels.Today)?.enabled = lobby
        panel(LobbyPanels.Trend)?.enabled = lobby
        panel(PagePanels.Page)?.enabled = lobby && pageOpen
        panel(LobbyPanels.Console)?.enabled = lobby || stage == SessionStage.RESULT
        Direction.entries.forEach { d -> panel(LobbyPanels.tag(d.key))?.enabled = centre && shownAngles?.get(d) != null }
        val result = stage == SessionStage.RESULT
        armillary?.enabled = centre || result
        applyArmillary(stage)
        ResultPanels.all.forEach { panel(it)?.enabled = result }
        gauge?.enabled = testing
        calibrationPoint?.enabled = calibrating
        panel(SessionPanels.Steps)?.enabled = testing
        panel(SessionPanels.Reading)?.enabled = testing && !paused
        panel(SessionPanels.Instruction)?.enabled = (testing || calibrating) && !paused
        val orb = stage == SessionStage.ORB
        panel(SessionPanels.Reticle)?.enabled = (testing || calibrating || orb) && !paused
        OrbPanels.all.forEach { panel(it)?.enabled = orb && !paused }
        orbScene?.enabled = orb
        val shoulder = stage == SessionStage.SHOULDER
        ShoulderPanels.all.forEach { panel(it)?.enabled = shoulder && !paused }
        shoulderScene?.enabled = shoulder
        val punching = stage == SessionStage.PUNCH
        PunchPanels.all.forEach { panel(it)?.enabled = punching && !paused }
        punchScene?.enabled = punching
        panel(SessionPanels.Console)?.enabled = !lobby && stage != SessionStage.RESULT
        panel(SessionPanels.Pause)?.enabled = paused
        if (!testing) panel(SessionPanels.LastTag)?.enabled = false
    }

    fun destroy() {
        anchor.destroy()
    }

    private fun gaze(yawDeg: Float, pitchDeg: Float): Quat = StageAnchor.yaw(-yawDeg) * StageAnchor.pitch(pitchDeg)

    private fun angleBetween(a: Vector3, b: Vector3): Float {
        val dot = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot).toDouble()).toFloat()
    }

    private fun Quat.toDomain() = Quaternion(x, y, z, w)

    private companion object {
        /** Armillary centre on the lobby board. */
        const val ARMILLARY_BOARD_Y = 392f
        const val PX_M = 0.003125f
        const val TAG_LIFT_M = 0.05f
        const val FOLLOW_THRESHOLD_DEG = 30f
        const val FOLLOW_DELAY_S = 0.6f
        const val FOLLOW_EASE_S = 0.8f
        const val READING_SMOOTHING_S = 0.12f

        /** Tag centres as drawn on the lobby board, in artboard px from the armillary centre. */
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
