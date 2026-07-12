package com.riffle.app.launcher

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset

@Composable
fun RiffleLauncherTheme(
    themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
    themePreset: LauncherThemePreset = LauncherThemePreset.MATERIAL,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            LauncherThemeMode.SYSTEM -> isSystemInDarkTheme()
            LauncherThemeMode.LIGHT -> false
            LauncherThemeMode.DARK -> true
        }
    val context = LocalContext.current
    val colorScheme =
        when {
            themePreset == LauncherThemePreset.MATERIAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                darkTheme -> dynamicDarkColorScheme(context)

            themePreset == LauncherThemePreset.MATERIAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            else -> fallbackScheme(darkTheme = darkTheme, themePreset = themePreset)
        }

    CompositionLocalProvider(
        LocalLauncherCardShape provides launcherCardShape(themePreset),
        LocalLauncherPanelShape provides launcherPanelShape(themePreset),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = launcherTypography(themePreset),
            content = content,
        )
    }
}

internal val LocalLauncherCardShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(24.dp) }
internal val LocalLauncherPanelShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(32.dp) }

internal fun launcherCardShape(themePreset: LauncherThemePreset): Shape =
    RoundedCornerShape(
        when (themePreset) {
            LauncherThemePreset.MINIMAL -> 8.dp
            LauncherThemePreset.VICTORIAN -> 20.dp
            LauncherThemePreset.RETRO -> 12.dp
            LauncherThemePreset.GLASS -> 28.dp
            LauncherThemePreset.TERMINAL -> 0.dp
            LauncherThemePreset.MATERIAL,
            LauncherThemePreset.CUSTOM,
            -> 24.dp
        },
    )

internal fun launcherPanelShape(themePreset: LauncherThemePreset): Shape =
    RoundedCornerShape(
        when (themePreset) {
            LauncherThemePreset.MINIMAL -> 12.dp
            LauncherThemePreset.VICTORIAN -> 28.dp
            LauncherThemePreset.RETRO -> 20.dp
            LauncherThemePreset.GLASS -> 36.dp
            LauncherThemePreset.TERMINAL -> 0.dp
            LauncherThemePreset.MATERIAL,
            LauncherThemePreset.CUSTOM,
            -> 32.dp
        },
    )

internal fun launcherTypography(themePreset: LauncherThemePreset): Typography =
    when (themePreset) {
        LauncherThemePreset.VICTORIAN -> defaultLauncherTypography.withFontFamily(FontFamily.Serif)

        LauncherThemePreset.TERMINAL -> defaultLauncherTypography.withFontFamily(FontFamily.Monospace)

        else -> defaultLauncherTypography
    }

private val defaultLauncherTypography = Typography()

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography =
    copy(
        displayLarge = displayLarge.copy(fontFamily = fontFamily),
        displayMedium = displayMedium.copy(fontFamily = fontFamily),
        displaySmall = displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = titleLarge.copy(fontFamily = fontFamily),
        titleMedium = titleMedium.copy(fontFamily = fontFamily),
        titleSmall = titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = bodySmall.copy(fontFamily = fontFamily),
        labelLarge = labelLarge.copy(fontFamily = fontFamily),
        labelMedium = labelMedium.copy(fontFamily = fontFamily),
        labelSmall = labelSmall.copy(fontFamily = fontFamily),
    )

internal fun supportsDynamicMaterialColor(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

internal fun fallbackScheme(
    darkTheme: Boolean,
    themePreset: LauncherThemePreset = LauncherThemePreset.MATERIAL,
): ColorScheme =
    (if (darkTheme) darkScheme else lightScheme)
        .withThemePreset(themePreset)

private fun ColorScheme.withThemePreset(preset: LauncherThemePreset): ColorScheme =
    when (preset) {
        LauncherThemePreset.MATERIAL,
        LauncherThemePreset.CUSTOM,
        -> this

        LauncherThemePreset.MINIMAL -> copy(primary = Color(0xFF5B5F66), secondary = Color(0xFF5B5F66))
        LauncherThemePreset.VICTORIAN -> copy(primary = Color(0xFF76546F), secondary = Color(0xFF4D5C92))
        LauncherThemePreset.RETRO -> copy(primary = Color(0xFF875A00), secondary = Color(0xFF006B5F))
        LauncherThemePreset.GLASS -> copy(primary = Color(0xFF356F8A), secondary = Color(0xFF4D5C92))
        LauncherThemePreset.TERMINAL -> copy(primary = Color(0xFF1D7A45), secondary = Color(0xFF1D7A45))
    }

internal val lightScheme =
    lightColorScheme(
        primary = Color(0xFF4D5C92),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDCE1FF),
        onPrimaryContainer = Color(0xFF07164B),
        secondary = Color(0xFF006B5F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF76F8E3),
        onSecondaryContainer = Color(0xFF00201C),
        tertiary = Color(0xFF76546F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD7F3),
        onTertiaryContainer = Color(0xFF2D122A),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1B1B20),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1B1B20),
        surfaceVariant = Color(0xFFE2E1EC),
        onSurfaceVariant = Color(0xFF45464F),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

internal val darkScheme =
    darkColorScheme(
        primary = Color(0xFFB7C4FF),
        onPrimary = Color(0xFF1E2E61),
        primaryContainer = Color(0xFF354479),
        onPrimaryContainer = Color(0xFFDCE1FF),
        secondary = Color(0xFF55DBC7),
        onSecondary = Color(0xFF003731),
        secondaryContainer = Color(0xFF005047),
        onSecondaryContainer = Color(0xFF76F8E3),
        tertiary = Color(0xFFE5BADB),
        onTertiary = Color(0xFF442740),
        tertiaryContainer = Color(0xFF5C3D57),
        onTertiaryContainer = Color(0xFFFFD7F3),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C5D0),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )
