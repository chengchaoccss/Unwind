package com.armilla.neckcare.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armilla.neckcare.data.repository.SessionRepository
import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.ExerciseResult
import com.armilla.neckcare.domain.model.ExerciseType
import com.armilla.neckcare.domain.model.Measurement
import com.armilla.neckcare.domain.model.MeasurementStatus
import com.armilla.neckcare.domain.model.SessionMode
import com.armilla.neckcare.domain.model.TestResult
import com.armilla.neckcare.domain.usecase.BreathSnapshot
import com.armilla.neckcare.domain.usecase.BreathingExercise
import com.armilla.neckcare.domain.usecase.DwellRecorder
import com.armilla.neckcare.domain.usecase.GazePoint
import com.armilla.neckcare.domain.usecase.HandSample
import com.armilla.neckcare.domain.usecase.OrbExercise
import com.armilla.neckcare.domain.usecase.OrbPath
import com.armilla.neckcare.domain.usecase.OrbPathGenerator
import com.armilla.neckcare.domain.usecase.OrbSnapshot
import com.armilla.neckcare.domain.usecase.HeadAngles
import com.armilla.neckcare.domain.usecase.MobilityInsights
import com.armilla.neckcare.domain.usecase.Point3
import com.armilla.neckcare.domain.usecase.PunchExercise
import com.armilla.neckcare.domain.usecase.PunchSnapshot
import com.armilla.neckcare.domain.usecase.RecorderConfig
import com.armilla.neckcare.domain.usecase.RecorderPhase
import com.armilla.neckcare.domain.usecase.ShoulderExercise
import com.armilla.neckcare.domain.usecase.ShoulderPhase
import com.armilla.neckcare.domain.usecase.ShoulderSnapshot
import com.armilla.neckcare.domain.usecase.SpeedTier
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One session from "开始练习" to "今日数据" (PRD §10 state diagram). The scene side feeds it one
 * head sample per frame; everything it decides is plain data, so it runs in unit tests.
 */
class SessionViewModel(
    private val sessions: SessionRepository,
    private val insights: MobilityInsights = MobilityInsights(),
    private val config: RecorderConfig = RecorderConfig(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val autoAdjust: () -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var history: List<TestResult> = emptyList()
    private var recorder: DwellRecorder? = null
    private val measurements = LinkedHashMap<Direction, Measurement>()
    private var calibrationDwell = 0f
    private var pending: TestResult? = null

    /** How close the gaze must be to the calibration point; widened only by debug captures. */
    var calibrationAimDeg: Float = CALIBRATION_AIM_DEG

    /** Latest finished test of this session, for the result screen. */
    var lastResult: TestResult? = null
        private set

    fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Start -> start(event.mode)
            SessionEvent.Pause -> if (isRunning()) { orbPauses++; _state.update { it.copy(paused = true) } }
            SessionEvent.Resume -> _state.update { it.copy(paused = false) }
            SessionEvent.Next -> next()
            SessionEvent.EndAndSave -> finishTest(partial = true)
            SessionEvent.QuitWithoutSaving -> reset()
            SessionEvent.Retest -> start(_state.value.mode)
            SessionEvent.Done -> reset()
            SessionEvent.RetrySave -> pending?.let(::save)
            SessionEvent.StartBreathing -> {
                breathing = BreathingExercise()
                breathSnapshot = null
                _state.value = SessionUiState(stage = SessionStage.BREATH)
            }
        }
    }

    /** Debug captures only: finishes a test with the given readings, as if they had been measured. */
    fun finishWithReadingsForCapture(mode: SessionMode, readings: Map<Direction, Int>, thenExercise: Boolean = false) {
        measurements.clear()
        readings.forEach { (direction, angle) -> measurements[direction] = Measurement(direction, angle) }
        _state.value = SessionUiState(stage = SessionStage.TESTING, mode = if (thenExercise) SessionMode.FULL else SessionMode.TEST_ONLY)
        viewModelScope.launch {
            history = sessions.history()
            finishTest(partial = false)
            _state.update { it.copy(mode = mode) }
        }
    }

    /** PRD §10: tracking lost for more than 3 s pauses the session and says what to do. */
    fun pauseForTrackingLoss() {
        if (!isRunning() || _state.value.paused) return
        _state.update { it.copy(paused = true, instruction = "追踪丢失，请看向明亮处") }
    }

    private fun isRunning() = _state.value.stage !in setOf(SessionStage.LOBBY, SessionStage.RESULT)

    private fun start(mode: SessionMode) {
        measurements.clear()
        recorder = null
        calibrationDwell = 0f
        _state.value =
            SessionUiState(
                stage = SessionStage.CALIBRATING,
                mode = mode,
                instruction = "看向正前方的光点，停住 2 秒",
                instructionDetail = "坐稳，身体保持不动",
            )
        viewModelScope.launch { history = sessions.history() }
    }

    /**
     * Calibration frame: [aimErrorDeg] is the angle between the gaze and the calibration point.
     * Returns true on the frame the neutral pose should be captured.
     */
    fun onCalibrationFrame(dtSeconds: Float, aimErrorDeg: Float, headSpeedDps: Float): Boolean {
        val s = _state.value
        if (s.stage != SessionStage.CALIBRATING || s.paused) return false
        val steady = aimErrorDeg <= calibrationAimDeg && headSpeedDps < config.dwellSpeedDps * 2
        calibrationDwell = if (steady) calibrationDwell + dtSeconds else 0f
        val progress = quantise(calibrationDwell / config.dwellSeconds)
        if (calibrationDwell >= config.dwellSeconds) {
            beginDirection(Direction.testOrder.first())
            return true
        }
        if (progress != s.dwellProgress) {
            _state.update { it.copy(dwellProgress = progress, reticle = if (progress > 0f) ReticleLook.DWELLING else ReticleLook.IDLE) }
        }
        return false
    }

    /** Test frame: head angles relative to the calibrated neutral pose. */
    fun onTestFrame(dtSeconds: Float, angles: HeadAngles) {
        val s = _state.value
        val direction = s.current ?: return
        if (s.stage != SessionStage.TESTING || s.paused) return
        val snap = (recorder ?: return).update(dtSeconds, angles.of(direction.axis))

        if (snap.phase == RecorderPhase.DONE) {
            measurements[direction] =
                Measurement(direction, snap.recordedAngleDeg ?: 0, snap.peakSpeedDps, snap.dwellMs, snap.retries)
            advance()
            return
        }
        val instruction =
            when {
                snap.phase == RecorderPhase.RECORDED || snap.phase == RecorderPhase.RETURNING -> "好了，慢慢回正"
                snap.phase == RecorderPhase.VOIDED -> "先回正，再来一次"
                snap.showSkipHint -> "可以先跳过这一项"
                else -> direction.instruction
            }
        val next =
            s.copy(
                displayAngleDeg = snap.displayAngleDeg,
                dwellProgress = quantise(snap.dwellProgress),
                reticle =
                    when {
                        snap.phase == RecorderPhase.RECORDED -> ReticleLook.RECORDED
                        snap.dwellProgress > 0f -> ReticleLook.DWELLING
                        else -> ReticleLook.IDLE
                    },
                cue = if (snap.phase == RecorderPhase.RECORDED && s.reticle != ReticleLook.RECORDED) SessionCue.RECORDED else s.cue,
                cueSerial = if (snap.phase == RecorderPhase.RECORDED && s.reticle != ReticleLook.RECORDED) s.cueSerial + 1 else s.cueSerial,
                speedBars = snap.speedBars,
                // The meter only shows how fast the head is moving; it never tells the user off.
                speedLabel = if (snap.speedTier == SpeedTier.OK) snap.speedTier.label else "",
                speedIsOk = true,
                instruction = instruction,
                steps =
                    s.steps.map {
                        if (it.direction == direction && snap.recordedAngleDeg != null && snap.phase != RecorderPhase.MEASURING && snap.phase != RecorderPhase.VOIDED)
                            it.copy(status = StepStatus.DONE, readingDeg = snap.recordedAngleDeg)
                        else it
                    },
            )
        if (next != s) _state.value = next
    }

    /** The route of the current orb exercise; the scene draws it. */
    var orbPath: OrbPath? = null
        private set

    /** Latest orb frame for the scene; kept out of the UI state to avoid recomposition. */
    var orbSnapshot: OrbSnapshot? = null
        private set

    private var orb: OrbExercise? = null
    private var orbPauses = 0

    private fun beginOrb() {
        val latest = lastResult ?: insights.latest(history)
        val (left, right) = insights.orbSplit(latest, autoAdjust())
        val path = OrbPathGenerator.generate(insights.motionBoundary(history), left, right)
        orbPath = path
        orb = OrbExercise(path)
        orbSnapshot = null
        orbPauses = 0
    }

    /** Orb frame: where the head points, and how fast it is turning. */
    fun onOrbFrame(dtSeconds: Float, gaze: GazePoint, headSpeedDps: Float) {
        val s = _state.value
        if (s.stage != SessionStage.ORB || s.paused) return
        val exercise = orb ?: run { beginOrb(); orb!! }
        val snap = exercise.update(dtSeconds, gaze, headSpeedDps)
        orbSnapshot = snap
        if (snap.finished) {
            val sessionId = lastResult?.sessionId ?: 0L
            viewModelScope.launch {
                runCatching {
                    sessions.saveExercise(
                        ExerciseResult(sessionId, ExerciseType.ORB, 90 - snap.remainingSeconds, snap.caught, snap.total, pauseCount = orbPauses)
                    )
                }
            }
            orb = null
            _state.update { it.copy(stage = SessionStage.SHOULDER, orbHint = null) }
            return
        }
        val remaining = "%d:%02d".format(snap.remainingSeconds / 60, snap.remainingSeconds % 60)
        val next =
            s.copy(
                orbCaught = snap.caught,
                orbTotal = snap.total,
                orbRemaining = remaining,
                orbTimeProgress = (snap.timeProgress * 160).roundToInt() / 160f,
                orbHint = if (snap.showSlowHint) "慢一点也没关系" else null,
                cue = if (snap.justCaught) SessionCue.ORB_CAUGHT else s.cue,
                cueSerial = if (snap.justCaught) s.cueSerial + 1 else s.cueSerial,
            )
        if (next != s) _state.value = next
    }

    /** Latest breathing frame for the scene. */
    var breathSnapshot: BreathSnapshot? = null
        private set

    private var breathing: BreathingExercise? = null

    /** Breathing frame. Ends back in the lobby: there is nothing to score. */
    fun onBreathFrame(dtSeconds: Float) {
        val s = _state.value
        if (s.stage != SessionStage.BREATH || s.paused) return
        val exercise = breathing ?: return
        val snap = exercise.update(dtSeconds)
        breathSnapshot = snap
        if (snap.finished) {
            finishBreathing(snap.totalBreaths)
            return
        }
        fun step(v: Float) = (v * 20).roundToInt() / 20f
        val next =
            s.copy(
                breathInhaleAlpha = step(snap.inhaleCue),
                breathExhaleAlpha = step(snap.exhaleCue),
                breathCount = snap.breath,
                breathTotal = snap.totalBreaths,
                breathRemaining = "%d:%02d".format(snap.remainingSeconds / 60, snap.remainingSeconds % 60),
                breathProgress = (snap.progress * 200).roundToInt() / 200f,
            )
        if (next != s) _state.value = next
    }

    private fun finishBreathing(breathsDone: Int) {
        val total = breathSnapshot?.totalBreaths ?: 12
        viewModelScope.launch {
            runCatching { sessions.saveExercise(ExerciseResult(0L, ExerciseType.BREATH, breathsDone * 10, orbsCaught = breathsDone, orbsTotal = total)) }
        }
        breathing = null
        reset()
    }

    /** Latest punch frame for the scene. */
    var punchSnapshot: PunchSnapshot? = null
        private set

    private var punch: PunchExercise? = null

    /** Punch frame: fist positions relative to the eyes, or null while a hand is not tracked. */
    fun onPunchFrame(dtSeconds: Float, left: Point3?, right: Point3?) {
        val s = _state.value
        if (s.stage != SessionStage.PUNCH || s.paused) return
        val exercise = punch ?: PunchExercise().also { punch = it }
        val snap = exercise.update(dtSeconds, left, right)
        punchSnapshot = snap
        if (snap.finished) {
            val sessionId = lastResult?.sessionId ?: 0L
            viewModelScope.launch {
                runCatching { sessions.saveExercise(ExerciseResult(sessionId, ExerciseType.PUNCH, 60, orbsCaught = snap.hits, orbsTotal = snap.launched)) }
            }
            punch = null
            _state.update { it.copy(stage = SessionStage.RESULT) }
            return
        }
        val next =
            s.copy(
                punchHits = snap.hits,
                punchCombo = snap.combo,
                punchRemaining = "%d:%02d".format(snap.remainingSeconds / 60, snap.remainingSeconds % 60),
                punchProgress = (snap.timeProgress * 160).roundToInt() / 160f,
                cue = if (snap.justHit) SessionCue.PUNCH else s.cue,
                cueSerial = if (snap.justHit) s.cueSerial + 1 else s.cueSerial,
            )
        if (next != s) _state.value = next
    }

    /** Latest shoulder frame for the scene. */
    var shoulderSnapshot: ShoulderSnapshot? = null
        private set

    private var shoulder: ShoulderExercise? = null

    /** Shoulder frame: each hand's place on its ring, or null while the hand is not tracked. */
    fun onShoulderFrame(dtSeconds: Float, left: HandSample?, right: HandSample?) {
        val s = _state.value
        if (s.stage != SessionStage.SHOULDER || s.paused) return
        val exercise = shoulder ?: ShoulderExercise().also { shoulder = it }
        val snap = exercise.update(dtSeconds, left, right)
        shoulderSnapshot = snap
        if (snap.finished) {
            val sessionId = lastResult?.sessionId ?: 0L
            viewModelScope.launch {
                runCatching {
                    sessions.saveExercise(
                        ExerciseResult(sessionId, ExerciseType.SHOULDER, 90 - snap.remainingSeconds, lapsBack = snap.lapsBack, lapsForward = snap.lapsForward)
                    )
                }
            }
            shoulder = null
            _state.update { it.copy(stage = SessionStage.PUNCH) }
            return
        }
        val counting = snap.phase == ShoulderPhase.BACKWARD || snap.phase == ShoulderPhase.FORWARD
        val next =
            s.copy(
                shoulderTitle = snap.phase.title,
                shoulderLaps = snap.laps,
                shoulderCounting = counting,
                shoulderHint = snap.hint ?: "跟着前面的小光点，一圈大约 4 秒",
                cue = if (snap.lapJustCounted) SessionCue.LAP else s.cue,
                cueSerial = if (snap.lapJustCounted) s.cueSerial + 1 else s.cueSerial,
            )
        if (next != s) _state.value = next
    }

    /** Unrounded sweep angle for the scene; kept out of the UI state to avoid recomposition. */
    var sweepAngleDeg: Float = 0f
        private set

    fun onSweep(angles: HeadAngles) {
        val d = _state.value.current ?: return
        sweepAngleDeg = (angles.of(d.axis) * d.sign).coerceAtLeast(0f)
    }

    private fun next() {
        val s = _state.value
        when (s.stage) {
            SessionStage.TESTING -> {
                val direction = s.current ?: return
                if (measurements[direction] == null) {
                    measurements[direction] = Measurement(direction, 0, status = MeasurementStatus.SKIPPED)
                    _state.update { st -> st.copy(steps = st.steps.map { if (it.direction == direction) it.copy(status = StepStatus.SKIPPED) else it }) }
                }
                advance()
            }
            SessionStage.BREATH -> finishBreathing(((breathSnapshot?.breath ?: 1) - 1).coerceAtLeast(0))
            SessionStage.ORB -> { orb = null; _state.update { it.copy(stage = SessionStage.SHOULDER, paused = false, orbHint = null) } }
            SessionStage.SHOULDER -> { shoulder = null; _state.update { it.copy(stage = SessionStage.PUNCH, paused = false) } }
            SessionStage.PUNCH -> { punch = null; _state.update { it.copy(stage = SessionStage.RESULT, paused = false) } }
            else -> Unit
        }
    }

    private fun advance() {
        val order = Direction.testOrder
        val index = order.indexOf(_state.value.current)
        if (index + 1 < order.size) beginDirection(order[index + 1]) else finishTest(partial = false)
    }

    private fun beginDirection(direction: Direction) {
        recorder = DwellRecorder(direction, config)
        sweepAngleDeg = 0f
        _state.update { s ->
            s.copy(
                stage = SessionStage.TESTING,
                current = direction,
                displayAngleDeg = 0,
                dwellProgress = 0f,
                reticle = ReticleLook.IDLE,
                instruction = direction.instruction,
                instructionDetail = "停住 2 秒后自动记录，不用使劲",
                lastAngleDeg = insights.latest(history)?.angle(direction),
                steps =
                    s.steps.map {
                        when {
                            it.direction == direction -> it.copy(status = StepStatus.CURRENT)
                            it.status == StepStatus.CURRENT -> it.copy(status = StepStatus.SKIPPED)
                            else -> it
                        }
                    },
            )
        }
    }

    private fun finishTest(partial: Boolean) {
        val mode = _state.value.mode
        val all =
            Direction.testOrder.map { measurements[it] ?: Measurement(it, 0, status = MeasurementStatus.SKIPPED) }
        if (all.none { it.status == MeasurementStatus.VALID }) {
            reset()
            return
        }
        val result = TestResult(0, clock(), mode, all)
        save(result)
        val nextStage = if (mode == SessionMode.FULL && !partial) SessionStage.ORB else SessionStage.RESULT
        lastResult = result
        if (nextStage == SessionStage.ORB) beginOrb()
        _state.update { it.copy(stage = nextStage, current = null, paused = false, reticle = ReticleLook.IDLE, dwellProgress = 0f) }
    }

    private fun save(result: TestResult) {
        pending = result
        viewModelScope.launch {
            runCatching { sessions.save(result) }
                .onSuccess { id ->
                    lastResult = result.copy(sessionId = id)
                    pending = null
                    history = sessions.history()
                    _state.update { it.copy(savedSessionId = id, saveFailed = false) }
                }
                .onFailure {
                    lastResult = result
                    _state.update { it.copy(saveFailed = true) }
                }
        }
    }

    private fun reset() {
        recorder = null
        measurements.clear()
        _state.value = SessionUiState()
    }

    private fun quantise(progress: Float) = (progress.coerceIn(0f, 1f) * 48).roundToInt() / 48f

    private companion object {
        const val CALIBRATION_AIM_DEG = 4f
    }
}
