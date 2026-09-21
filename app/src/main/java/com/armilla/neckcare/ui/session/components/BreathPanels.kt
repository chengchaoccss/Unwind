package com.armilla.neckcare.ui.session.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** 吸气 / 呼气: 宋体 600, 50 px, 0.36 em apart, cross-fading a little after each turn of the breath. */
@Composable
fun BreathCue(inhaleAlpha: Float, exhaleAlpha: Float, modifier: Modifier = Modifier) {
    val shadow = Shadow(ArmillaColors.Ink.copy(alpha = 0.85f), Offset(0f, 2f), 26f)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        listOf("吸气" to inhaleAlpha, "呼气" to exhaleAlpha).forEach { (word, alpha) ->
            Text(
                word,
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
                color = ArmillaColors.Paper,
                style = TextStyle(shadow = shadow), // design-style: text-shadow only
                fontFamily = ArmillaType.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 50.spx,
                letterSpacing = 0.36.em,
            )
        }
    }
}
