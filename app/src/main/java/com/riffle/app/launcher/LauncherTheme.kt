package com.riffle.app.launcher

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.riffle.core.domain.launcher.settings.LauncherThemeMode

@Composable
fun RiffleLauncherTheme(
    themeMode: LauncherThemeMode = LauncherThemeMode.SYSTEM,
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
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            else -> fallbackScheme(darkTheme = darkTheme)
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

internal fun supportsDynamicMaterialColor(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

internal fun fallbackScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) darkScheme else lightScheme

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
