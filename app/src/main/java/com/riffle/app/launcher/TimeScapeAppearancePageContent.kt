@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_BLUR_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_CONTENT_PADDING_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_CONTRAST_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_CURVE_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_FOCUSED_GAP_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_FOCUSED_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_HIGHLIGHT_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_HORIZONTAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_OUTLINE_WIDTH_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_OVERLAP_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_PARALLAX_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_ROTATION_DEGREES
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_ROTATION_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_SATURATION_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_SETTLE_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_SHADOW_ELEVATION_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_SPRING_BOUNCINESS_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_TEXTURE_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_TEXT_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_TRAVEL_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_VERTICAL_SPACING_DP
import com.riffle.core.domain.launcher.settings.MAX_TIMESCAPE_VISIBLE_DEPTH
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_BLUR_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_CONTENT_PADDING_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_CONTRAST_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_CURVE_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_FOCUSED_GAP_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_FOCUSED_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_HIGHLIGHT_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_HORIZONTAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_OUTLINE_WIDTH_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_OVERLAP_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_PARALLAX_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_ROTATION_DEGREES
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_ROTATION_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_SATURATION_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_SETTLE_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_SHADOW_ELEVATION_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_SPRING_BOUNCINESS_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_TEXTURE_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_TEXT_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_TRAVEL_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_VERTICAL_SPACING_DP
import com.riffle.core.domain.launcher.settings.MIN_TIMESCAPE_VISIBLE_DEPTH
import com.riffle.core.domain.launcher.settings.TimeScapeAccentSource
import com.riffle.core.domain.launcher.settings.TimeScapeAppearancePreset
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeBackgroundSource
import com.riffle.core.domain.launcher.settings.TimeScapeContentDensity
import com.riffle.core.domain.launcher.settings.TimeScapeEasing
import com.riffle.core.domain.launcher.settings.TimeScapeFanDirection
import com.riffle.core.domain.launcher.settings.TimeScapeHapticStrength

@Composable
internal fun TimeScapeAppearancePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val appearance = state.settings.cards.timeScapeAppearance
    var resetConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val update: ((TimeScapeAppearanceSettings) -> TimeScapeAppearanceSettings) -> Unit = { transform ->
        onAction(LauncherShellAction.UpdateTimeScapeAppearance(transform(appearance).coerce()))
    }

    SettingsSection(title = "Preview") {
        TimeScapeAppearancePreview(
            appearance = appearance,
            modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 460.dp),
        )
        timeScapeFallbackMessage(appearance)?.let { message ->
            SettingsListRow(title = "Effective fallback", subtitle = message)
        }
    }
    SettingsSection(title = "Preset and reset") {
        TimeScapeEnumChoices(
            title = "Appearance preset",
            values = TimeScapeAppearancePreset.entries,
            selected = appearance.preset,
            label = TimeScapeAppearancePreset::label,
            onSelected = { preset -> update { it.applyPreset(preset) } },
        )
        SettingsClickableRow(
            title = "Reset TimeScape appearance",
            subtitle = "Restore the Modern TimeScape profile",
            onClick = { resetConfirmationVisible = true },
            trailingContent = { SettingsButtonText(text = "Reset") },
        )
    }
    SettingsSection(title = "Card geometry") {
        TimeScapeSlider(
            "Card aspect ratio",
            appearance.geometry.cardAspectRatioPercent,
            MIN_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT..MAX_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(cardAspectRatioPercent = value))
            }
        }
        TimeScapeSlider(
            "Focused card scale",
            appearance.geometry.focusedScalePercent,
            MIN_TIMESCAPE_FOCUSED_SCALE_PERCENT..MAX_TIMESCAPE_FOCUSED_SCALE_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(focusedScalePercent = value))
            }
        }
        TimeScapeSlider(
            "Focused card gap",
            appearance.geometry.focusedGapDp,
            MIN_TIMESCAPE_FOCUSED_GAP_DP..MAX_TIMESCAPE_FOCUSED_GAP_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(focusedGapDp = value))
            }
        }
        TimeScapeSlider(
            "Corner radius",
            appearance.geometry.cornerRadiusDp,
            MIN_TIMESCAPE_CORNER_RADIUS_DP..MAX_TIMESCAPE_CORNER_RADIUS_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(cornerRadiusDp = value))
            }
        }
        TimeScapeSlider(
            "Content padding",
            appearance.geometry.contentPaddingDp,
            MIN_TIMESCAPE_CONTENT_PADDING_DP..MAX_TIMESCAPE_CONTENT_PADDING_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(contentPaddingDp = value))
            }
        }
        SettingsSwitchRow("Clip card content", "Keep content inside the card shape", appearance.geometry.clipContent, { value ->
            update {
                it.copy(geometry = it.geometry.copy(clipContent = value))
            }
        })
    }
    SettingsSection(title = "Stack and spline") {
        TimeScapeSlider(
            "Visible card depth",
            appearance.geometry.visibleDepth,
            MIN_TIMESCAPE_VISIBLE_DEPTH..MAX_TIMESCAPE_VISIBLE_DEPTH,
            "cards",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(visibleDepth = value))
            }
        }
        TimeScapeSlider(
            "Card overlap",
            appearance.geometry.overlapPercent,
            MIN_TIMESCAPE_OVERLAP_PERCENT..MAX_TIMESCAPE_OVERLAP_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(overlapPercent = value))
            }
        }
        TimeScapeSlider(
            "Vertical spacing",
            appearance.geometry.verticalSpacingDp,
            MIN_TIMESCAPE_VERTICAL_SPACING_DP..MAX_TIMESCAPE_VERTICAL_SPACING_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(verticalSpacingDp = value))
            }
        }
        TimeScapeSlider(
            "Horizontal offset",
            appearance.geometry.horizontalOffsetDp,
            MIN_TIMESCAPE_HORIZONTAL_OFFSET_DP..MAX_TIMESCAPE_HORIZONTAL_OFFSET_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(horizontalOffsetDp = value))
            }
        }
        TimeScapeSlider(
            "Spline curve",
            appearance.geometry.curveDp,
            MIN_TIMESCAPE_CURVE_DP..MAX_TIMESCAPE_CURVE_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(curveDp = value))
            }
        }
        TimeScapeSlider(
            "Card rotation",
            appearance.geometry.rotationDegrees,
            MIN_TIMESCAPE_ROTATION_DEGREES..MAX_TIMESCAPE_ROTATION_DEGREES,
            "°",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(rotationDegrees = value))
            }
        }
        TimeScapeEnumChoices(
            "Fan direction",
            TimeScapeFanDirection.entries,
            appearance.geometry.fanDirection,
            TimeScapeFanDirection::label,
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(fanDirection = value))
            }
        }
    }
    SettingsSection(title = "Surface and glass") {
        TimeScapeEnumChoices(
            "Background",
            TimeScapeBackgroundSource.entries,
            appearance.surface.backgroundSource,
            TimeScapeBackgroundSource::label,
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(backgroundSource = value))
            }
        }
        TimeScapeColorChoices("Custom background", appearance.surface.customBackgroundArgb) { value ->
            update {
                it.copy(surface = it.surface.copy(customBackgroundArgb = value))
            }
        }
        TimeScapeSlider(
            "Glass transparency",
            appearance.surface.glassTransparencyPercent,
            MIN_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT..MAX_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(glassTransparencyPercent = value))
            }
        }
        TimeScapeColorChoices("Glass tint", appearance.surface.glassTintArgb) { value ->
            update {
                it.copy(surface = it.surface.copy(glassTintArgb = value))
            }
        }
        TimeScapeSlider(
            "Blur strength",
            appearance.surface.blurStrengthPercent,
            MIN_TIMESCAPE_BLUR_STRENGTH_PERCENT..MAX_TIMESCAPE_BLUR_STRENGTH_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(blurStrengthPercent = value))
            }
        }
        TimeScapeSlider(
            "Saturation",
            appearance.surface.saturationPercent,
            MIN_TIMESCAPE_SATURATION_PERCENT..MAX_TIMESCAPE_SATURATION_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(saturationPercent = value))
            }
        }
        TimeScapeSlider(
            "Contrast",
            appearance.surface.contrastPercent,
            MIN_TIMESCAPE_CONTRAST_PERCENT..MAX_TIMESCAPE_CONTRAST_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(contrastPercent = value))
            }
        }
        TimeScapeSlider(
            "Outline width",
            appearance.surface.outlineWidthDp,
            MIN_TIMESCAPE_OUTLINE_WIDTH_DP..MAX_TIMESCAPE_OUTLINE_WIDTH_DP,
            "dp",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(outlineWidthDp = value))
            }
        }
        TimeScapeSlider(
            "Highlight",
            appearance.surface.highlightPercent,
            MIN_TIMESCAPE_HIGHLIGHT_PERCENT..MAX_TIMESCAPE_HIGHLIGHT_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(highlightPercent = value))
            }
        }
        TimeScapeSlider(
            "Shadow elevation",
            appearance.surface.shadowElevationDp,
            MIN_TIMESCAPE_SHADOW_ELEVATION_DP..MAX_TIMESCAPE_SHADOW_ELEVATION_DP,
            "dp",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(shadowElevationDp = value))
            }
        }
        TimeScapeSlider(
            "Texture intensity",
            appearance.surface.textureIntensityPercent,
            MIN_TIMESCAPE_TEXTURE_INTENSITY_PERCENT..MAX_TIMESCAPE_TEXTURE_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(textureIntensityPercent = value))
            }
        }
    }
    SettingsSection(title = "Colour and content") {
        TimeScapeEnumChoices(
            "Accent",
            TimeScapeAccentSource.entries,
            appearance.typography.accentSource,
            TimeScapeAccentSource::label,
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(accentSource = value))
            }
        }
        TimeScapeColorChoices("Custom accent", appearance.typography.customAccentArgb) { value ->
            update {
                it.copy(typography = it.typography.copy(customAccentArgb = value))
            }
        }
        SettingsSwitchRow(
            title = "Automatic text contrast",
            subtitle = "Choose readable foreground text automatically",
            checked = appearance.typography.automaticForegroundContrast,
            onCheckedChange = { value ->
                update {
                    it.copy(typography = it.typography.copy(automaticForegroundContrast = value))
                }
            },
        )
        TimeScapeEnumChoices(
            "Content density",
            TimeScapeContentDensity.entries,
            appearance.typography.contentDensity,
            TimeScapeContentDensity::label,
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(contentDensity = value))
            }
        }
        TimeScapeSlider(
            "Text scale",
            appearance.typography.textScalePercent,
            MIN_TIMESCAPE_TEXT_SCALE_PERCENT..MAX_TIMESCAPE_TEXT_SCALE_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(textScalePercent = value))
            }
        }
    }
    SettingsSection(title = "Motion and haptics") {
        TimeScapeSlider(
            "Settle duration",
            appearance.motion.settleDurationMillis,
            MIN_TIMESCAPE_SETTLE_DURATION_MILLIS..MAX_TIMESCAPE_SETTLE_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(settleDurationMillis = value))
            }
        }
        TimeScapeSlider(
            "Reflow duration",
            appearance.motion.reflowDurationMillis,
            MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS..MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(reflowDurationMillis = value))
            }
        }
        TimeScapeSlider(
            "Enter duration",
            appearance.motion.enterDurationMillis,
            MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS..MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(enterDurationMillis = value))
            }
        }
        TimeScapeSlider(
            "Exit duration",
            appearance.motion.exitDurationMillis,
            MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS..MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(exitDurationMillis = value))
            }
        }
        TimeScapeSlider(
            "Expand duration",
            appearance.motion.expandDurationMillis,
            MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS..MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(expandDurationMillis = value))
            }
        }
        TimeScapeEnumChoices("Easing", TimeScapeEasing.entries, appearance.motion.easing, TimeScapeEasing::label) { value ->
            update {
                it.copy(motion = it.motion.copy(easing = value))
            }
        }
        TimeScapeSlider(
            "Spring bounciness",
            appearance.motion.springBouncinessPercent,
            MIN_TIMESCAPE_SPRING_BOUNCINESS_PERCENT..MAX_TIMESCAPE_SPRING_BOUNCINESS_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(springBouncinessPercent = value))
            }
        }
        TimeScapeSlider(
            "Travel intensity",
            appearance.motion.travelIntensityPercent,
            MIN_TIMESCAPE_TRAVEL_INTENSITY_PERCENT..MAX_TIMESCAPE_TRAVEL_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(travelIntensityPercent = value))
            }
        }
        TimeScapeSlider(
            "Parallax intensity",
            appearance.motion.parallaxIntensityPercent,
            MIN_TIMESCAPE_PARALLAX_INTENSITY_PERCENT..MAX_TIMESCAPE_PARALLAX_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(parallaxIntensityPercent = value))
            }
        }
        TimeScapeSlider(
            "Rotation intensity",
            appearance.motion.rotationIntensityPercent,
            MIN_TIMESCAPE_ROTATION_INTENSITY_PERCENT..MAX_TIMESCAPE_ROTATION_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(rotationIntensityPercent = value))
            }
        }
        TimeScapeEnumChoices(
            "Haptic strength",
            TimeScapeHapticStrength.entries,
            appearance.motion.hapticStrength,
            TimeScapeHapticStrength::label,
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(hapticStrength = value))
            }
        }
    }
    SettingsSection(title = "Accessibility fallbacks") {
        SettingsSwitchRow("Reduced motion", "Use static, reachable card positions", appearance.motion.reducedMotion, { value ->
            update {
                it.copy(motion = it.motion.copy(reducedMotion = value))
            }
        })
        SettingsSwitchRow("Reduced transparency", "Remove translucent glass and blur", appearance.motion.reducedTransparency, { value ->
            update {
                it.copy(motion = it.motion.copy(reducedTransparency = value))
            }
        })
    }
    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text("Reset TimeScape appearance?") },
            text = { Text("This replaces all TimeScape appearance, geometry, and motion choices with the Modern preset.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        update { it.reset() }
                        resetConfirmationVisible = false
                    },
                    modifier = Modifier.semantics { contentDescription = "Confirm TimeScape reset" },
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetConfirmationVisible = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TimeScapeSlider(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    onValueChange: (Int) -> Unit,
) {
    DiscreteSettingSlider(title, value, range, { "$it $unit" }, onValueChange)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> TimeScapeEnumChoices(
    title: String,
    values: Iterable<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(title = title, subtitle = label(selected))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
private fun TimeScapeColorChoices(
    title: String,
    selected: Long,
    onSelected: (Long) -> Unit,
) {
    val colors = listOf(0xFF1B1B1FL, 0xFF355C7DL, 0xFF6C5B7BL, 0xFFC06C84L, 0xFFFFFFFFL)
    TimeScapeEnumChoices(title, colors, selected, { color -> "#${color.toString(16).takeLast(6).uppercase()}" }, onSelected)
}

private fun timeScapeFallbackMessage(appearance: TimeScapeAppearanceSettings): String? {
    val effective = appearance.effectiveFor(timeScapeRendererCapabilities())
    return when {
        appearance.motion.reducedTransparency -> "Reduced transparency is on: glass and blur are disabled."
        appearance.surface.blurStrengthPercent != effective.surface.blurStrengthPercent ->
            "Blur is unavailable on this device; the preview shows the opaque fallback."
        appearance.surface.textureIntensityPercent != effective.surface.textureIntensityPercent ->
            "Texture is unavailable on this device; the preview omits it."
        else -> null
    }
}

private fun TimeScapeAppearancePreset.label(): String =
    when (this) {
        TimeScapeAppearancePreset.MODERN_TIMESCAPE -> "Modern"
        TimeScapeAppearancePreset.FLAT_REDUCED_DEPTH -> "Flat"
    }

private fun TimeScapeBackgroundSource.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun TimeScapeAccentSource.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun TimeScapeContentDensity.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun TimeScapeEasing.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun TimeScapeFanDirection.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun TimeScapeHapticStrength.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)
