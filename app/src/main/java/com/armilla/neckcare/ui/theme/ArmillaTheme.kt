package com.armilla.neckcare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * Wraps content in [PicoTheme] with the design's palette mapped onto all 16 public colour roles.
 * Roles the design does not define keep the system value, assigned explicitly.
 */
@Composable
fun ArmillaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme =
        remember(context) {
            val system = systemColorScheme(context)
            system.copy(
                fillPrimary = ArmillaColors.PanelFill,
                fillSecondary = ArmillaColors.TagFill,
                fillTertiary = ArmillaColors.ConsoleSelectedFill,
                fillLight = ArmillaColors.TrackFill,
                labelPrimaryLight = ArmillaColors.OnAmber,
                labelPrimary = ArmillaColors.Paper,
                labelSecondary = ArmillaColors.Mist,
                labelTertiary = ArmillaColors.Slate,
                labelQuaternary = ArmillaColors.Slate.copy(alpha = 0.6f),
                lightenHover = Color.White.copy(alpha = 0.06f),
                lightenPressed = Color.White.copy(alpha = 0.10f),
                error = system.error,
                alert = ArmillaColors.Amber,
                passable = ArmillaColors.Jade,
                interaction = ArmillaColors.Amber,
                dividerLine = ArmillaColors.Divider,
            )
        }
    PicoTheme(colorScheme = scheme, content = content)
}
