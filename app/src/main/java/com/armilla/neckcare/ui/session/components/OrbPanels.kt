package com.armilla.neckcare.ui.session.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.components.ArmillaProgressBar
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** O-10: title in 宋体 26 px, 320 × 6 progress bar, "还剩" 20 px + time in Instrument Serif 32 px. */
@Composable
fun ExerciseStatusBar(title: String, progress: Float, remaining: String, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier, cornerRadiusPx = 999) {
        Row(
            modifier = Modifier.padding(horizontal = 36.dpx, vertical = 18.dpx),
            horizontalArrangement = Arrangement.spacedBy(28.dpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = ArmillaColors.Paper, fontFamily = ArmillaType.Title, fontWeight = FontWeight.SemiBold, fontSize = 26.spx, maxLines = 1, softWrap = false)
            ArmillaProgressBar(progress, widthPx = 320)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dpx), verticalAlignment = Alignment.Bottom) {
                Text("还剩", color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 20.spx)
                Text(remaining, color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 32.spx, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** One calm line under the status bar, e.g. "慢一点也没关系". */
@Composable
fun GentleHint(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = ArmillaColors.Paper,
            style = TextStyle(shadow = Shadow(ArmillaColors.Ink.copy(alpha = 0.85f), Offset(0f, 2f), 18f)), // design-style: text-shadow only
            fontFamily = ArmillaType.Body,
            fontSize = 22.spx,
        )
    }
}
