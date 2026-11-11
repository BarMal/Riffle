package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LiquidGlassSettings
import com.riffle.core.domain.launcher.settings.MAX_LIQUID_GLASS_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_SPOTLIGHT_FLOW_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_LIQUID_GLASS_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_SPOTLIGHT_FLOW_MILLIS
import com.riffle.core.domain.launcher.settings.SPOTLIGHT_FLOW_STEP_MILLIS

/**
 * Settings for Riffle's liquid-glass material: a master toggle plus two independent strength axes
 * (see [LiquidGlassSettings] for why they are independent rather than one combined dial).
 *
 * This PR wires the material into the dock's header pill/action capsule and the appearance tuning
 * sheet (`GlassSurface`'s 3 existing call sites) only; the dock's own background, card tiles, and
 * the rest of Settings pick it up in follow-up PRs, per the AGENTS.md product-priority ordering of
 * shipping one coherent slice at a time.
 */
@Composable
internal fun LiquidGlassSetting(
    settings: LiquidGlassSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSwitchRow(
            title = "Liquid glass",
            subtitle =
                if (settings.enabled) {
                    "Frosted, refractive material on the dock header and appearance sheet"
                } else {
                    "Floating chrome uses a flat tinted surface instead"
                },
            checked = settings.enabled,
            onCheckedChange = { enabled -> onAction(LauncherShellAction.SelectLiquidGlassEnabled(enabled)) },
        )
        if (settings.enabled) {
            DiscreteSettingSlider(
                title = "Frost strength",
                value = settings.frostStrengthPercent,
                valueRange = MIN_LIQUID_GLASS_STRENGTH_PERCENT..MAX_LIQUID_GLASS_STRENGTH_PERCENT,
                valueLabel = { "$it%" },
                onValueChange = { value -> onAction(LauncherShellAction.SelectLiquidGlassFrostStrength(value)) },
            )
            DiscreteSettingSlider(
                title = "Edge refraction",
                value = settings.refractionStrengthPercent,
                valueRange = MIN_LIQUID_GLASS_STRENGTH_PERCENT..MAX_LIQUID_GLASS_STRENGTH_PERCENT,
                valueLabel = { "$it%" },
                onValueChange = { value -> onAction(LauncherShellAction.SelectLiquidGlassRefractionStrength(value)) },
            )
            // In 50ms steps rather than single milliseconds: twenty stops is a dial you can land on.
            DiscreteSettingSlider(
                title = "Spotlight flow",
                value = settings.spotlightFlowMillis / SPOTLIGHT_FLOW_STEP_MILLIS,
                valueRange = SPOTLIGHT_FLOW_SLIDER_STEPS,
                valueLabel = { step -> spotlightFlowLabel(step * SPOTLIGHT_FLOW_STEP_MILLIS) },
                onValueChange = { step ->
                    onAction(LauncherShellAction.SelectLiquidGlassSpotlightFlow(step * SPOTLIGHT_FLOW_STEP_MILLIS))
                },
            )
        }
    }
}

/** How the spotlight flow reads on its slider: "Instant" at 0, otherwise its time constant. */
internal fun spotlightFlowLabel(millis: Int): String = if (millis <= 0) "Instant" else "$millis ms"

/** The spotlight flow slider's stops, in [SPOTLIGHT_FLOW_STEP_MILLIS] steps. */
private val SPOTLIGHT_FLOW_SLIDER_STEPS =
    (MIN_SPOTLIGHT_FLOW_MILLIS / SPOTLIGHT_FLOW_STEP_MILLIS)..(MAX_SPOTLIGHT_FLOW_MILLIS / SPOTLIGHT_FLOW_STEP_MILLIS)
