package io.github.angad7600123.cambio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.angad7600123.cambio.data.ThemeMode

/**
 * The semantic colour roles the calculator UI actually uses.
 *
 * Material 3's own scheme does not have a concept of "a calculator key" or "the
 * destructive glyph", so these roles are declared explicitly. Components reference
 * roles rather than raw colours, which is what keeps light, dark and dynamic
 * variants consistent.
 */
@Immutable
data class CambioColors(
    val canvas: Color,
    val surface: Color,
    val key: Color,
    val keyPressed: Color,
    val onKey: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val destructive: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val outline: Color,
)

internal val DarkCambioColors = CambioColors(
    canvas = DarkCanvas,
    surface = DarkSurface,
    key = DarkKey,
    keyPressed = DarkKeyPressed,
    onKey = DarkOnKey,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    destructive = DarkDestructive,
    accent = DarkAccent,
    accentPressed = DarkAccentPressed,
    onAccent = DarkOnAccent,
    outline = DarkOutline,
)

internal val LightCambioColors = CambioColors(
    canvas = LightCanvas,
    surface = LightSurface,
    key = LightKey,
    keyPressed = LightKeyPressed,
    onKey = LightOnKey,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    destructive = LightDestructive,
    accent = LightAccent,
    accentPressed = LightAccentPressed,
    onAccent = LightOnAccent,
    outline = LightOutline,
)

val LocalCambioColors = staticCompositionLocalOf { DarkCambioColors }

/**
 * Applies the Cambio theme.
 *
 * @param useSystemColors when true and the device supports it (Android 12+), the
 *   key and accent roles are re-derived from the wallpaper-based system palette, so
 *   the app adopts a Samsung One UI or Material You theme if one is applied. When
 *   false, the signature palette from the reference design is used.
 */
@Composable
fun CambioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useSystemColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val materialScheme = when {
        useSystemColors && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useSystemColors && supportsDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(
            primary = DarkAccent,
            onPrimary = DarkOnAccent,
            background = DarkCanvas,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            error = DarkDestructive,
        )
        else -> lightColorScheme(
            primary = LightAccent,
            onPrimary = LightOnAccent,
            background = LightCanvas,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            error = LightDestructive,
        )
    }

    val cambioColors = if (useSystemColors && supportsDynamic) {
        materialScheme.toCambioColors(darkTheme)
    } else if (darkTheme) {
        DarkCambioColors
    } else {
        LightCambioColors
    }

    CompositionLocalProvider(LocalCambioColors provides cambioColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = CambioTypography,
            content = content,
        )
    }
}

/**
 * Maps a dynamic Material scheme onto the calculator's roles.
 *
 * The mapping keeps the reference layout's structure — a recessive key surface, a
 * single filled accent for equals, an error colour for the destructive keys — while
 * letting the system supply the actual hues.
 */
private fun androidx.compose.material3.ColorScheme.toCambioColors(darkTheme: Boolean): CambioColors = CambioColors(
    canvas = if (darkTheme) Color.Black else background,
    surface = surfaceContainerLow,
    key = surfaceContainerHigh,
    keyPressed = surfaceContainerHighest,
    onKey = onSurface,
    textPrimary = onBackground,
    textSecondary = onSurfaceVariant,
    destructive = error,
    accent = primary,
    accentPressed = primaryContainer,
    onAccent = onPrimary,
    outline = outlineVariant,
)

/** Convenient access to the calculator colour roles from any composable. */
object CambioTheme {
    val colors: CambioColors
        @Composable @ReadOnlyComposable
        get() = LocalCambioColors.current
}
