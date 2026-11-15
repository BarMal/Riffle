package com.riffle.app.launcher.stream

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.riffle.core.domain.launcher.stream.StreamCardSizes

/**
 * The sizes the stacks below are drawn at ([StreamCardSizes]): the folded phone's unless the unfolded column says
 * otherwise ([ProvideStreamCardSizes]).
 */
internal val LocalStreamCardSizes = staticCompositionLocalOf { StreamCardSizes.FOLDED }

/**
 * Draws [content] at [sizes]: the stack reads them for its pitch, strips and card heights, and the cards' text is the
 * theme's typography scaled by [StreamCardSizes.textScale] (cards are measured in that typography too, so a taller
 * card is as tall as the larger text needs). The folded sizes change nothing.
 */
@Composable
internal fun ProvideStreamCardSizes(
    sizes: StreamCardSizes,
    content: @Composable () -> Unit,
) {
    if (sizes.textScale == 1f) {
        CompositionLocalProvider(LocalStreamCardSizes provides sizes, content = content)
    } else {
        val theme = MaterialTheme.typography
        val scaled = remember(theme, sizes.textScale) { theme.scaledBy(sizes.textScale) }
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = scaled,
        ) {
            CompositionLocalProvider(LocalStreamCardSizes provides sizes, content = content)
        }
    }
}

private fun Typography.scaledBy(factor: Float): Typography =
    Typography(
        displayLarge = displayLarge.scaledBy(factor),
        displayMedium = displayMedium.scaledBy(factor),
        displaySmall = displaySmall.scaledBy(factor),
        headlineLarge = headlineLarge.scaledBy(factor),
        headlineMedium = headlineMedium.scaledBy(factor),
        headlineSmall = headlineSmall.scaledBy(factor),
        titleLarge = titleLarge.scaledBy(factor),
        titleMedium = titleMedium.scaledBy(factor),
        titleSmall = titleSmall.scaledBy(factor),
        bodyLarge = bodyLarge.scaledBy(factor),
        bodyMedium = bodyMedium.scaledBy(factor),
        bodySmall = bodySmall.scaledBy(factor),
        labelLarge = labelLarge.scaledBy(factor),
        labelMedium = labelMedium.scaledBy(factor),
        labelSmall = labelSmall.scaledBy(factor),
    )

private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize.scaledBy(factor), lineHeight = lineHeight.scaledBy(factor))

private fun TextUnit.scaledBy(factor: Float): TextUnit =
    if (type == TextUnitType.Sp) TextUnit(value * factor, TextUnitType.Sp) else this
