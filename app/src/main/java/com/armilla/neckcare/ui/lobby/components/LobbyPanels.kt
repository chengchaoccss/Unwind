package com.armilla.neckcare.ui.lobby.components

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.armilla.neckcare.ui.components.ArmillaDivider
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.components.PrimaryButton
import com.armilla.neckcare.ui.components.SecondaryButton
import com.armilla.neckcare.ui.lobby.LobbyUiState
import com.armilla.neckcare.ui.lobby.TrendUi
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** H-07 左面板 "今天": greeting, today's four minutes, next reminder. 440 px wide, padding 40, gap 26. */
@Composable
fun TodayPanel(state: LobbyUiState, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dpx),
            verticalArrangement = Arrangement.spacedBy(26.dpx),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dpx)) {
                Text(
                    state.greeting,
                    color = ArmillaColors.Paper,
                    fontFamily = ArmillaType.Title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 48.spx,
                    letterSpacing = 0.04.em,
                )
                Text(state.dateLine, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 22.spx)
            }
            ArmillaDivider()
            Column {
                Text(
                    "今天的 5 分钟",
                    color = ArmillaColors.Mist,
                    fontFamily = ArmillaType.Body,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.spx,
                )
                Spacer(Modifier.height(10.dpx))
                state.plan.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(54.dpx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.ordinal,
                            modifier = Modifier.width(38.dpx),
                            color = ArmillaColors.Slate,
                            fontFamily = ArmillaType.Reading,
                            fontStyle = FontStyle.Italic,
                            fontSize = 26.spx,
                        )
                        Text(
                            row.name,
                            modifier = Modifier.weight(1f),
                            color = ArmillaColors.Paper,
                            fontFamily = ArmillaType.Body,
                            fontSize = 24.spx,
                        )
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dpx)) {
                            Text(row.amount, color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 30.spx)
                            Text(row.unit, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                        }
                    }
                }
            }
            ArmillaDivider()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "下次提醒",
                    modifier = Modifier.weight(1f),
                    color = ArmillaColors.Mist,
                    fontFamily = ArmillaType.Body,
                    fontSize = 22.spx,
                )
                Text(state.nextReminder, color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 30.spx)
            }
        }
    }
}

/** H-08 右面板 "近 14 天": total, change, 360 × 176 line chart, left/right balance. Gap 22. */
@Composable
fun TrendPanel(state: LobbyUiState, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dpx),
            verticalArrangement = Arrangement.spacedBy(22.dpx),
        ) {
            Text(
                "近 14 天",
                color = ArmillaColors.Paper,
                fontFamily = ArmillaType.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.spx,
            )
            val trend = state.trend
            if (trend == null) {
                Text(
                    state.emptyTrendText,
                    color = ArmillaColors.Mist,
                    fontFamily = ArmillaType.Body,
                    fontSize = 22.spx,
                    lineHeight = 1.6.em,
                )
                return@Column
            }
            Text(
                trend.totalText,
                color = ArmillaColors.Paper,
                fontFamily = ArmillaType.Reading,
                fontSize = 120.spx,
                lineHeight = 0.9.em,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "六个方向合计",
                    modifier = Modifier.weight(1f),
                    color = ArmillaColors.Mist,
                    fontFamily = ArmillaType.Body,
                    fontSize = 22.spx,
                )
                trend.changeText?.let {
                    Text(
                        it,
                        color = if (trend.changeIsPositive) ArmillaColors.Jade else ArmillaColors.Mist,
                        fontFamily = ArmillaType.Body,
                        fontSize = 22.spx,
                    )
                }
            }
            TrendChart(trend, Modifier.width(360.dpx).height(176.dpx))
            if (state.balanceHeadline != null) {
                ArmillaDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dpx)) {
                    Text(state.balanceHeadline, color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 24.spx)
                    state.balanceDetail?.let {
                        Text(it, color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                    }
                }
            }
        }
    }
}

/** Paper-white 2.2 px line, two 12 % reference lines, amber end point with a 22 % halo. */
@Composable
private fun TrendChart(trend: TrendUi, modifier: Modifier = Modifier) {
    Box(modifier) {
        val labelWidth = 42
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 360f
            val left = labelWidth * u
            val top = 10f * u
            val bottom = size.height - 40f * u
            val low = minOf(trend.values.min(), trend.referenceLines.min()) - 6
            val high = maxOf(trend.values.max(), trend.referenceLines.max()) + 6
            fun y(v: Int) = bottom - (v - low).toFloat() / (high - low) * (bottom - top)
            fun x(i: Int) = left + (size.width - left - 14f * u) * i / (trend.values.size - 1).coerceAtLeast(1)

            trend.referenceLines.forEach { v ->
                drawLine(ArmillaColors.Paper.copy(alpha = 0.12f), Offset(left, y(v)), Offset(size.width, y(v)), 1f * u)
            }
            val path = Path()
            trend.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, ArmillaColors.Paper, style = Stroke(2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val end = Offset(x(trend.values.lastIndex), y(trend.values.last()))
            drawCircle(ArmillaColors.Amber.copy(alpha = 0.22f), 13f * u, end)
            drawCircle(ArmillaColors.Amber, 6f * u, end)
        }
        // Axis labels: Instrument Serif 22 px in slate; the x axis names only the first and last day.
        trend.referenceLines.forEach { v ->
            val low = minOf(trend.values.min(), trend.referenceLines.min()) - 6
            val high = maxOf(trend.values.max(), trend.referenceLines.max()) + 6
            val fraction = 1f - (v - low).toFloat() / (high - low)
            Text(
                v.toString(),
                modifier = Modifier.padding(top = (10 + (176 - 50) * fraction - 13).coerceAtLeast(0f).dpx),
                color = ArmillaColors.Slate,
                fontFamily = ArmillaType.Reading,
                fontSize = 22.spx,
            )
        }
        Row(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = labelWidth.dpx)) {
            Text(
                trend.firstDateLabel,
                modifier = Modifier.weight(1f),
                color = ArmillaColors.Slate,
                fontFamily = ArmillaType.Reading,
                fontSize = 22.spx,
            )
            Text(
                trend.lastDateLabel,
                color = ArmillaColors.Slate,
                fontFamily = ArmillaType.Reading,
                fontSize = 22.spx,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** H-02: caption above the armillary; shadowed so it stays readable over the sky. */
@Composable
fun ArmillaryCaption(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val shadow = Shadow(color = ArmillaColors.Ink.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 18f)
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dpx, Alignment.CenterVertically),
    ) {
        Text(
            title,
            color = ArmillaColors.Paper,
            style = TextStyle(shadow = shadow), // design-style: text-shadow only, size set below
            fontFamily = ArmillaType.Title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.spx,
            letterSpacing = 0.04.em,
        )
        Text(
            subtitle,
            color = ArmillaColors.Mist,
            style = TextStyle(shadow = shadow),
            fontFamily = ArmillaType.Body,
            fontSize = 20.spx,
        )
    }
}

/** H-05 / H-06: "开始练习" amber 80 px, and "只做测试" 210 × 80, 16 px apart. */
@Composable
fun LobbyActions(onStart: () -> Unit, onTestOnly: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dpx, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryButton("开始练习", onStart, modifier = Modifier.width(314.dpx), heightPx = 80, fontPx = 27)
        SecondaryButton("只做测试", onTestOnly, modifier = Modifier.width(210.dpx), heightPx = 80, fontPx = 22)
    }
}
