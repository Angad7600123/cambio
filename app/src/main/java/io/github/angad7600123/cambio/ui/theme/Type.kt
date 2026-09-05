package io.github.angad7600123.cambio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography for Cambio.
 *
 * Uses the system font rather than bundling or downloading one — it keeps the app
 * small, renders natively on every OEM skin, and picks up the user's font
 * preferences.
 *
 * The display styles use light weights at large sizes with negative tracking,
 * which is what gives big numbers a calm, precise look instead of a heavy one. All
 * sizes are in `sp`, so they scale with the user's font-size setting.
 */
internal val CambioTypography = Typography()

/** Styles specific to the calculator surface, kept apart from the Material scale. */
object CambioTextStyles {
    /**
     * The large display line. 45sp matches One UI's calculator; the actual size is
     * stepped down at runtime as the content grows.
     */
    val Display = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 45.sp,
        letterSpacing = (-1).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    /**
     * The small line under the display: the running preview while typing, or the
     * expression that produced the result after equals.
     */
    val Secondary = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp,
    )

    /** The converted amount beneath the result. */
    val Converted = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        letterSpacing = (-1).sp,
    )

    /** Glyphs on the keypad. */
    val Key = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        letterSpacing = 0.sp,
    )

    /** Currency codes on the selector chips. */
    val CurrencyCode = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp,
    )

    /** The live rate line and timestamps. */
    val Meta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.sp,
    )
}
