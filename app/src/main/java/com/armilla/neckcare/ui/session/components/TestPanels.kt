package com.armilla.neckcare.ui.session.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.armilla.neckcare.ui.components.ArmillaDivider
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.components.PrimaryButton
import com.armilla.neckcare.ui.components.SecondaryButton
import com.armilla.neckcare.ui.session.ReticleLook
import com.armilla.neckcare.ui.session.SessionUiState
import com.armilla.neckcare.ui.session.StepStatus
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** T-01: six steps in a fully round panel; done = jade tick + reading, current = amber chip. */
@Composable
fun TestStepBar(state: SessionUiState, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier, cornerRadiusPx = 999) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dpx, vertical = 10.dpx),
            horizontalArrangement = Arrangement.spacedBy(4.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.steps.forEach { step ->
                val current = step.status == StepStatus.CURRENT
                Row(
                    modifier =
                        Modifier.height(60.dpx)
                            .clip(CircleShape)
                            .then(
                                if (current)
                                    Modifier.background(ArmillaColors.AmberCurrentFill)
                                        .border(1.dpx, ArmillaColors.AmberCurrentStroke, CircleShape)
                                else Modifier
                            )
                            .padding(horizontal = 24.dpx),
                    horizontalArrangement = Arrangement.spacedBy(10.dpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step.status == StepStatus.DONE) CheckMark(ArmillaColors.Jade, Modifier.size(24.dpx))
                    Text(
                        step.direction.label,
                        color =
                            when (step.status) {
                                StepStatus.CURRENT -> ArmillaColors.Amber
                                StepStatus.DONE -> ArmillaColors.Paper
                                else -> ArmillaColors.Slate
                            },
                        fontFamily = ArmillaType.Body,
                        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                        fontSize = 22.spx,
                        maxLines = 1,
                        softWrap = false,
                    )
                    when (step.status) {
                        StepStatus.DONE ->
                            Text("${step.readingDeg}°", color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 30.spx)
                        StepStatus.SKIPPED ->
                            Text("未测", color = ArmillaColors.Slate, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val path = Path().apply { moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19.5f * u, 7f * u) }
        drawPath(path, color, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** T-07: direction name 24 px with 0.1 em spacing over the 176 px reading, shadowed for the sky. */
@Composable
fun BigReading(label: String, angleDeg: Int, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    val shadow = Shadow(Color(0xBF04090C), Offset(0f, 4f), 30f)
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        // Left of the reticle the reading hugs it from the other side.
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            label,
            color = ArmillaColors.Paper,
            style = TextStyle(shadow = shadow), // design-style: text-shadow only
            fontFamily = ArmillaType.Body,
            fontWeight = FontWeight.Medium,
            fontSize = 24.spx,
            letterSpacing = 0.1.em,
        )
        Text(
            "$angleDeg°",
            color = ArmillaColors.Paper,
            style = TextStyle(shadow = shadow),
            fontFamily = ArmillaType.Reading,
            fontSize = 176.spx,
            lineHeight = 0.86.em,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** T-08 / T-09: 940 px bar, 36 px corners: instruction on the left, speed meter on the right. */
@Composable
fun InstructionBar(state: SessionUiState, showSpeed: Boolean, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier.fillMaxSize(), cornerRadiusPx = 36) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dpx)) {
                Text(state.instruction, color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 26.spx, maxLines = 1)
                Text(state.instructionDetail, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx, maxLines = 1)
            }
            if (showSpeed) {
                Box(Modifier.padding(horizontal = 28.dpx).width(1.dpx).height(56.dpx).background(ArmillaColors.Divider))
                SpeedMeter(state.speedBars, state.speedLabel, state.speedIsOk)
            }
        }
    }
}

/** Five bars 8 px wide, 14 to 38 px tall; lit bars jade when the speed is fine, amber otherwise. */
@Composable
private fun SpeedMeter(bars: Int, label: String, ok: Boolean) {
    val lit = if (ok) ArmillaColors.Jade else ArmillaColors.Amber
    Row(horizontalArrangement = Arrangement.spacedBy(16.dpx), verticalAlignment = Alignment.CenterVertically) {
        Text("速度", color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dpx), verticalAlignment = Alignment.Bottom) {
            listOf(14, 20, 26, 32, 38).forEachIndexed { index, heightPx ->
                Box(
                    Modifier.width(8.dpx)
                        .height(heightPx.dpx)
                        .clip(RoundedCornerShape(4.dpx))
                        .background(if (index < bars) lit else ArmillaColors.Paper.copy(alpha = 0.2f))
                )
            }
        }
        Text(label, modifier = Modifier.width(72.dpx), color = lit, fontFamily = ArmillaType.Body, fontSize = 22.spx, maxLines = 1)
    }
}

/** Gaze reticle: idle ring, amber dwell arc from 12 o'clock, or jade ring with a tick and halo. */
@Composable
fun GazeReticle(look: ReticleLook, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val u = size.minDimension / 80f
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = 24f * u
        if (look == ReticleLook.RECORDED) {
            drawCircle(ArmillaColors.Jade.copy(alpha = 0.18f), 34f * u, c)
            drawCircle(ArmillaColors.Jade, r, c, style = Stroke(3.5f * u))
            val tick = Path().apply { moveTo(c.x - 10f * u, c.y + 0.5f * u); lineTo(c.x - 3f * u, c.y + 7f * u); lineTo(c.x + 11f * u, c.y - 8f * u) }
            drawPath(tick, ArmillaColors.Jade, style = Stroke(3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            return@Canvas
        }
        drawCircle(Color(0x590A1419), r, c)
        drawCircle(ArmillaColors.Paper.copy(alpha = 0.5f), r, c, style = Stroke(2f * u))
        if (look == ReticleLook.DWELLING && progress > 0f) {
            drawArc(
                ArmillaColors.Amber, -90f, 360f * progress, false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(3.5f * u, cap = StrokeCap.Round),
            )
        }
        drawCircle(ArmillaColors.Paper, 3.5f * u, c)
    }
}

/** Not drawn in the design (PRD §10): same panel material, the four choices in PRD order. */
@Composable
fun PausePanel(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onEndAndSave: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArmillaPanel(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dpx),
            verticalArrangement = Arrangement.spacedBy(16.dpx),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "已暂停",
                color = ArmillaColors.Paper,
                fontFamily = ArmillaType.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 42.spx,
                letterSpacing = 0.04.em,
            )
            ArmillaDivider()
            PrimaryButton("继续", onResume, Modifier.fillMaxWidth(), heightPx = 72, fontPx = 25)
            SecondaryButton("重新开始这一节", onRestart, Modifier.fillMaxWidth(), heightPx = 68)
            SecondaryButton("结束并保存", onEndAndSave, Modifier.fillMaxWidth(), heightPx = 68)
            SecondaryButton("退出不保存", onQuit, Modifier.fillMaxWidth(), heightPx = 68)
        }
    }
}
