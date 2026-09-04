package io.github.angad7600123.cambio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Cambio palette.
 *
 * The dark scheme is the app's signature look, taken from the reference design: a
 * true-black canvas, uniform graphite keys, white glyphs, a coral accent reserved
 * for the two destructive keys (clear and backspace), and a single filled jade key
 * for equals. Hierarchy comes from glyph colour and one filled key, not from
 * colouring whole rows.
 *
 * The light scheme mirrors those roles rather than simply inverting them, so the
 * same meanings survive: destructive is still coral, equals is still the one
 * filled key.
 *
 * Every foreground/background pair below was checked against WCAG 2.1 AA. The
 * light coral is deliberately darker than the dark-theme coral because it has to
 * carry 4.5:1 against a near-white key.
 */

// Dark scheme (default look).
internal val DarkCanvas = Color(0xFF000000)
internal val DarkSurface = Color(0xFF121214)
internal val DarkKey = Color(0xFF2C2C2E)
internal val DarkKeyPressed = Color(0xFF3D3D40)
internal val DarkOnKey = Color(0xFFFFFFFF)
internal val DarkTextPrimary = Color(0xFFFFFFFF)
internal val DarkTextSecondary = Color(0xFF8E8E93)
internal val DarkDestructive = Color(0xFFF0776B)
internal val DarkAccent = Color(0xFF17876B)
internal val DarkAccentPressed = Color(0xFF1FA383)
internal val DarkOnAccent = Color(0xFFFFFFFF)
internal val DarkOutline = Color(0xFF2A2A2C)

/** A lighter jade for accent *text* on the true-black canvas, where the filled
 * accent would not carry enough contrast as a glyph colour. */
internal val DarkAccentText = Color(0xFF35C79E)

// Light scheme.
internal val LightCanvas = Color(0xFFFFFFFF)
internal val LightSurface = Color(0xFFF7F7F9)
internal val LightKey = Color(0xFFEAEAEF)
internal val LightKeyPressed = Color(0xFFD6D6DE)
internal val LightOnKey = Color(0xFF1A1A1C)
internal val LightTextPrimary = Color(0xFF000000)
internal val LightTextSecondary = Color(0xFF6C6C70)
internal val LightDestructive = Color(0xFFB3392A)
internal val LightAccent = Color(0xFF0F6E56)
internal val LightAccentPressed = Color(0xFF0B5643)
internal val LightOnAccent = Color(0xFFFFFFFF)
internal val LightOutline = Color(0xFFE2E2E7)
