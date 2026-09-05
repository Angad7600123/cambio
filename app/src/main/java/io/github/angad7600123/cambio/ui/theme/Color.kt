package io.github.angad7600123.cambio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Cambio palette.
 *
 * The dark values are sampled from One UI's calculator so the app sits naturally
 * next to it on a Samsung device: a true-black canvas, near-black digit keys, a
 * deliberately *lighter* shade for the operator column, coral for the two
 * destructive keys, and a single filled jade key for equals.
 *
 * Two different greens are needed. [DarkAccent] fills the equals key, where a
 * saturated mint would glare. [DarkAccentText] is for glyphs on the black canvas —
 * the operators inside the expression and the caret — where the filled tone would
 * be far too dark to read.
 *
 * The light scheme mirrors those roles rather than inverting them, so the same
 * meanings survive: destructive is still coral, operators still read as a distinct
 * column, equals is still the one filled key.
 *
 * Every foreground/background pair was checked against WCAG 2.1 AA. The light
 * coral is deliberately darker than the dark-theme coral because it has to carry
 * 4.5:1 against a near-white key.
 */

// Dark scheme (default look), sampled from One UI Calculator.
internal val DarkCanvas = Color(0xFF000000)
internal val DarkSurface = Color(0xFF121214)

/** Digits, clear, backspace and percent. */
internal val DarkKey = Color(0xFF171619)
internal val DarkKeyPressed = Color(0xFF2A292D)

/** The operator column, one step lighter so it reads as its own group. */
internal val DarkOperatorKey = Color(0xFF272727)
internal val DarkOperatorKeyPressed = Color(0xFF3A3A3A)

internal val DarkOnKey = Color(0xFFFFFFFF)
internal val DarkTextPrimary = Color(0xFFFBFBFB)
internal val DarkTextSecondary = Color(0xFF808080)
internal val DarkDestructive = Color(0xFFEF6670)
internal val DarkAccent = Color(0xFF187D6F)
internal val DarkAccentPressed = Color(0xFF229486)
internal val DarkOnAccent = Color(0xFFFFFFFF)
internal val DarkOutline = Color(0xFF2A2A2C)

/**
 * Mint, for accent *glyphs* on the black canvas — expression operators and the
 * caret. The filled [DarkAccent] would not carry as text at this size.
 */
internal val DarkAccentText = Color(0xFF5DD4B9)

// Light scheme.
internal val LightCanvas = Color(0xFFFFFFFF)
internal val LightSurface = Color(0xFFF7F7F9)
internal val LightKey = Color(0xFFEFEFF3)
internal val LightKeyPressed = Color(0xFFDCDCE4)
internal val LightOperatorKey = Color(0xFFDFDFE6)
internal val LightOperatorKeyPressed = Color(0xFFCACAD4)
internal val LightOnKey = Color(0xFF1A1A1C)
internal val LightTextPrimary = Color(0xFF000000)
internal val LightTextSecondary = Color(0xFF6C6C70)
internal val LightDestructive = Color(0xFFB3392A)
internal val LightAccent = Color(0xFF0F6E56)
internal val LightAccentPressed = Color(0xFF0B5643)
internal val LightOnAccent = Color(0xFFFFFFFF)
internal val LightOutline = Color(0xFFE2E2E7)
