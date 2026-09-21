package com.armilla.neckcare.domain.model

/** The three rotation axes of the neck; each is measured in two opposite directions. */
enum class Axis {
    ROTATION,
    FLEXION,
    LATERAL,
}

/** Which colour family a direction uses: jade for left, amber for right, paper for front/back. */
enum class Side {
    LEFT,
    RIGHT,
    CENTER,
}

/**
 * The six measured directions, in the fixed test order of PRD §6. [sign] is the sign of the axis
 * angle in this direction, following PRD §11: right rotation, extension and right bend are positive.
 */
enum class Direction(
    val key: String,
    val label: String,
    val axis: Axis,
    val side: Side,
    val sign: Int,
    val instruction: String,
) {
    LEFT_ROTATION("lrot", "左旋", Axis.ROTATION, Side.LEFT, -1, "慢慢向左转头，转到自然停住的位置"),
    RIGHT_ROTATION("rrot", "右旋", Axis.ROTATION, Side.RIGHT, 1, "慢慢向右转头，转到自然停住的位置"),
    FLEXION("flex", "前屈", Axis.FLEXION, Side.CENTER, -1, "慢慢低头，下巴靠向胸口，到自然停住的位置"),
    EXTENSION("ext", "后仰", Axis.FLEXION, Side.CENTER, 1, "慢慢抬头向上看，到自然停住的位置"),
    LEFT_BEND("lbend", "左侧屈", Axis.LATERAL, Side.LEFT, -1, "左耳慢慢靠向左肩，肩膀不要抬"),
    RIGHT_BEND("rbend", "右侧屈", Axis.LATERAL, Side.RIGHT, 1, "右耳慢慢靠向右肩，肩膀不要抬");

    /** Upper bound above which a reading means the body turned or tracking drifted (PRD §11). */
    val plausibleMaxDeg: Int
        get() =
            when (axis) {
                Axis.ROTATION -> 100
                Axis.FLEXION -> 90
                Axis.LATERAL -> 60
            }

    companion object {
        val testOrder: List<Direction> = entries.toList()

        fun fromKey(key: String): Direction? = entries.firstOrNull { it.key == key }
    }
}

enum class MeasurementStatus {
    VALID,
    SKIPPED,
    INVALID,
}

data class Measurement(
    val direction: Direction,
    val angleDeg: Int,
    val peakSpeedDps: Float = 0f,
    val dwellMs: Long = 0L,
    val retries: Int = 0,
    val status: MeasurementStatus = MeasurementStatus.VALID,
)

enum class SessionMode {
    FULL,
    TEST_ONLY,
}

/** One finished test: the unit every lobby, trend and result figure is derived from. */
data class TestResult(
    val sessionId: Long,
    val finishedAtMillis: Long,
    val mode: SessionMode,
    val measurements: List<Measurement>,
) {
    fun angle(direction: Direction): Int? =
        measurements.firstOrNull { it.direction == direction && it.status == MeasurementStatus.VALID }?.angleDeg

    val measuredCount: Int
        get() = Direction.entries.count { angle(it) != null }

    val isComplete: Boolean
        get() = measuredCount == Direction.entries.size

    /** Sum of the measured directions; PRD §11 "合计". */
    val totalDeg: Int
        get() = Direction.entries.sumOf { angle(it) ?: 0 }
}

enum class ExerciseType(val key: String) {
    ORB("orb"),
    SHOULDER("shoulder"),

    /** Stores hits and launched targets in the caught / total columns. */
    PUNCH("punch"),

    /** 三环呼吸; breaths finished go in the caught / total columns. */
    BREATH("breath"),
}

/** Outcome of one guided exercise (PRD §12 ExerciseResult). */
data class ExerciseResult(
    val sessionId: Long,
    val type: ExerciseType,
    val durationSeconds: Int,
    val orbsCaught: Int = 0,
    val orbsTotal: Int = 0,
    val lapsBack: Int = 0,
    val lapsForward: Int = 0,
    val pauseCount: Int = 0,
)
