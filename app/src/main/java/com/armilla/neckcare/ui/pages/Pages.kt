package com.armilla.neckcare.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.armilla.neckcare.data.repository.Posture
import com.armilla.neckcare.data.repository.Settings
import com.armilla.neckcare.domain.model.Side
import com.armilla.neckcare.ui.components.ArmillaDivider
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.components.ArmillaSwitch
import com.armilla.neckcare.ui.components.PrimaryButton
import com.armilla.neckcare.ui.components.SecondaryButton
import com.armilla.neckcare.ui.navigation.MainEvent
import com.armilla.neckcare.ui.records.RecordsEvent
import com.armilla.neckcare.ui.records.RecordsRange
import com.armilla.neckcare.ui.records.RecordsUiState
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

/** Shared frame of every full page: the design's panel, a 宋体 title, 18 px between blocks. */
@Composable
private fun PageFrame(title: String, spacingPx: Int = 18, content: @Composable ColumnScope.() -> Unit) {
    ArmillaPanel(modifier = Modifier.fillMaxSize(), cornerRadiusPx = 36) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 48.dpx, vertical = 40.dpx),
            verticalArrangement = Arrangement.spacedBy(spacingPx.dpx),
        ) {
            Text(title, color = ArmillaColors.Paper, fontFamily = ArmillaType.Title, fontWeight = FontWeight.SemiBold, fontSize = 42.spx, letterSpacing = 0.04.em)
            content()
        }
    }
}

@Composable
private fun Body(text: String, color: Color = ArmillaColors.Paper, sizePx: Int = 24) {
    Text(text, color = color, fontFamily = ArmillaType.Body, fontSize = sizePx.spx, lineHeight = 1.6.em)
}

/** PRD §13 健康提示: four statements, confirmed before anything else; can be read again later. */
@Composable
fun HealthNoticePage(onAccept: () -> Unit) {
    PageFrame("开始之前") {
        listOf(
            "这是一个帮助养成活动习惯的工具，不能代替医生的诊断和治疗。",
            "有颈椎病史、眩晕、近期颈部外伤或手术的人，请先问过医生。",
            "练习中出现疼痛、头晕、手麻或恶心，请立即停下并摘下头显。",
            "所有动作都慢慢做，到自然停住的位置就好，不用使劲。",
        ).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dpx)) {
                Box(Modifier.padding(top = 15.dpx).size(7.dpx).clip(CircleShape).background(ArmillaColors.Jade))
                Body(line)
            }
        }
        Body("把头带调稳再开始，头显不晃，脖子更轻松。", ArmillaColors.Mist, 20)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PrimaryButton("我知道了", onAccept, heightPx = 68, fontPx = 25, horizontalPaddingPx = 56)
        }
    }
}

@Composable
fun PosturePage(onChoose: (Posture) -> Unit) {
    PageFrame("怎么练") {
        Body("坐着练更稳，读数也更准。请用稳固的椅子，带轮的椅子先锁住。")
        Body("之后可以在设置里改。", ArmillaColors.Mist, 20)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dpx, Alignment.End)) {
            SecondaryButton("站着练", { onChoose(Posture.STANDING) }, heightPx = 68)
            PrimaryButton("坐着练", { onChoose(Posture.SEATED) }, heightPx = 68, fontPx = 25, horizontalPaddingPx = 56)
        }
    }
}

@Composable
fun ReminderSetupPage(times: List<String>, onChoose: (Boolean) -> Unit) {
    PageFrame("工作日提醒") {
        Body("工作日的 ${times.joinToString(" 和 ")}，提醒你花几分钟活动一下。时间可以在设置里改。")
        Body("提醒只在头显里出现，没戴头显时收不到。", ArmillaColors.Mist, 20)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dpx, Alignment.End)) {
            SecondaryButton("先不用", { onChoose(false) }, heightPx = 68)
            PrimaryButton("打开提醒", { onChoose(true) }, heightPx = 68, fontPx = 25, horizontalPaddingPx = 48)
        }
    }
}

/** MVP placeholder (PRD §10): one line. */
@Composable
fun CoursesPage() {
    PageFrame("课程") { Body("课程还在准备中", ArmillaColors.Mist) }
}

@Composable
fun DeleteConfirmPage(onCancel: () -> Unit, onConfirm: () -> Unit) {
    PageFrame("删除全部数据") {
        Body("所有测量、练习记录和设置都会从这台头显上删除，删除后无法恢复。之后会回到第一次打开时的样子。")
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dpx, Alignment.End)) {
            SecondaryButton("全部删除", onConfirm, heightPx = 68)
            PrimaryButton("先不删", onCancel, heightPx = 68, fontPx = 25, horizontalPaddingPx = 56)
        }
    }
}

/** 记录: range chips, total trend, one line per direction, four weeks of check-in dots. */
@Composable
fun RecordsPage(state: RecordsUiState, onEvent: (RecordsEvent) -> Unit) {
    PageFrame("记录", spacingPx = 11) {
        if (state.empty) {
            Body("还没有记录。先做一次 30 秒测试，这里就会出现第一条。", ArmillaColors.Mist)
            return@PageFrame
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dpx)) {
            RecordsRange.entries.forEach { range -> Choice(range.label, state.range == range) { onEvent(RecordsEvent.Range(range)) } }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dpx)) {
            Text("${state.totals.lastOrNull() ?: 0}°", color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 64.spx, lineHeight = 1.0.em)
            Body("六个方向合计，${state.firstLabel} 至 ${state.lastLabel}", ArmillaColors.Mist, 20)
        }
        Sparkline(state.totals, ArmillaColors.Paper, Modifier.fillMaxWidth().height(78.dpx), endDot = true)
        ArmillaDivider()
        Body(state.selectedLabel, ArmillaColors.Mist, 20)
        state.series.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dpx)) {
                pair.forEach { s ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dpx)) {
                        Text(s.direction.label, modifier = Modifier.width(80.dpx), color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                        Text(
                            s.shownDeg?.let { "$it°" } ?: "未测",
                            modifier = Modifier.width(64.dpx),
                            color = ArmillaColors.Paper,
                            fontFamily = ArmillaType.Reading,
                            fontSize = 32.spx,
                        )
                        Sparkline(s.values, sideColor(s.direction.side), Modifier.weight(1f).height(36.dpx), endDot = false)
                    }
                }
            }
        }
        ArmillaDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.calendar.forEach { day ->
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(20.dpx)
                        .clip(CircleShape)
                        .background(if (day.hasRecord) ArmillaColors.Jade else ArmillaColors.TrackFill)
                        .then(if (day.date == state.selected) Modifier.border(2.dpx, ArmillaColors.Amber, CircleShape) else Modifier)
                        .clickable(source, LocalIndication.current, enabled = day.hasRecord, role = Role.Button) {
                            onEvent(RecordsEvent.SelectDay(day.date))
                        }
                )
            }
        }
        Body("最近四周，点亮的是有记录的日子，点一下看当天读数。", ArmillaColors.Slate, 20)
    }
}

@Composable
private fun Sparkline(values: List<Int>, color: Color, modifier: Modifier, endDot: Boolean) {
    Canvas(modifier) {
        if (values.size < 2) {
            if (values.size == 1) drawCircle(color, size.height * 0.08f, Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val low = values.min() - 2f
        val high = values.max() + 2f
        val pad = size.height * 0.12f
        fun x(i: Int) = pad + (size.width - 2 * pad) * i / (values.size - 1)
        fun y(v: Int) = size.height - pad - (v - low) / (high - low) * (size.height - 2 * pad)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, color, style = Stroke(size.height * 0.035f + 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (endDot) {
            val end = Offset(x(values.lastIndex), y(values.last()))
            drawCircle(ArmillaColors.Amber.copy(alpha = 0.22f), size.height * 0.13f, end)
            drawCircle(ArmillaColors.Amber, size.height * 0.06f, end)
        }
    }
}

/** 设置 (PRD §10): every item of the list, one row each. */
@Composable
fun SettingsPage(settings: Settings, message: String?, onEvent: (MainEvent) -> Unit) {
    PageFrame("设置", spacingPx = 8) {
        SettingRow("工作日提醒") { ArmillaSwitch(settings.remindersEnabled, { onEvent(MainEvent.SetReminders(it)) }, label = "工作日提醒") }
        SettingRow("提醒时间") {
            settings.reminderTimes.forEachIndexed { i, time ->
                Stepper(time, { onEvent(MainEvent.ShiftReminder(i, -30)) }, { onEvent(MainEvent.ShiftReminder(i, 30)) }, reading = true)
            }
        }
        SettingRow("练习姿势") {
            Posture.entries.forEach { p -> Choice(p.label, settings.posture == p) { onEvent(MainEvent.SetPosture(p)) } }
        }
        SettingRow("自动调整练习") { ArmillaSwitch(settings.autoAdjust, { onEvent(MainEvent.SetAutoAdjust(it)) }, label = "自动调整练习") }
        SettingRow("引导环大小") {
            Stepper("${(settings.ringRadiusM * 100).toInt()} cm", { onEvent(MainEvent.NudgeRing(-0.02f)) }, { onEvent(MainEvent.NudgeRing(0.02f)) })
        }
        SettingRow("环境音量") {
            Stepper(
                if (settings.ambientVolume <= 0f) "关" else "${(settings.ambientVolume * 100).toInt()}%",
                { onEvent(MainEvent.NudgeVolume(-0.1f)) },
                { onEvent(MainEvent.NudgeVolume(0.1f)) },
            )
        }
        ArmillaDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dpx)) {
            SecondaryButton("重新校准", { onEvent(MainEvent.Recentre) }, heightPx = 64, horizontalPaddingPx = 20)
            SecondaryButton("导出数据", { onEvent(MainEvent.Export) }, heightPx = 64, horizontalPaddingPx = 20)
            SecondaryButton("健康提示", { onEvent(MainEvent.ReviewHealthNotice) }, heightPx = 64, horizontalPaddingPx = 20)
            SecondaryButton("删除全部数据", { onEvent(MainEvent.AskDelete) }, heightPx = 64, horizontalPaddingPx = 20)
        }
        message?.let { Body(it, ArmillaColors.Jade, 20) }
    }
}

@Composable
private fun SettingRow(label: String, controls: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(66.dpx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dpx),
    ) {
        Text(label, modifier = Modifier.weight(1f), color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 24.spx)
        controls()
    }
}

/** A value between a minus and a plus; each target is 64 px, the design's minimum. */
@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, reading: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dpx)) {
        RoundGlyph("−", onMinus)
        Text(
            value,
            modifier = Modifier.width(if (reading) 84.dpx else 96.dpx),
            color = ArmillaColors.Paper,
            fontFamily = if (reading) ArmillaType.Reading else ArmillaType.Body,
            fontSize = (if (reading) 32 else 22).spx,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        RoundGlyph("+", onPlus)
    }
}

@Composable
private fun RoundGlyph(glyph: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier.size(64.dpx)
                .clip(CircleShape)
                .background(ArmillaColors.SecondaryFill)
                .border(1.dpx, ArmillaColors.SecondaryStroke, CircleShape)
                .spatialHoverEffect()
                .clickable(source, LocalIndication.current, role = Role.Button, onClick = onClick)
                .controllerHapticFeedback(interactionSource = source),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 30.spx)
    }
}

/** One of a small set: amber outline and text when chosen. */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier.height(64.dpx)
                .clip(CircleShape)
                .background(if (selected) ArmillaColors.AmberCurrentFill else ArmillaColors.SecondaryFill)
                .border(1.dpx, if (selected) ArmillaColors.AmberCurrentStroke else ArmillaColors.SecondaryStroke, CircleShape)
                .spatialHoverEffect()
                .clickable(source, LocalIndication.current, role = Role.RadioButton, onClick = onClick)
                .controllerHapticFeedback(interactionSource = source)
                .padding(horizontal = 30.dpx),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) ArmillaColors.Amber else ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 22.spx, maxLines = 1, softWrap = false)
    }
}

private fun sideColor(side: Side): Color =
    when (side) {
        Side.LEFT -> ArmillaColors.Jade
        Side.RIGHT -> ArmillaColors.Amber
        Side.CENTER -> ArmillaColors.Paper
    }
