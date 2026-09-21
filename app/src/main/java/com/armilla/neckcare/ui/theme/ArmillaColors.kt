package com.armilla.neckcare.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens taken verbatim from the "设计语言" artboard. Left is always jade, right is always
 * amber, flexion/extension is paper white; never swap them.
 */
object ArmillaColors {
    // design-style: fixed-figma-color 设计语言/颜色
    val Ink = Color(0xFF0E1A20) // 黛夜: panels, dais, switch thumb
    val Mountain = Color(0xFF2C4852) // 远山: environment mid-ground
    val Mist = Color(0xFFA9B5B4) // 晨雾: secondary text
    val Slate = Color(0xFF8A9A9C) // 辅助灰: pending steps, axis labels, ordinals
    val Paper = Color(0xFFEFE9DC) // 纸白: primary text, flexion/extension data
    val Amber = Color(0xFFF0B45A) // 琥珀: right side, primary action, current item
    val Jade = Color(0xFF7FD1C4) // 青玉: left side, done state, positive change
    val OnAmber = Color(0xFF1D1406) // text on amber

    // Panel material: rgba(13,25,31,0.84) over blur; PRD §14 fallback is 0.92 solid without blur.
    val PanelFill = Color(0xD60D191F)
    val PanelFillSolid = Color(0xEB0D191F)
    val PanelStroke = Paper.copy(alpha = 0.16f)
    val PanelHighlight = Paper.copy(alpha = 0.18f)
    val Divider = Paper.copy(alpha = 0.14f)

    // Reading tag: rgba(10,20,25,0.80), 1 px paper 14 %
    val TagFill = Color(0xCC0A1419)
    val TagStroke = Paper.copy(alpha = 0.14f)

    // Secondary button: rgba(10,20,25,0.72), 1 px paper 30 %
    val SecondaryFill = Color(0xB80A1419)
    val SecondaryStroke = Paper.copy(alpha = 0.30f)

    val ConsoleSelectedFill = Paper.copy(alpha = 0.12f)
    val TrackFill = Paper.copy(alpha = 0.16f)
    val SwitchOffFill = Paper.copy(alpha = 0.16f)
    val AmberGlow = Amber.copy(alpha = 0.28f)
    val AmberCurrentFill = Amber.copy(alpha = 0.12f)
    val AmberCurrentStroke = Amber.copy(alpha = 0.55f)
    val AmberTagStroke = Amber.copy(alpha = 0.60f)
}
