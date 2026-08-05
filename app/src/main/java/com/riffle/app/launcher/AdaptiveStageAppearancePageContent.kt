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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_CONTENT_PADDING_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_CONTRAST_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_CURVE_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_FOCUSED_GAP_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_OVERLAP_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_ROTATION_DEGREES
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_SATURATION_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_VERTICAL_SPACING_DP
import com.riffle.core.domain.launcher.settings.MAX_ADAPTIVE_STAGE_VISIBLE_DEPTH
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_CONTENT_PADDING_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_CONTRAST_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_CURVE_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_FOCUSED_GAP_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_OVERLAP_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_ROTATION_DEGREES
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_SATURATION_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_VERTICAL_SPACING_DP
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_VISIBLE_DEPTH
import com.riffle.core.domain.launcher.settings.AdaptiveStageAccentSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearancePreset
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageBackgroundSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageContentDensity
import com.riffle.core.domain.launcher.settings.AdaptiveStageEasing
import com.riffle.core.domain.launcher.settings.AdaptiveStageFanDirection
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities

@Composable
internal fun AdaptiveStageAppearancePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
    rendererCapabilities: AdaptiveStageRendererCapabilities = adaptiveStageRendererCapabilities(),
) {
    val appearance = state.settings.cards.adaptiveStageAppearance
    var resetConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val update: ((AdaptiveStageAppearanceSettings) -> AdaptiveStageAppearanceSettings) -> Unit = { transform ->
        onAction(LauncherShellAction.UpdateAdaptiveStageAppearance(transform(appearance).coerce()))
    }

    SettingsSection(title = "Preview") {
        AdaptiveStageAppearancePreview(
            appearance = appearance,
            globalReducedMotion = state.settings.motion.reducedMotion,
            rendererCapabilities = rendererCapabilities,
            modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 460.dp),
        )
        adaptiveStageFallbackMessage(appearance, rendererCapabilities)?.let { message ->
            SettingsListRow(title = "Effective fallback", subtitle = message)
        }
    }
    SettingsSection(title = "Layout") {
        AdaptiveStageEnumChoices(
            title = "Rail side",
            values = AdaptiveStageRailSide.entries,
            selected = state.settings.cards.adaptiveStageRailSide ?: AdaptiveStageRailSide.LEADING,
            label = AdaptiveStageRailSide::label,
            testTag = { side -> "adaptive-stage-rail-side-${side.name}" },
            onSelected = { side ->
                onAction(LauncherShellAction.SelectAdaptiveStageRailSide(side))
            },
        )
        SettingsListRow(
            title = "About rail side",
            subtitle = "Which edge the stage rail docks to in unfolded and split-pane layouts",
        )
        AdaptiveStageEnumChoices(
            title = "Pane arrangement",
            values = AdaptiveStagePaneArrangement.entries,
            selected = state.settings.cards.adaptiveStagePaneArrangement,
            label = AdaptiveStagePaneArrangement::label,
            testTag = { arrangement -> "adaptive-stage-pane-arrangement-${arrangement.name}" },
            onSelected = { arrangement ->
                onAction(LauncherShellAction.SelectAdaptiveStagePaneArrangement(arrangement))
            },
        )
        SettingsListRow(
            title = "About Split",
            subtitle = "Split shows card details in a larger area above the stack",
        )
    }
    SettingsSection(title = "Preset and reset") {
        AdaptiveStageEnumChoices(
            title = "Appearance preset",
            values = AdaptiveStageAppearancePreset.entries,
            selected = appearance.preset,
            label = AdaptiveStageAppearancePreset::label,
            testTag = { preset -> "adaptive-stage-preset-${preset.name}" },
            onSelected = { preset ->
                onAction(adaptiveStageAppearancePresetAction(preset))
            },
        )
        SettingsClickableRow(
            title = "Reset Cards appearance",
            subtitle = "Restore the Modern Cards profile",
            onClick = { resetConfirmationVisible = true },
            trailingContent = { SettingsButtonText(text = "Reset") },
        )
    }
    SettingsSection(title = "Card geometry") {
        AdaptiveStageSlider(
            "Card aspect ratio",
            appearance.geometry.cardAspectRatioPercent,
            MIN_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT..MAX_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(cardAspectRatioPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Focused card scale",
            appearance.geometry.focusedScalePercent,
            MIN_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT..MAX_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(focusedScalePercent = value))
            }
        }
        AdaptiveStageSlider(
            "Focused card gap",
            appearance.geometry.focusedGapDp,
            MIN_ADAPTIVE_STAGE_FOCUSED_GAP_DP..MAX_ADAPTIVE_STAGE_FOCUSED_GAP_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(focusedGapDp = value))
            }
        }
        AdaptiveStageSlider(
            "Corner radius",
            appearance.geometry.cornerRadiusDp,
            MIN_ADAPTIVE_STAGE_CORNER_RADIUS_DP..MAX_ADAPTIVE_STAGE_CORNER_RADIUS_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(cornerRadiusDp = value))
            }
        }
        AdaptiveStageSlider(
            "Content padding",
            appearance.geometry.contentPaddingDp,
            MIN_ADAPTIVE_STAGE_CONTENT_PADDING_DP..MAX_ADAPTIVE_STAGE_CONTENT_PADDING_DP,
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
    SettingsSection(title = "Stack and stack") {
        AdaptiveStageSlider(
            "Visible card depth",
            appearance.geometry.visibleDepth,
            MIN_ADAPTIVE_STAGE_VISIBLE_DEPTH..MAX_ADAPTIVE_STAGE_VISIBLE_DEPTH,
            "cards",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(visibleDepth = value))
            }
        }
        AdaptiveStageSlider(
            "Card overlap",
            appearance.geometry.overlapPercent,
            MIN_ADAPTIVE_STAGE_OVERLAP_PERCENT..MAX_ADAPTIVE_STAGE_OVERLAP_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(overlapPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Vertical spacing",
            appearance.geometry.verticalSpacingDp,
            MIN_ADAPTIVE_STAGE_VERTICAL_SPACING_DP..MAX_ADAPTIVE_STAGE_VERTICAL_SPACING_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(verticalSpacingDp = value))
            }
        }
        AdaptiveStageSlider(
            "Horizontal offset",
            appearance.geometry.horizontalOffsetDp,
            MIN_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP..MAX_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(horizontalOffsetDp = value))
            }
        }
        AdaptiveStageSlider(
            "Stack curve",
            appearance.geometry.curveDp,
            MIN_ADAPTIVE_STAGE_CURVE_DP..MAX_ADAPTIVE_STAGE_CURVE_DP,
            "dp",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(curveDp = value))
            }
        }
        AdaptiveStageSlider(
            "Card rotation",
            appearance.geometry.rotationDegrees,
            MIN_ADAPTIVE_STAGE_ROTATION_DEGREES..MAX_ADAPTIVE_STAGE_ROTATION_DEGREES,
            "°",
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(rotationDegrees = value))
            }
        }
        AdaptiveStageEnumChoices(
            "Fan direction",
            AdaptiveStageFanDirection.entries,
            appearance.geometry.fanDirection,
            AdaptiveStageFanDirection::label,
        ) { value ->
            update {
                it.copy(geometry = it.geometry.copy(fanDirection = value))
            }
        }
    }
    SettingsSection(title = "Surface and glass") {
        AdaptiveStageEnumChoices(
            "Background",
            AdaptiveStageBackgroundSource.entries,
            appearance.surface.backgroundSource,
            AdaptiveStageBackgroundSource::label,
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(backgroundSource = value))
            }
        }
        AdaptiveStageColorChoices("Custom background", appearance.surface.customBackgroundArgb) { value ->
            update {
                it.copy(surface = it.surface.copy(customBackgroundArgb = value))
            }
        }
        AdaptiveStageSlider(
            "Glass transparency",
            appearance.surface.glassTransparencyPercent,
            MIN_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT..MAX_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(glassTransparencyPercent = value))
            }
        }
        AdaptiveStageColorChoices("Glass tint", appearance.surface.glassTintArgb) { value ->
            update {
                it.copy(surface = it.surface.copy(glassTintArgb = value))
            }
        }
        AdaptiveStageSlider(
            "Blur strength",
            appearance.surface.blurStrengthPercent,
            MIN_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT..MAX_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(blurStrengthPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Saturation",
            appearance.surface.saturationPercent,
            MIN_ADAPTIVE_STAGE_SATURATION_PERCENT..MAX_ADAPTIVE_STAGE_SATURATION_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(saturationPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Contrast",
            appearance.surface.contrastPercent,
            MIN_ADAPTIVE_STAGE_CONTRAST_PERCENT..MAX_ADAPTIVE_STAGE_CONTRAST_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(contrastPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Outline width",
            appearance.surface.outlineWidthDp,
            MIN_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP..MAX_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP,
            "dp",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(outlineWidthDp = value))
            }
        }
        AdaptiveStageSlider(
            "Highlight",
            appearance.surface.highlightPercent,
            MIN_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT..MAX_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(highlightPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Shadow elevation",
            appearance.surface.shadowElevationDp,
            MIN_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP..MAX_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP,
            "dp",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(shadowElevationDp = value))
            }
        }
        AdaptiveStageSlider(
            "Texture intensity",
            appearance.surface.textureIntensityPercent,
            MIN_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT..MAX_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(surface = it.surface.copy(textureIntensityPercent = value))
            }
        }
    }
    SettingsSection(title = "Colour and content") {
        AdaptiveStageEnumChoices(
            "Accent",
            AdaptiveStageAccentSource.entries,
            appearance.typography.accentSource,
            AdaptiveStageAccentSource::label,
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(accentSource = value))
            }
        }
        AdaptiveStageColorChoices("Custom accent", appearance.typography.customAccentArgb) { value ->
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
        AdaptiveStageEnumChoices(
            "Content density",
            AdaptiveStageContentDensity.entries,
            appearance.typography.contentDensity,
            AdaptiveStageContentDensity::label,
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(contentDensity = value))
            }
        }
        AdaptiveStageSlider(
            "Text scale",
            appearance.typography.textScalePercent,
            MIN_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT..MAX_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(typography = it.typography.copy(textScalePercent = value))
            }
        }
    }
    SettingsSection(title = "Motion") {
        AdaptiveStageSlider(
            "Settle duration",
            appearance.motion.settleDurationMillis,
            MIN_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS..MAX_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(settleDurationMillis = value))
            }
        }
        AdaptiveStageSlider(
            "Reflow duration",
            appearance.motion.reflowDurationMillis,
            MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS..MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(reflowDurationMillis = value))
            }
        }
        AdaptiveStageSlider(
            "Enter duration",
            appearance.motion.enterDurationMillis,
            MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS..MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(enterDurationMillis = value))
            }
        }
        AdaptiveStageSlider(
            "Exit duration",
            appearance.motion.exitDurationMillis,
            MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS..MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(exitDurationMillis = value))
            }
        }
        AdaptiveStageSlider(
            "Expand duration",
            appearance.motion.expandDurationMillis,
            MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS..MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
            "ms",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(expandDurationMillis = value))
            }
        }
        AdaptiveStageEnumChoices(
            "Easing",
            AdaptiveStageEasing.entries,
            appearance.motion.easing,
            AdaptiveStageEasing::label,
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(easing = value))
            }
        }
        AdaptiveStageSlider(
            "Spring bounciness",
            appearance.motion.springBouncinessPercent,
            MIN_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT..MAX_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(springBouncinessPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Travel intensity",
            appearance.motion.travelIntensityPercent,
            MIN_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT..MAX_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(travelIntensityPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Parallax intensity",
            appearance.motion.parallaxIntensityPercent,
            MIN_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT..MAX_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(parallaxIntensityPercent = value))
            }
        }
        AdaptiveStageSlider(
            "Rotation intensity",
            appearance.motion.rotationIntensityPercent,
            MIN_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT..MAX_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT,
            "%",
        ) { value ->
            update {
                it.copy(motion = it.motion.copy(rotationIntensityPercent = value))
            }
        }
        AdaptiveStageEnumChoices(
            "Haptic strength",
            AdaptiveStageHapticStrength.entries,
            appearance.motion.hapticStrength,
            AdaptiveStageHapticStrength::label,
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
            title = { Text("Reset Cards appearance?") },
            text = { Text("This replaces all Cards appearance, geometry, and motion choices with the Modern preset.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        update { it.reset() }
                        resetConfirmationVisible = false
                    },
                    modifier = Modifier.semantics { contentDescription = "Confirm Cards reset" },
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetConfirmationVisible = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AdaptiveStageSlider(
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
private fun <T> AdaptiveStageEnumChoices(
    title: String,
    values: Iterable<T>,
    selected: T,
    label: (T) -> String,
    testTag: ((T) -> String)? = null,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsTextColumn(title = title, subtitle = label(selected))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    modifier =
                        Modifier
                            .semantics { contentDescription = "$title: ${label(value)}" }
                            .then(testTag?.let { Modifier.testTag(it(value)) } ?: Modifier),
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
private fun AdaptiveStageColorChoices(
    title: String,
    selected: Long,
    onSelected: (Long) -> Unit,
) {
    val colors = listOf(0xFF1B1B1FL, 0xFF355C7DL, 0xFF6C5B7BL, 0xFFC06C84L, 0xFFFFFFFFL)
    AdaptiveStageEnumChoices(
        title = title,
        values = colors,
        selected = selected,
        label = { color -> "#${color.toString(16).takeLast(6).uppercase()}" },
        onSelected = onSelected,
    )
}

private fun adaptiveStageFallbackMessage(
    appearance: AdaptiveStageAppearanceSettings,
    rendererCapabilities: AdaptiveStageRendererCapabilities,
): String? {
    val effective = appearance.effectiveFor(rendererCapabilities)
    return when {
        appearance.motion.reducedTransparency -> "Reduced transparency is on: glass and blur are disabled."
        appearance.surface.blurStrengthPercent != effective.surface.blurStrengthPercent ->
            "Blur is unavailable on this device; the preview shows the opaque fallback."
        appearance.surface.textureIntensityPercent != effective.surface.textureIntensityPercent ->
            "Texture is unavailable on this device; the preview omits it."
        else -> null
    }
}

internal fun adaptiveStageAppearancePresetAction(preset: AdaptiveStageAppearancePreset): LauncherShellAction.UpdateAdaptiveStageAppearance =
    LauncherShellAction.UpdateAdaptiveStageAppearance(
        AdaptiveStageAppearanceSettings.modern().applyPreset(preset).coerce(),
    )

private fun AdaptiveStageAppearancePreset.label(): String =
    when (this) {
        AdaptiveStageAppearancePreset.MODERN_ADAPTIVE_STAGE -> "Modern"
        AdaptiveStageAppearancePreset.FLAT_REDUCED_DEPTH -> "Flat"
        AdaptiveStageAppearancePreset.WARM_GLASS -> "Warm glass"
    }

private fun AdaptiveStageBackgroundSource.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun AdaptiveStageAccentSource.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun AdaptiveStageContentDensity.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun AdaptiveStageFanDirection.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun AdaptiveStageEasing.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun AdaptiveStageHapticStrength.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun AdaptiveStagePaneArrangement.label(): String =
    when (this) {
        AdaptiveStagePaneArrangement.STACK -> "Stack"
        AdaptiveStagePaneArrangement.SPLIT -> "Split"
    }

private fun AdaptiveStageRailSide.label(): String =
    when (this) {
        AdaptiveStageRailSide.LEADING -> "Leading edge"
        AdaptiveStageRailSide.TRAILING -> "Trailing edge"
        AdaptiveStageRailSide.TOP -> "Top edge"
        AdaptiveStageRailSide.BOTTOM -> "Bottom edge"
    }
