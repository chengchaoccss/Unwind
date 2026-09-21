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

/** Panels of the lobby (大厅), measured off the artboard. */
object LobbyPanels {
    /** H-02: caption above the armillary, on the 2.5 m layer. */
    val Caption = PanelSpec("lobby_caption", 560, 96, DesignScale.FAR_M, boardCenterPx = 800f to 122f)

    /** H-05/H-06: both buttons, straight ahead below the armillary. */
    val Actions = PanelSpec("lobby_actions", 580, 100, DesignScale.MAIN_M, elevationDeg = -16f)

    /** H-07: left panel at 38°, turned 22° toward the user. */
    val Today = PanelSpec("lobby_today", 440, 548, DesignScale.MAIN_M, azimuthDeg = -38f, elevationDeg = -1f, yawDeg = 22f)

    /** H-08: right panel at 38°, turned 22° toward the user. */
    val Trend = PanelSpec("lobby_trend", 440, 640, DesignScale.MAIN_M, azimuthDeg = 38f, elevationDeg = 0f, yawDeg = -22f)

    /** H-09: near-field console, 35° below the line of sight, tilted back to face the eyes. */
    val Console = PanelSpec("console", 680, 90, DesignScale.NEAR_M, elevationDeg = -35f, pitchDeg = -36f)

    /** H-04: one tag per direction, placed by the armillary at the end of each arc. */
    fun tag(key: String) = PanelSpec("lobby_tag_$key", 200, 56, DesignScale.FAR_M)
}
