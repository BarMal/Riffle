@file:Suppress("CyclomaticComplexMethod", "TooManyFunctions")

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LauncherThemeAccent
import com.riffle.core.domain.launcher.settings.LauncherThemeColors
import com.riffle.core.domain.launcher.settings.LauncherThemeCornerStyle
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.LauncherThemePreset
import com.riffle.core.domain.launcher.settings.LauncherThemeTypography

@Composable
fun RiffleLauncherTheme(
    themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
    themePreset: LauncherThemePreset = LauncherThemePreset.MATERIAL,
    themeAccent: LauncherThemeAccent = LauncherThemeAccent.DEFAULT,
    themeColors: LauncherThemeColors = LauncherThemeColors(),
    themeCornerStyle: LauncherThemeCornerStyle = LauncherThemeCornerStyle.PRESET,
    themeTypography: LauncherThemeTypography = LauncherThemeTypography.PRESET,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            LauncherThemeMode.SYSTEM -> isSystemInDarkTheme()
            LauncherThemeMode.LIGHT -> false
            LauncherThemeMode.DARK -> true
        }
    val context = LocalContext.current
    val baseColorScheme =
        when {
            themePreset == LauncherThemePreset.MATERIAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                darkTheme -> dynamicDarkColorScheme(context)

            themePreset == LauncherThemePreset.MATERIAL &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            else -> fallbackScheme(darkTheme = darkTheme, themePreset = themePreset)
        }
    val colorScheme = baseColorScheme.withThemeAccent(themeAccent, darkTheme).withThemeColors(themeColors)

    CompositionLocalProvider(
        LocalLauncherCardShape provides launcherCardShape(themePreset, themeCornerStyle),
        LocalLauncherPanelShape provides launcherPanelShape(themePreset, themeCornerStyle),
        LocalLauncherThemeColorOverrides provides themeColors.toColorOverrides(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = launcherTypography(themePreset, themeTypography),
            content = content,
        )
    }
}

internal val LocalLauncherCardShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(24.dp) }
internal val LocalLauncherPanelShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(32.dp) }
internal val LocalLauncherThemeColorOverrides = staticCompositionLocalOf { LauncherThemeColorOverrides() }

internal data class LauncherThemeColorOverrides(
    val dock: Color? = null,
    val label: Color? = null,
    val labelBackground: Color? = null,
)

internal fun launcherCardShape(
    themePreset: LauncherThemePreset,
    cornerStyle: LauncherThemeCornerStyle = LauncherThemeCornerStyle.PRESET,
): Shape =
    RoundedCornerShape(
        when (cornerStyle) {
            LauncherThemeCornerStyle.COMPACT -> 8.dp
            LauncherThemeCornerStyle.ROUNDED -> 28.dp
            LauncherThemeCornerStyle.PRESET ->
                when (themePreset) {
                    LauncherThemePreset.MINIMAL -> 8.dp
                    LauncherThemePreset.RETRO -> 12.dp
                    LauncherThemePreset.GLASS -> 28.dp
                    LauncherThemePreset.TERMINAL -> 0.dp
                    LauncherThemePreset.MATERIAL,
                    LauncherThemePreset.CUSTOM,
                    -> 24.dp
                }
        },
    )

internal fun launcherPanelShape(
    themePreset: LauncherThemePreset,
    cornerStyle: LauncherThemeCornerStyle = LauncherThemeCornerStyle.PRESET,
): Shape =
    RoundedCornerShape(
        when (cornerStyle) {
            LauncherThemeCornerStyle.COMPACT -> 12.dp
            LauncherThemeCornerStyle.ROUNDED -> 36.dp
            LauncherThemeCornerStyle.PRESET ->
                when (themePreset) {
                    LauncherThemePreset.MINIMAL -> 12.dp
                    LauncherThemePreset.RETRO -> 20.dp
                    LauncherThemePreset.GLASS -> 36.dp
                    LauncherThemePreset.TERMINAL -> 0.dp
                    LauncherThemePreset.MATERIAL,
                    LauncherThemePreset.CUSTOM,
                    -> 32.dp
                }
        },
    )

internal fun launcherTypography(
    themePreset: LauncherThemePreset,
    typography: LauncherThemeTypography = LauncherThemeTypography.PRESET,
): Typography =
    when (typography) {
        LauncherThemeTypography.SYSTEM -> defaultLauncherTypography
        LauncherThemeTypography.MONOSPACE -> defaultLauncherTypography.withFontFamily(FontFamily.Monospace)
        LauncherThemeTypography.PRESET ->
            when (themePreset) {
                LauncherThemePreset.TERMINAL -> defaultLauncherTypography.withFontFamily(FontFamily.Monospace)
                else -> defaultLauncherTypography
            }
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

internal fun ColorScheme.withThemeAccent(
    accent: LauncherThemeAccent,
    darkTheme: Boolean,
): ColorScheme =
    accent.colorRoles(darkTheme)?.let { roles ->
        copy(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = roles.secondary,
            onSecondary = roles.onSecondary,
            secondaryContainer = roles.secondaryContainer,
            onSecondaryContainer = roles.onSecondaryContainer,
            tertiary = roles.tertiary,
            onTertiary = roles.onTertiary,
            tertiaryContainer = roles.tertiaryContainer,
            onTertiaryContainer = roles.onTertiaryContainer,
            surfaceVariant = roles.secondaryContainer,
            onSurfaceVariant = roles.onSecondaryContainer,
            surfaceContainerHigh = roles.secondaryContainer,
            surfaceContainerHighest = roles.secondaryContainer,
            outline = roles.secondary,
            outlineVariant = roles.secondary,
            surfaceTint = roles.primary,
            inversePrimary = roles.inversePrimary,
        )
    } ?: this

internal fun ColorScheme.withThemeColors(colors: LauncherThemeColors): ColorScheme {
    val background = colors.backgroundArgb?.let(::Color)
    val accent = colors.accentArgb?.let(::Color)
    return copy(
        background = background ?: this.background,
        onBackground = background?.contentColor(onBackground) ?: onBackground,
        surface = background ?: surface,
        onSurface = background?.contentColor(onSurface) ?: onSurface,
        surfaceDim = background ?: surfaceDim,
        surfaceBright = background ?: surfaceBright,
        surfaceContainerLowest = background ?: surfaceContainerLowest,
        surfaceContainerLow = background ?: surfaceContainerLow,
        surfaceContainer = background ?: surfaceContainer,
        surfaceContainerHigh = background ?: surfaceContainerHigh,
        surfaceContainerHighest = background ?: surfaceContainerHighest,
        surfaceVariant = background ?: surfaceVariant,
        onSurfaceVariant = background?.contentColor(onSurfaceVariant) ?: onSurfaceVariant,
        primary = accent ?: primary,
        onPrimary = accent?.contentColor(onPrimary) ?: onPrimary,
        primaryContainer = accent ?: primaryContainer,
        onPrimaryContainer = accent?.contentColor(onPrimaryContainer) ?: onPrimaryContainer,
        secondary = accent ?: secondary,
        onSecondary = accent?.contentColor(onSecondary) ?: onSecondary,
        secondaryContainer = accent ?: secondaryContainer,
        onSecondaryContainer = accent?.contentColor(onSecondaryContainer) ?: onSecondaryContainer,
        tertiary = accent ?: tertiary,
        onTertiary = accent?.contentColor(onTertiary) ?: onTertiary,
        tertiaryContainer = accent ?: tertiaryContainer,
        onTertiaryContainer = accent?.contentColor(onTertiaryContainer) ?: onTertiaryContainer,
        surfaceTint = accent ?: surfaceTint,
        inversePrimary = accent ?: inversePrimary,
    )
}

private fun LauncherThemeColors.toColorOverrides(): LauncherThemeColorOverrides =
    LauncherThemeColorOverrides(
        dock = dockArgb?.let(::Color),
        label = labelArgb?.let(::Color),
        labelBackground = labelBackgroundArgb?.let(::Color),
    )

private fun Color.contentColor(fallback: Color): Color =
    if (alpha < 0.5f) {
        fallback
    } else {
        listOf(Color.Black, Color.White).maxBy { candidate -> candidate.contrastRatioAgainst(this) }
    }

private fun Color.contrastRatioAgainst(background: Color): Float {
    val lighter = maxOf(luminance(), background.luminance())
    val darker = minOf(luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private data class LauncherAccentColorRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inversePrimary: Color,
)

private fun LauncherThemeAccent.colorRoles(darkTheme: Boolean): LauncherAccentColorRoles? =
    when (this) {
        LauncherThemeAccent.DEFAULT -> null
        LauncherThemeAccent.BLUE -> if (darkTheme) blueDarkAccent else blueLightAccent
        LauncherThemeAccent.TEAL -> if (darkTheme) tealDarkAccent else tealLightAccent
        LauncherThemeAccent.ROSE -> if (darkTheme) roseDarkAccent else roseLightAccent
        LauncherThemeAccent.AMBER -> if (darkTheme) amberDarkAccent else amberLightAccent
    }

private val blueLightAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF2C6094), onPrimary = Color.White,
        primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D35),
        secondary = Color(0xFF4E6078), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD7E3F7), onSecondaryContainer = Color(0xFF0A1D31),
        tertiary = Color(0xFF66587A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFECDDFF), onTertiaryContainer = Color(0xFF201634),
        inversePrimary = Color(0xFF9DCAFF),
    )

private val blueDarkAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF9DCAFF), onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF124875), onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFB6C8DF), onSecondary = Color(0xFF203246),
        secondaryContainer = Color(0xFF36485E), onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = Color(0xFFD0BEE8), onTertiary = Color(0xFF372B4A),
        tertiaryContainer = Color(0xFF4E4062), onTertiaryContainer = Color(0xFFECDDFF),
        inversePrimary = Color(0xFF2C6094),
    )

private val tealLightAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF006B61), onPrimary = Color.White,
        primaryContainer = Color(0xFF72F8E6), onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF4A635F), onSecondary = Color.White,
        secondaryContainer = Color(0xFFCCE8E2), onSecondaryContainer = Color(0xFF05201C),
        tertiary = Color(0xFF456179), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCBE6FF), onTertiaryContainer = Color(0xFF001E2F),
        inversePrimary = Color(0xFF55DBC8),
    )

private val tealDarkAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF55DBC8), onPrimary = Color(0xFF003731),
        primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFF72F8E6),
        secondary = Color(0xFFB1CCC6), onSecondary = Color(0xFF1D3531),
        secondaryContainer = Color(0xFF334B46), onSecondaryContainer = Color(0xFFCCE8E2),
        tertiary = Color(0xFFADCAE5), onTertiary = Color(0xFF153349),
        tertiaryContainer = Color(0xFF2D4961), onTertiaryContainer = Color(0xFFCBE6FF),
        inversePrimary = Color(0xFF006B61),
    )

private val roseLightAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF904B72), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD8E8), onPrimaryContainer = Color(0xFF3A0027),
        secondary = Color(0xFF735761), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E3), onSecondaryContainer = Color(0xFF2A151E),
        tertiary = Color(0xFF7C5633), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCC1), onTertiaryContainer = Color(0xFF2E1500),
        inversePrimary = Color(0xFFFFB0D0),
    )

private val roseDarkAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFFFFB0D0), onPrimary = Color(0xFF58113F),
        primaryContainer = Color(0xFF723157), onPrimaryContainer = Color(0xFFFFD8E8),
        secondary = Color(0xFFE2BDC8), onSecondary = Color(0xFF422A33),
        secondaryContainer = Color(0xFF5A3F49), onSecondaryContainer = Color(0xFFFFD9E3),
        tertiary = Color(0xFFEFBF95), onTertiary = Color(0xFF472A0B),
        tertiaryContainer = Color(0xFF60401F), onTertiaryContainer = Color(0xFFFFDCC1),
        inversePrimary = Color(0xFF904B72),
    )

private val amberLightAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFF825500), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDDB7), onPrimaryContainer = Color(0xFF291800),
        secondary = Color(0xFF705B40), onSecondary = Color.White,
        secondaryContainer = Color(0xFFFBDDBA), onSecondaryContainer = Color(0xFF281805),
        tertiary = Color(0xFF53643D), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD7EABA), onTertiaryContainer = Color(0xFF112000),
        inversePrimary = Color(0xFFFFB95F),
    )

private val amberDarkAccent =
    LauncherAccentColorRoles(
        primary = Color(0xFFFFB95F), onPrimary = Color(0xFF452B00),
        primaryContainer = Color(0xFF623F00), onPrimaryContainer = Color(0xFFFFDDB7),
        secondary = Color(0xFFDEC3A0), onSecondary = Color(0xFF3E2D16),
        secondaryContainer = Color(0xFF57432A), onSecondaryContainer = Color(0xFFFBDDBA),
        tertiary = Color(0xFFBBCEA0), onTertiary = Color(0xFF263515),
        tertiaryContainer = Color(0xFF3C4C28), onTertiaryContainer = Color(0xFFD7EABA),
        inversePrimary = Color(0xFF825500),
    )

private fun ColorScheme.withThemePreset(preset: LauncherThemePreset): ColorScheme =
    when (preset) {
        LauncherThemePreset.MATERIAL,
        LauncherThemePreset.CUSTOM,
        -> this

        LauncherThemePreset.MINIMAL -> copy(primary = Color(0xFF5B5F66), secondary = Color(0xFF5B5F66))
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
