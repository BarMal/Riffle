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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide
import com.riffle.core.domain.launcher.settings.AdaptiveStageAccentSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageBackgroundSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageContentDensity
import com.riffle.core.domain.launcher.settings.AdaptiveStageEasing
import com.riffle.core.domain.launcher.settings.AdaptiveStageFanDirection
import com.riffle.core.domain.launcher.settings.AdaptiveStageHapticStrength
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities
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

private typealias AdaptiveStageAppearanceUpdate = ((AdaptiveStageAppearanceSettings) -> AdaptiveStageAppearanceSettings) -> Unit

@Composable
internal fun AdaptiveStageAppearancePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    rendererCapabilities: AdaptiveStageRendererCapabilities = adaptiveStageRendererCapabilities(),
) {
    var editorTarget by rememberSaveable { mutableStateOf(AdaptiveStageAppearanceEditorTarget.FOLDED) }
    var selectedTab by rememberSaveable { mutableStateOf(AdaptiveStageAppearanceTab.LAYOUT) }
    val appearance =
        when (editorTarget) {
            AdaptiveStageAppearanceEditorTarget.FOLDED -> state.settings.cards.adaptiveStageAppearance
            AdaptiveStageAppearanceEditorTarget.UNFOLDED -> state.settings.cards.unfoldedAppearance
        }
    var resetConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val update: AdaptiveStageAppearanceUpdate = { transform ->
        val next = transform(appearance).coerce()
        onAction(
            when (editorTarget) {
                AdaptiveStageAppearanceEditorTarget.FOLDED -> LauncherShellAction.UpdateAdaptiveStageAppearance(next)
                AdaptiveStageAppearanceEditorTarget.UNFOLDED ->
                    LauncherShellAction.UpdateUnfoldedAdaptiveStageAppearance(next)
            },
        )
    }

    Column(modifier = modifier) {
        // Sticky header: the target chooser and reset action share one compact row instead of
        // two separately titled, separately carded sections, and the preview drops its own
        // "Preview" label -- on a real device the previous three stacked sections left no room
        // for any tab content at all. The reset row's old subtitle is dropped too since the
        // confirmation dialog already explains what resetting does.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AdaptiveStageTargetToggle(selected = editorTarget, onSelected = { target -> editorTarget = target })
            TextButton(onClick = { resetConfirmationVisible = true }) {
                SettingsButtonText(text = "Reset ${editorTarget.label()}")
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AdaptiveStageAppearancePreview(
                appearance = appearance,
                globalReducedMotion = state.settings.motion.reducedMotion,
                rendererCapabilities = rendererCapabilities,
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 300.dp),
            )
        }
        adaptiveStageFallbackMessage(appearance, rendererCapabilities)?.let { message ->
            SettingsListRow(title = "Effective fallback", subtitle = message)
        }
        AdaptiveStageAppearanceTabRow(selected = selectedTab, onSelected = { tab -> selectedTab = tab })
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selectedTab) {
                AdaptiveStageAppearanceTab.LAYOUT ->
                    AdaptiveStageLayoutTabContent(state = state, onAction = onAction)

                AdaptiveStageAppearanceTab.GEOMETRY ->
                    AdaptiveStageGeometryTabContent(appearance = appearance, update = update)

                AdaptiveStageAppearanceTab.SURFACE ->
                    AdaptiveStageSurfaceTabContent(appearance = appearance, update = update)

                AdaptiveStageAppearanceTab.COLOR ->
                    AdaptiveStageColorTabContent(appearance = appearance, update = update)

                AdaptiveStageAppearanceTab.MOTION ->
                    AdaptiveStageMotionTabContent(appearance = appearance, update = update)

                AdaptiveStageAppearanceTab.ACCESSIBILITY ->
                    AdaptiveStageAccessibilityTabContent(appearance = appearance, update = update)
            }
        }
    }
    if (resetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { resetConfirmationVisible = false },
            title = { Text("Reset ${editorTarget.label()} Cards appearance?") },
            text = {
                Text(
                    "This replaces all ${editorTarget.label().lowercase()} Cards appearance, geometry, and" +
                        " motion choices with its default values.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        update { editorTarget.defaultAppearance() }
                        resetConfirmationVisible = false
                    },
                    modifier = Modifier.semantics { contentDescription = "Confirm Cards reset" },
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetConfirmationVisible = false }) { Text("Cancel") } },
        )
    }
}

internal enum class AdaptiveStageAppearanceEditorTarget { FOLDED, UNFOLDED }

internal fun AdaptiveStageAppearanceEditorTarget.defaultAppearance(): AdaptiveStageAppearanceSettings =
    when (this) {
        AdaptiveStageAppearanceEditorTarget.FOLDED -> AdaptiveStageAppearanceSettings.modern()
        AdaptiveStageAppearanceEditorTarget.UNFOLDED -> AdaptiveStageAppearanceSettings.unfolded()
    }

internal fun AdaptiveStageAppearanceEditorTarget.label(): String =
    when (this) {
        AdaptiveStageAppearanceEditorTarget.FOLDED -> "Folded"
        AdaptiveStageAppearanceEditorTarget.UNFOLDED -> "Unfolded"
    }

internal enum class AdaptiveStageAppearanceTab { LAYOUT, GEOMETRY, SURFACE, COLOR, MOTION, ACCESSIBILITY }

internal fun AdaptiveStageAppearanceTab.label(): String =
    when (this) {
        AdaptiveStageAppearanceTab.LAYOUT -> "Layout"
        AdaptiveStageAppearanceTab.GEOMETRY -> "Geometry"
        AdaptiveStageAppearanceTab.SURFACE -> "Surface"
        AdaptiveStageAppearanceTab.COLOR -> "Color"
        AdaptiveStageAppearanceTab.MOTION -> "Motion"
        AdaptiveStageAppearanceTab.ACCESSIBILITY -> "Accessibility"
    }

@Composable
private fun AdaptiveStageAppearanceTabRow(
    selected: AdaptiveStageAppearanceTab,
    onSelected: (AdaptiveStageAppearanceTab) -> Unit,
) {
    val tabs = AdaptiveStageAppearanceTab.entries
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selected),
        edgePadding = 8.dp,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                text = { Text(tab.label()) },
                modifier =
                    Modifier
                        .semantics { contentDescription = "Appearance section: ${tab.label()}" }
                        .testTag("adaptive-stage-appearance-tab-${tab.name}"),
            )
        }
    }
}

@Composable
private fun AdaptiveStageTargetToggle(
    selected: AdaptiveStageAppearanceEditorTarget,
    onSelected: (AdaptiveStageAppearanceEditorTarget) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveStageAppearanceEditorTarget.entries.forEach { target ->
            FilterChip(
                selected = target == selected,
                onClick = { onSelected(target) },
                label = { Text(target.label()) },
                modifier =
                    Modifier
                        .semantics { contentDescription = "Appearance target: ${target.label()}" }
                        .testTag("adaptive-stage-appearance-target-${target.name}"),
            )
        }
    }
}

@Composable
private fun AdaptiveStageLayoutTabContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = "Layout") {
        SettingsListRow(
            title = "About Folded and Unfolded",
            subtitle =
                "Folded is the single-stage, full-size stack. Unfolded is the docked rail" +
                    " shown alongside content on a larger or unfolded screen. Each has its own" +
                    " independent appearance.",
        )
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
}

@Composable
private fun AdaptiveStageGeometryTabContent(
    appearance: AdaptiveStageAppearanceSettings,
    update: AdaptiveStageAppearanceUpdate,
) {
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
}

@Composable
private fun AdaptiveStageSurfaceTabContent(
    appearance: AdaptiveStageAppearanceSettings,
    update: AdaptiveStageAppearanceUpdate,
) {
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
        // Only actually used when Background above is set to Custom -- see
        // resolveAdaptiveStageCardColors, which ignores customBackgroundArgb for every other
        // source. Showing it unconditionally read as two competing background controls.
        if (appearance.surface.backgroundSource == AdaptiveStageBackgroundSource.CUSTOM_SOLID) {
            AdaptiveStageColorChoices("Custom background", appearance.surface.customBackgroundArgb) { value ->
                update {
                    it.copy(surface = it.surface.copy(customBackgroundArgb = value))
                }
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
}

@Composable
private fun AdaptiveStageColorTabContent(
    appearance: AdaptiveStageAppearanceSettings,
    update: AdaptiveStageAppearanceUpdate,
) {
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
        // Only actually used when Accent above is set to Custom -- see
        // resolveAdaptiveStageCardColors, which ignores customAccentArgb for every other source.
        if (appearance.typography.accentSource == AdaptiveStageAccentSource.CUSTOM) {
            AdaptiveStageColorChoices("Custom accent", appearance.typography.customAccentArgb) { value ->
                update {
                    it.copy(typography = it.typography.copy(customAccentArgb = value))
                }
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
}

@Composable
private fun AdaptiveStageMotionTabContent(
    appearance: AdaptiveStageAppearanceSettings,
    update: AdaptiveStageAppearanceUpdate,
) {
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
}

@Composable
private fun AdaptiveStageAccessibilityTabContent(
    appearance: AdaptiveStageAppearanceSettings,
    update: AdaptiveStageAppearanceUpdate,
) {
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
