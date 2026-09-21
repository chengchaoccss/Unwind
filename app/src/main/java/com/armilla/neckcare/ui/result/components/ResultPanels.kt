package com.armilla.neckcare.ui.result.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.ui.components.ArmillaDivider
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.components.ArmillaSwitch
import com.armilla.neckcare.ui.components.DifferenceTag
import com.armilla.neckcare.ui.components.PrimaryButton
import com.armilla.neckcare.ui.components.SecondaryButton
import com.armilla.neckcare.ui.result.AxisRow
import com.armilla.neckcare.ui.result.ResultUiState
import com.armilla.neckcare.ui.result.SideReading
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** R-01: 720 px main panel, 36 px corners, padding 40 / 48 / 36, 18 px between blocks. */
@Composable
fun ResultPanel(
    state: ResultUiState,
    saveFailed: Boolean,
    onRetest: () -> Unit,
    onDone: () -> Unit,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArmillaPanel(modifier = modifier.fillMaxSize(), cornerRadiusPx = 36) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 48.dpx, end = 48.dpx, top = 40.dpx, bottom = 36.dpx),
            verticalArrangement = Arrangement.spacedBy(18.dpx),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dpx)) {
                    Text(
                        "今天的活动度",
                        color = ArmillaColors.Paper,
                        fontFamily = ArmillaType.Title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 42.spx,
                        letterSpacing = 0.04.em,
                    )
                    Text(state.dateLine, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(state.totalText, color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 76.spx, lineHeight = 0.95.em)
                    Text(
                        state.totalCaption,
                        color = if (state.totalCaptionIsPositive) ArmillaColors.Jade else ArmillaColors.Mist,
                        fontFamily = ArmillaType.Body,
                        fontSize = 20.spx,
                        textAlign = TextAlign.End,
                    )
                }
            }
            ArmillaDivider()
            state.rows.forEach { DivergingRow(it) }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dpx, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saveFailed) {
                    Text("这次没存上", modifier = Modifier.weight(1f), color = ArmillaColors.Amber, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                    SecondaryButton("再存一次", onRetrySave, heightPx = 68)
                }
                SecondaryButton("再测一次", onRetest, heightPx = 68, horizontalPaddingPx = 36)
                PrimaryButton("完成", onDone, heightPx = 68, fontPx = 25, horizontalPaddingPx = 56, letterSpacingEm = 0.1f)
            }
        }
    }
}

/** Row title with optional difference tag, then label / bar / label. */
@Composable
private fun DivergingRow(row: AxisRow) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dpx), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.title,
                color = ArmillaColors.Slate,
                fontFamily = ArmillaType.Body,
                fontWeight = FontWeight.Medium,
                fontSize = 20.spx,
                letterSpacing = 0.2.em,
            )
            row.differenceTag?.let { DifferenceTag(it) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SideLabel(row.left, alignEnd = true)
            Spacer(Modifier.width(20.dpx))
            DivergingBar(row.left, row.right, Modifier.width(372.dpx).height(56.dpx))
            Spacer(Modifier.width(20.dpx))
            SideLabel(row.right, alignEnd = false)
        }
    }
}

@Composable
private fun SideLabel(reading: SideReading, alignEnd: Boolean) {
    Column(modifier = Modifier.width(118.dpx), horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(reading.label, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
        if (reading.valueDeg != null)
            Text("${reading.valueDeg}°", color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 40.spx, lineHeight = 1.0.em)
        else Text("未测", color = ArmillaColors.Slate, fontFamily = ArmillaType.Body, fontSize = 22.spx)
    }
}

/**
 * Each half is 186 px wide at 2 px per degree; bars are 14 px tall, fully round outside and 3 px
 * by the axis, with an 18 px glow; the last reading is a 2 × 32 px paper-white line.
 */
@Composable
private fun DivergingBar(left: SideReading, right: SideReading, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 372f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun bar(reading: SideReading, direction: Float) {
            val value = reading.valueDeg ?: return
            val color = colorOf(reading.side)
            val length = (value * 2f).coerceAtMost(186f) * u
            val x0 = if (direction < 0) cx - length else cx
            for (i in 3 downTo 1) {
                val grow = 6f * i * u
                drawRoundRect(
                    color.copy(alpha = 0.05f),
                    Offset(x0 - grow / 2, cy - 7f * u - grow / 2),
                    Size(length + grow, 14f * u + grow),
                    CornerRadius(7f * u + grow / 2),
                )
            }
            drawRoundRect(color, Offset(x0, cy - 7f * u), Size(length, 14f * u), CornerRadius(7f * u))
            // Square off the end that meets the axis to a 3 px radius.
            val innerX = if (direction < 0) cx - 7f * u else cx
            drawRoundRect(color, Offset(innerX, cy - 7f * u), Size(7f * u, 14f * u), CornerRadius(3f * u))
            reading.lastDeg?.let { last ->
                val x = cx + direction * (last * 2f).coerceAtMost(186f) * u
                drawRect(ArmillaColors.Paper.copy(alpha = 0.8f), Offset(x - 1f * u, cy - 16f * u), Size(2f * u, 32f * u))
            }
        }
        bar(left, -1f)
        bar(right, 1f)
        drawRect(ArmillaColors.Paper.copy(alpha = 0.4f), Offset(cx - 0.5f * u, 0f), Size(1f * u, 56f * u))
    }
}

private fun colorOf(side: Side): Color =
    when (side) {
        Side.LEFT -> ArmillaColors.Jade
        Side.RIGHT -> ArmillaColors.Amber
        Side.CENTER -> ArmillaColors.Paper
    }

/** R-03: "接下来一周", 368 px wide, padding 36. */
@Composable
fun NextWeekPanel(state: ResultUiState, autoAdjust: Boolean, onAutoAdjust: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(36.dpx), verticalArrangement = Arrangement.spacedBy(22.dpx)) {
            Text("接下来一周", color = ArmillaColors.Paper, fontFamily = ArmillaType.Title, fontWeight = FontWeight.SemiBold, fontSize = 32.spx)
            Text(state.nextWeekText, color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 22.spx, lineHeight = 1.65.em)
            ArmillaDivider()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("自动调整练习", modifier = Modifier.weight(1f), color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 22.spx)
                ArmillaSwitch(autoAdjust, onAutoAdjust, label = "自动调整练习")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dpx)) {
                Text("工作日提醒", color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                Row(horizontalArrangement = Arrangement.spacedBy(28.dpx)) {
                    state.reminderTimes.forEach {
                        Text(it, color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 32.spx)
                    }
                }
            }
        }
    }
}

/** R-02: caption under the small armillary. */
@Composable
fun ArmillaryUpdatedTag(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        com.armilla.neckcare.ui.components.ReadingTag(label = "三环仪已更新到今天", value = null)
    }
}
