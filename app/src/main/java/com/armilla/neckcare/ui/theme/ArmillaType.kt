package com.armilla.neckcare.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.armilla.neckcare.R

/** The three typefaces of the design: titles, readings, body. All SIL OFL, subset at build time. */
object ArmillaType {
    /** 思源宋体 600: panel titles and greetings. */
    val Title = FontFamily(Font(R.font.noto_serif_sc_semibold, FontWeight.SemiBold))

    /** Instrument Serif: every angle, time and count. */
    val Reading =
        FontFamily(
            Font(R.font.instrument_serif_regular, FontWeight.Normal),
            Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
        )

    /** 思源黑体 400/500/600: instructions, labels, buttons. */
    val Body =
        FontFamily(
            Font(R.font.noto_sans_sc_regular, FontWeight.Normal),
            Font(R.font.noto_sans_sc_medium, FontWeight.Medium),
            Font(R.font.noto_sans_sc_semibold, FontWeight.SemiBold),
        )
}
