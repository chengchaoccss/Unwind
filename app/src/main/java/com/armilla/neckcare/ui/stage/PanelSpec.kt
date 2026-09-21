package com.armilla.neckcare.ui.stage

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.armilla.neckcare.ui.theme.DesignScale
import kotlin.math.roundToInt

/**
 * Where one 2D panel sits, in the terms of the "空间布局" board: artboard-pixel size, distance from
 * the eyes, azimuth to the right of straight ahead, elevation above the line of sight, and how far
 * the panel is turned (yaw, toward the user) or tilted back (pitch).
 */
/** How a panel moves with the head (PRD §4 "跟随规则"). */
enum class PanelGroup {
    /** World fixed, like the lobby and result panels. */
    WORLD,

    /** Eases back in front after the gaze has been more than 30° away for 0.6 s. */
    FOLLOW,

    /** Stays beside the gaze with a little smoothing: the big reading. */
    READING,

    /** The gaze cursor itself. */
    RETICLE,
}

data class PanelSpec(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
    val distanceM: Float,
    val azimuthDeg: Float = 0f,
    val elevationDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    /** When set, the panel centre is this artboard point on a plane at [distanceM] (far layer). */
    val boardCenterPx: Pair<Float, Float>? = null,
    val group: PanelGroup = PanelGroup.WORLD,
) {
    /**
     * Texture pixels per artboard pixel. AttachmentPanel content beyond about 2048 x 1280 px is
     * not shown (measured on device), so large or distant panels render at a capped density and the
     * entity is scaled up by [entityScale] to keep the physical size of PRD §4.
     */
    fun pixelsPerDesignPx(density: Density): Float {
        val wanted = DesignScale(distanceM).dpPerPx * density.density
        return minOf(wanted, MAX_TEXTURE_WIDTH_PX / widthPx, MAX_TEXTURE_HEIGHT_PX / heightPx)
    }

    fun pixelSize(density: Density): IntSize {
        val q = pixelsPerDesignPx(density)
        return IntSize((widthPx * q).roundToInt(), (heightPx * q).roundToInt())
    }

    /** dp per artboard pixel to use for the panel's own content. */
    fun contentDpPerPx(density: Density): Float = pixelsPerDesignPx(density) / density.density

    fun entityScale(density: Density): Float =
        DesignScale(distanceM).dpPerPx * density.density / pixelsPerDesignPx(density)

    private companion object {
        const val MAX_TEXTURE_WIDTH_PX = 2040f
        const val MAX_TEXTURE_HEIGHT_PX = 1270f
    }
}

/**
 * Panels of the lobby (大厅), measured off the artboard. Angles here are before the draw-in of
 * DesignScale.COMFORT; the side panels and the buttons are set a little wider than the board so
 * that, once drawn in, they still clear the armillary's tags on the far layer.
 */
object LobbyPanels {
    /** H-02: caption above the armillary, on the 2.5 m layer. */
    val Caption = PanelSpec("lobby_caption", 560, 96, DesignScale.FAR_M, boardCenterPx = 800f to 122f)

    /** H-05/H-06: both buttons, straight ahead below the armillary. */
    val Actions = PanelSpec("lobby_actions", 580, 100, DesignScale.MAIN_M, elevationDeg = -21f)

    /** H-07: left panel at 38°, turned 22° toward the user. */
    val Today = PanelSpec("lobby_today", 440, 604, DesignScale.MAIN_M, azimuthDeg = -44f, elevationDeg = -1f, yawDeg = 26f)

    /** H-08: right panel at 38°, turned 22° toward the user. */
    val Trend = PanelSpec("lobby_trend", 440, 640, DesignScale.MAIN_M, azimuthDeg = 44f, elevationDeg = 0f, yawDeg = -26f)

    /** H-09: near-field console, 35° below the line of sight, tilted back to face the eyes. */
    val Console = PanelSpec("console", 680, 90, DesignScale.NEAR_M, elevationDeg = -35f, pitchDeg = -36f)

    /** Panels with a fixed place; the six tags are placed by the scene. */
    val fixed by lazy { listOf(Caption, Actions, Today, Trend, Console) }

    /** H-04: one tag per direction, placed by the armillary at the end of each arc. */
    fun tag(key: String) = PanelSpec("lobby_tag_$key", 200, 56, DesignScale.FAR_M)
}

/** Panels of calibration and the mobility test (活动度测试), measured off the artboard. */
object SessionPanels {
    /** T-01: step bar, top centre. */
    val Steps = PanelSpec("test_steps", 720, 84, DesignScale.MAIN_M, boardCenterPx = 800f to 102f, group = PanelGroup.FOLLOW)

    /** T-08: instruction bar, 940 px wide, below the line of sight. */
    val Instruction = PanelSpec("test_instruction", 940, 116, DesignScale.MAIN_M, boardCenterPx = 800f to 722f, group = PanelGroup.FOLLOW)

    /** T-07: big reading to the right of the reticle, slightly above the line of sight. */
    val Reading = PanelSpec("test_reading", 330, 230, DesignScale.FAR_M, boardCenterPx = 1025f to 412f, group = PanelGroup.READING)

    /** T-06: the reticle sits at the centre of view. */
    val Reticle = PanelSpec("reticle", 80, 80, DesignScale.FAR_M, boardCenterPx = 800f to 450f, group = PanelGroup.RETICLE)

    /** T-05: "上次 71°", placed by the gauge next to the dashed mark. */
    val LastTag = PanelSpec("test_last", 180, 56, DesignScale.FAR_M)

    /** T-10: exercise console with 暂停 and 下一项. */
    val Console = PanelSpec("console_session", 480, 90, DesignScale.NEAR_M, elevationDeg = -35f, pitchDeg = -36f)

    /** Pause panel, straight ahead on the main layer (not drawn in the design). */
    val Pause = PanelSpec("pause", 480, 470, DesignScale.MAIN_M, boardCenterPx = 800f to 440f, group = PanelGroup.FOLLOW)

    val all = listOf(Steps, Instruction, Reading, Reticle, LastTag, Console, Pause)
}

/** Panels of "今日数据", measured off the artboard. */
object ResultPanels {
    /** R-01: main panel, straight ahead on the main layer. */
    val Main = PanelSpec("result_main", 720, 712, DesignScale.MAIN_M, boardCenterPx = 800f to 410f)

    /** R-03: "接下来一周" at 38° to the right, turned 22° toward the user. */
    val NextWeek = PanelSpec("result_next", 368, 452, DesignScale.MAIN_M, azimuthDeg = 40f, elevationDeg = 3f, yawDeg = -22f)

    /** R-02: caption under the small armillary on the left. */
    val ArmillaryTag = PanelSpec("result_armillary_tag", 250, 56, DesignScale.FAR_M, boardCenterPx = 222f to 568f)

    val all = listOf(Main, NextWeek, ArmillaryTag)
}

/** Panels of 视线接光球, measured off the artboard. */
object OrbPanels {
    /** O-10: status bar, top centre, 80 px tall. */
    val Status = PanelSpec("orb_status", 680, 84, DesignScale.MAIN_M, boardCenterPx = 800f to 102f, group = PanelGroup.FOLLOW)

    /** Gentle hint under the status bar. */
    val Hint = PanelSpec("orb_hint", 420, 50, DesignScale.MAIN_M, boardCenterPx = 800f to 176f, group = PanelGroup.FOLLOW)

    /** O-09: "已接住 7 共 12 个", left of the orb; parented to the orb pivot by the scene. */
    val Count = PanelSpec("orb_count", 230, 60, DesignScale.FAR_M)

    /** O-08: caption on the motion boundary. */
    val Boundary = PanelSpec("orb_boundary", 330, 56, DesignScale.FAR_M)

    val all = listOf(Status, Hint, Count, Boundary)
}

/** Panels of 肩部环绕, measured off the artboard. */
object ShoulderPanels {
    /** S-06: centre panel, 400 px wide, straight ahead on the main layer. */
    val Centre = PanelSpec("shoulder_centre", 400, 500, DesignScale.MAIN_M, boardCenterPx = 800f to 460f)

    val all = listOf(Centre)
}

/** Panels of 出拳. */
object PunchPanels {
    val Status = PanelSpec("punch_status", 680, 84, DesignScale.MAIN_M, boardCenterPx = 800f to 102f, group = PanelGroup.FOLLOW)

    /** "已击中 12", under the status bar on the far layer. */
    val Count = PanelSpec("punch_count", 330, 60, DesignScale.FAR_M, boardCenterPx = 800f to 215f)

    val all = listOf(Status, Count)
}

/** Panels of 三环呼吸, measured off the "三环呼吸，山水中" board. */
object BreathPanels {
    val Status = PanelSpec("breath_status", 660, 84, DesignScale.MAIN_M, boardCenterPx = 800f to 102f)

    /** 吸气 / 呼气 in 宋体 50 px under the sphere. */
    val Cue = PanelSpec("breath_cue", 600, 76, DesignScale.FAR_M, boardCenterPx = 800f to 667f)

    /** "第 3 次，共 12 次". */
    val Count = PanelSpec("breath_count", 260, 56, DesignScale.FAR_M, boardCenterPx = 800f to 735f)

    /** 暂停 and 结束. */
    val Console = PanelSpec("console_breath", 480, 90, DesignScale.NEAR_M, elevationDeg = -35f, pitchDeg = -36f)

    val all = listOf(Status, Cue, Count, Console)
}

/** The one full page in front of the user: onboarding, 记录, 课程, 设置. Not drawn in the design. */
object PagePanels {
    val Page = PanelSpec("page", 780, 712, DesignScale.MAIN_M, boardCenterPx = 800f to 410f)
}
