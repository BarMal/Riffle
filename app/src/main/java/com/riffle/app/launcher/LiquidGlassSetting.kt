package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.LiquidGlassSettings
import com.riffle.core.domain.launcher.settings.MAX_SPOTLIGHT_FLOW_MILLIS
import com.riffle.core.domain.launcher.settings.MIN_SPOTLIGHT_FLOW_MILLIS
import com.riffle.core.domain.launcher.settings.SPOTLIGHT_FLOW_STEP_MILLIS

/**
 * Settings for Riffle's glass material: the spotlight's pace, plus the reduced-transparency switch,
 * which is the only way to turn glass off (see [LiquidGlassSettings]).
 */
@Composable
internal fun LiquidGlassSetting(
    settings: LiquidGlassSettings,
    reducedTransparency: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_REDUCED_TRANSPARENCY),
            title = SettingKey.APPEARANCE_REDUCED_TRANSPARENCY.label(),
            subtitle =
                if (reducedTransparency) {
                    "Glass surfaces are opaque; their edge lights are faint"
                } else {
                    "Make every glass surface opaque, with faint edge lights"
                },
            checked = reducedTransparency,
            onCheckedChange = { reduced -> onAction(LauncherShellAction.SelectReducedTransparency(reduced)) },
        )
        // In 50ms steps rather than single milliseconds: twenty stops is a dial you can land on.
        DiscreteSettingSlider(
            modifier = Modifier.settingAnchor(SettingKey.APPEARANCE_SPOTLIGHT),
            title = SettingKey.APPEARANCE_SPOTLIGHT.label(),
            value = settings.spotlightFlowMillis / SPOTLIGHT_FLOW_STEP_MILLIS,
            valueRange = SPOTLIGHT_FLOW_SLIDER_STEPS,
            valueLabel = { step -> spotlightFlowLabel(step * SPOTLIGHT_FLOW_STEP_MILLIS) },
            onValueChange = { step ->
                onAction(LauncherShellAction.SelectLiquidGlassSpotlightFlow(step * SPOTLIGHT_FLOW_STEP_MILLIS))
            },
        )
    }
}

/** How the spotlight flow reads on its slider: "Instant" at 0, otherwise its time constant. */
internal fun spotlightFlowLabel(millis: Int): String = if (millis <= 0) "Instant" else "$millis ms"

/** The spotlight flow slider's stops, in [SPOTLIGHT_FLOW_STEP_MILLIS] steps. */
private val SPOTLIGHT_FLOW_SLIDER_STEPS =
    (MIN_SPOTLIGHT_FLOW_MILLIS / SPOTLIGHT_FLOW_STEP_MILLIS)..(MAX_SPOTLIGHT_FLOW_MILLIS / SPOTLIGHT_FLOW_STEP_MILLIS)
