package com.armilla.neckcare.ui.session.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.armilla.neckcare.ui.components.ArmillaDivider
import com.armilla.neckcare.ui.components.ArmillaPanel
import com.armilla.neckcare.ui.session.SessionUiState
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/** S-06: "肩部环绕" 22 px, direction in 宋体 42 px, lap count 150 px + "共 8 圈", rule, hint 22 px. */
@Composable
fun ShoulderCentrePanel(state: SessionUiState, modifier: Modifier = Modifier) {
    ArmillaPanel(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dpx, vertical = 36.dpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dpx, Alignment.CenterVertically),
        ) {
            Text("肩部环绕", color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 22.spx, letterSpacing = 0.08.em)
            Text(
                state.shoulderTitle,
                color = ArmillaColors.Paper,
                fontFamily = ArmillaType.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (state.shoulderCounting) 42.spx else 32.spx,
                textAlign = TextAlign.Center,
                lineHeight = 1.4.em,
            )
            if (state.shoulderCounting) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dpx), verticalAlignment = Alignment.Bottom) {
                    Text("${state.shoulderLaps}", color = ArmillaColors.Paper, fontFamily = ArmillaType.Reading, fontSize = 150.spx, lineHeight = 0.9.em)
                    Text("共 8 圈", modifier = Modifier.padding(bottom = 18.dpx), color = ArmillaColors.Mist, fontFamily = ArmillaType.Body, fontSize = 24.spx)
                }
                ArmillaDivider()
                Text(state.shoulderHint, color = ArmillaColors.Paper, fontFamily = ArmillaType.Body, fontSize = 22.spx, textAlign = TextAlign.Center, lineHeight = 1.5.em)
            }
        }
    }
}
