package com.armilla.neckcare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Converts artboard pixels to dp for a panel placed at a given distance.
 *
 * PRD §4: world size (mm) = artboard px × 1.25 × distance (m). On device 1 dp is 0.8 mm
 * (LocalPhysicalLengthConverter, checked against a ruler entity), so one artboard pixel is
 * 1.25 × distance / 0.8 dp. Layout code keeps using the numbers printed on the artboards.
 */
class DesignScale(val distanceM: Float, dpPerPxOverride: Float? = null) {
    /** Overridden when a panel renders at a capped texture density and is scaled up as an entity. */
    val dpPerPx: Float = dpPerPxOverride ?: (1.25f * distanceM / MM_PER_DP)

    fun dp(px: Number): Dp = (px.toFloat() * dpPerPx).dp

    fun sp(px: Number): TextUnit = (px.toFloat() * dpPerPx).sp

    /** Physical size in metres of [px] artboard pixels at this distance. */
    fun meters(px: Number): Float = px.toFloat() * 1.25f * distanceM / 1000f

    companion object {
        const val MM_PER_DP = 0.8f
        const val NEAR_M = 0.6f
        const val MAIN_M = 1.4f
        const val FAR_M = 2.5f
    }
}

val LocalDesignScale = compositionLocalOf { DesignScale(DesignScale.MAIN_M) }

@Composable
fun WithDesignScale(distanceM: Float, dpPerPx: Float? = null, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDesignScale provides DesignScale(distanceM, dpPerPx), content = content)
}

/** Artboard pixels as dp / sp at the current panel distance. */
val Number.dpx: Dp
    @Composable get() = LocalDesignScale.current.dp(this)

val Number.spx: TextUnit
    @Composable get() = LocalDesignScale.current.sp(this)
