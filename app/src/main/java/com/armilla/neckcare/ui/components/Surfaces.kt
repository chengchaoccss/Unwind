package com.armilla.neckcare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.armilla.neckcare.ui.theme.ArmillaColors
import com.armilla.neckcare.ui.theme.ArmillaType
import com.armilla.neckcare.ui.theme.dpx
import com.armilla.neckcare.ui.theme.spx
import com.pico.spatial.ui.design.Text

/**
 * The design's panel material: 黛夜 at 84 %, a 1 px paper-white edge at 16 % and a paper-white
 * highlight along the top edge. Corner radius follows the hierarchy: panels 32, result panel and
 * instruction bar 36, pills fully round.
 */
@Composable
fun ArmillaPanel(
    modifier: Modifier = Modifier,
    cornerRadiusPx: Int = 32,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadiusPx.dpx)
    val highlight = ArmillaColors.PanelHighlight
    Box(
        modifier =
            modifier
                .clip(shape)
                // design-style: opaque custom surface; the design's own dark material, not system glass
                .background(ArmillaColors.PanelFill)
                .border(1.dpx, ArmillaColors.PanelStroke, shape)
                .drawWithContent {
                    drawContent()
                    // inset 0 1px 0 rgba(239,233,220,0.18)
                    drawLine(
                        brush =
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, highlight, highlight, Color.Transparent)
                            ),
                        start = Offset(size.width * 0.04f, 1f),
                        end = Offset(size.width * 0.96f, 1f),
                        strokeWidth = 2f,
                    )
                }
    ) {
        content()
    }
}

/** 1 px rule, paper white 14 %. */
@Composable
fun ArmillaDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dpx).background(ArmillaColors.Divider))
}

/** Reading tag: name in mist 20 px, value in Instrument Serif 32 px, on a dark 14 px-radius chip. */
@Composable
fun ReadingTag(
    label: String,
    value: String?,
    valueColor: Color = ArmillaColors.Paper,
    modifier: Modifier = Modifier,
    labelColor: Color = ArmillaColors.Mist,
    valueSizePx: Int = 32,
    trailing: String? = null,
) {
    val shape = RoundedCornerShape(14.dpx)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(ArmillaColors.TagFill)
                .border(1.dpx, ArmillaColors.TagStroke, shape)
                .padding(horizontal = 14.dpx, vertical = 5.dpx),
        horizontalArrangement = Arrangement.spacedBy(8.dpx),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            color = labelColor,
            fontFamily = ArmillaType.Body,
            fontSize = 20.spx,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
        if (value != null) {
            Text(
                value,
                color = valueColor,
                fontFamily = ArmillaType.Reading,
                fontSize = valueSizePx.spx,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                color = labelColor,
                fontFamily = ArmillaType.Body,
                fontSize = 20.spx,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
