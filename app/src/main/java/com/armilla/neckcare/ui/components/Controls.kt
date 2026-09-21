package com.armilla.neckcare.ui.components

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/** Amber pill: the one main action of a screen. Text #1d1406, 600, letter spacing 0.08 em. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightPx: Int = 80,
    fontPx: Int = 27,
    horizontalPaddingPx: Int = 52,
    letterSpacingEm: Float = 0.08f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val s = if (pressed) 0.97f else 1f
                    scaleX = s
                    scaleY = s
                }
                .height(heightPx.dpx)
                .clip(CircleShape)
                .background(ArmillaColors.Amber)
                .spatialHoverEffect()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .controllerHapticFeedback(interactionSource = interactionSource)
                .padding(horizontal = horizontalPaddingPx.dpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = ArmillaColors.OnAmber,
            fontFamily = ArmillaType.Body,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontPx.spx,
            letterSpacing = letterSpacingEm.em,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Outlined pill on rgba(10,20,25,0.72), paper-white 22 px text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightPx: Int = 80,
    fontPx: Int = 22,
    horizontalPaddingPx: Int = 36,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val s = if (pressed) 0.97f else 1f
                    scaleX = s
                    scaleY = s
                }
                .height(heightPx.dpx)
                .clip(CircleShape)
                .background(ArmillaColors.SecondaryFill)
                .border(1.dpx, ArmillaColors.SecondaryStroke, CircleShape)
                .spatialHoverEffect()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .controllerHapticFeedback(interactionSource = interactionSource)
                .padding(horizontal = horizontalPaddingPx.dpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = ArmillaColors.Paper,
            fontFamily = ArmillaType.Body,
            fontSize = fontPx.spx,
            letterSpacing = 0.04.em,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 84 × 48 switch: jade track with an ink thumb when on, paper-white 16 % track when off. */
@Composable
fun ArmillaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(width = 84.dpx, height = 48.dpx)
                .clip(CircleShape)
                .background(if (checked) ArmillaColors.Jade else ArmillaColors.SwitchOffFill)
                .spatialHoverEffect()
                .toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .controllerHapticFeedback(interactionSource = interactionSource)
                .padding(5.dpx),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.size(38.dpx)
                .clip(CircleShape)
                .background(if (checked) ArmillaColors.Ink else ArmillaColors.Paper)
        )
    }
}

/** "相差 7°": amber 20 px text in a 1 px amber 60 % pill. */
@Composable
fun DifferenceTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .border(1.dpx, ArmillaColors.AmberTagStroke, CircleShape)
                .padding(horizontal = 16.dpx, vertical = 3.dpx)
    ) {
        Text(
            text,
            color = ArmillaColors.Amber,
            fontFamily = ArmillaType.Body,
            fontSize = 20.spx,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 6 px track, paper white 16 %, amber fill. */
@Composable
fun ArmillaProgressBar(progress: Float, modifier: Modifier = Modifier, widthPx: Int = 320, fill: Color = ArmillaColors.Amber) {
    Box(
        modifier
            .width(widthPx.dpx)
            .height(6.dpx)
            .clip(CircleShape)
            .background(ArmillaColors.TrackFill)
    ) {
        Box(
            Modifier.width((widthPx * progress.coerceIn(0f, 1f)).dpx)
                .height(6.dpx)
                .clip(CircleShape)
                .background(fill)
        )
    }
}

enum class ConsoleIcon {
    LOBBY,
    RECORDS,
    COURSES,
    SETTINGS,
    PAUSE,
    NEXT,
    PLAY,
    STOP,
}

data class ConsoleItem(val icon: ConsoleIcon, val label: String, val selected: Boolean, val onClick: () -> Unit)

/** Near-field console: a fully round panel of 70 px pills, icon 28 px + label 22 px. */
@Composable
fun ConsoleBar(items: List<ConsoleItem>, modifier: Modifier = Modifier, itemPaddingPx: Int = 28) {
    ArmillaPanel(modifier = modifier, cornerRadiusPx = 999) {
        Row(
            modifier = Modifier.padding(8.dpx),
            horizontalArrangement = Arrangement.spacedBy(8.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item -> ConsoleButton(item, itemPaddingPx) }
        }
    }
}

@Composable
private fun ConsoleButton(item: ConsoleItem, paddingPx: Int) {
    val interactionSource = remember { MutableInteractionSource() }
    val action = rememberUpdatedState(item.onClick)
    val label = rememberUpdatedState(item.label)
    val lastFired = remember { mutableLongStateOf(0L) }
    val tint = if (item.selected) ArmillaColors.Paper else ArmillaColors.Mist
    Row(
        modifier =
            Modifier.height(70.dpx)
                .clip(CircleShape)
                .background(if (item.selected) ArmillaColors.ConsoleSelectedFill else Color.Transparent)
                .spatialHoverEffect()
                // The console is low and tilted, so the hand ray meets it at a shallow angle and a
                // pinch drags the hit point off the pill before release: clickable then sees a
                // press that ends outside and never fires ("下一项" did nothing in the headset).
                // The pill therefore acts when the pinch lands, not when it lets go.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val now = down.uptimeMillis
                        Log.i("ArmillaConsole", "down on " + label.value + " at " + down.position)
                        if (now - lastFired.longValue > 350) {
                            lastFired.longValue = now
                            action.value()
                        }
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Tab,
                    onClick = {},
                )
                .controllerHapticFeedback(interactionSource = interactionSource)
                .padding(horizontal = paddingPx.dpx),
        horizontalArrangement = Arrangement.spacedBy(12.dpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(item.icon, tint, Modifier.size(28.dpx))
        Text(
            item.label,
            color = tint,
            fontFamily = ArmillaType.Body,
            fontSize = 22.spx,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Line icons on a 28 × 28 grid, 1.8 px stroke, round caps and joins; paths from the artboards. */
@Composable
fun LineIcon(icon: ConsoleIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 28f
        val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), tint, style = stroke)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), 1.8f * u, StrokeCap.Round)
        when (icon) {
            ConsoleIcon.LOBBY ->
                path {
                    moveTo(5f * u, 13f * u); lineTo(14f * u, 5.5f * u); lineTo(23f * u, 13f * u)
                    moveTo(7.5f * u, 11.5f * u); lineTo(7.5f * u, 22f * u); lineTo(20.5f * u, 22f * u)
                    lineTo(20.5f * u, 11.5f * u)
                }
            ConsoleIcon.RECORDS -> {
                line(6f, 22f, 6f, 15f); line(11.3f, 22f, 11.3f, 8f)
                line(16.6f, 22f, 16.6f, 12f); line(22f, 22f, 22f, 6f)
            }
            ConsoleIcon.COURSES ->
                path {
                    moveTo(14f * u, 5f * u); lineTo(24f * u, 10.5f * u); lineTo(14f * u, 16f * u)
                    lineTo(4f * u, 10.5f * u); close()
                    moveTo(4f * u, 16f * u); lineTo(14f * u, 21.5f * u); lineTo(24f * u, 16f * u)
                }
            ConsoleIcon.SETTINGS -> {
                line(4f, 9f, 24f, 9f); line(4f, 19f, 24f, 19f)
                circleAt(this, tint, 10f, 9f, 2.6f, u, stroke)
                circleAt(this, tint, 18f, 19f, 2.6f, u, stroke)
            }
            ConsoleIcon.PAUSE -> {
                line(10f, 6f, 10f, 22f); line(18f, 6f, 18f, 22f)
            }
            ConsoleIcon.NEXT -> {
                path { moveTo(7f * u, 6f * u); lineTo(18f * u, 14f * u); lineTo(7f * u, 22f * u) }
                line(21f, 6f, 21f, 22f)
            }
            ConsoleIcon.STOP -> path {
                moveTo(8f * u, 8f * u); lineTo(20f * u, 8f * u); lineTo(20f * u, 20f * u); lineTo(8f * u, 20f * u); close()
            }
            ConsoleIcon.PLAY ->
                path { moveTo(9f * u, 6f * u); lineTo(21f * u, 14f * u); lineTo(9f * u, 22f * u); close() }
        }
    }
}

private fun circleAt(scope: DrawScope, tint: Color, cx: Float, cy: Float, r: Float, u: Float, stroke: Stroke) {
    scope.drawCircle(tint, radius = r * u, center = Offset(cx * u, cy * u), style = stroke)
}

/** Rounded-rectangle helper for small chips that are not full pills. */
fun chipShape(radius: androidx.compose.ui.unit.Dp) = RoundedCornerShape(radius)
