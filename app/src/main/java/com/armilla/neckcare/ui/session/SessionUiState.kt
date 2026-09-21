package com.armilla.neckcare.ui.session

import com.armilla.neckcare.domain.model.Direction
import com.armilla.neckcare.domain.model.SessionMode

enum class SessionStage {
    LOBBY,
    CALIBRATING,
    TESTING,
    ORB,
    SHOULDER,
    PUNCH,
    RESULT,
}

enum class StepStatus {
    PENDING,
    CURRENT,
    DONE,
    SKIPPED,
}

data class TestStepUi(val direction: Direction, val status: StepStatus, val readingDeg: Int? = null)

/** The three looks of the gaze reticle on the "设计语言" board. */
enum class ReticleLook {
    IDLE,
    DWELLING,
    RECORDED,
}

data class SessionUiState(
    val stage: SessionStage = SessionStage.LOBBY,
    val mode: SessionMode = SessionMode.FULL,
    val paused: Boolean = false,
    val steps: List<TestStepUi> = Direction.testOrder.map { TestStepUi(it, StepStatus.PENDING) },
    val current: Direction? = null,
    val displayAngleDeg: Int = 0,
    val reticle: ReticleLook = ReticleLook.IDLE,
    /** Dwell arc, quantised so the UI only recomposes when it visibly changes. */
    val dwellProgress: Float = 0f,
    val speedBars: Int = 1,
    val speedLabel: String = "合适",
    val speedIsOk: Boolean = true,
    val instruction: String = "",
    val instructionDetail: String = "停住 2 秒后自动记录，不用使劲",
    val lastAngleDeg: Int? = null,
    val orbCaught: Int = 0,
    val orbTotal: Int = 12,
    val orbRemaining: String = "1:30",
    val orbTimeProgress: Float = 0f,
    val orbHint: String? = null,
    val shoulderTitle: String = "",
    val shoulderLaps: Int = 0,
    val shoulderCounting: Boolean = false,
    val shoulderHint: String = "跟着前面的小光点，一圈大约 4 秒",
    val punchHits: Int = 0,
    val punchCombo: Int = 0,
    val punchRemaining: String = "1:00",
    val punchProgress: Float = 0f,
    /** Cue to play once; the stage clears it after playing. */
    val cue: SessionCue? = null,
    val cueSerial: Int = 0,
    val savedSessionId: Long? = null,
    val saveFailed: Boolean = false,
)

enum class SessionCue {
    RECORDED,
    ORB_CAUGHT,
    LAP,
    PUNCH,
}

sealed interface SessionEvent {
    data class Start(val mode: SessionMode) : SessionEvent

    data object Pause : SessionEvent

    data object Resume : SessionEvent

    /** "下一项": skip the current direction, or move on from an exercise. */
    data object Next : SessionEvent

    data object EndAndSave : SessionEvent

    data object QuitWithoutSaving : SessionEvent

    data object Retest : SessionEvent

    data object Done : SessionEvent

    data object RetrySave : SessionEvent
}
