package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.CustomPair
import com.riffle.app.launcher.diagnostics.pair.PairMethod
import com.riffle.app.launcher.diagnostics.pair.PairScenario

// The collapsed "Advanced" part of the "App pair experiment" page (SettingsAppPairPage.kt): everything round 1 and the
// Custom recipe had, unchanged, behind one expandable row.

internal const val APP_PAIR_ADVANCED_TAG = "appPair.advanced"
internal const val APP_PAIR_EDIT_AS_CUSTOM_TAG = "appPair.editAsCustom"
internal const val APP_PAIR_METHOD_TAG_PREFIX = "appPair.method."
internal const val APP_PAIR_DELAY_DESCRIPTION = "Delay before the second app"
internal const val APP_PAIR_MAX_DELAY_MS = 2000
private const val DELAY_STEP_MS = 100

/** What the Advanced part edits: the method, the Custom switches and the delay the scenarios share. */
internal class PairAdvancedValues(
    val method: PairMethod,
    val custom: CustomPair,
    val delayMillis: Float,
    /** The scenario of the newest entry, whose flags "Edit as custom" loads; null when nothing was run yet. */
    val loadable: PairScenario?,
)

internal class PairAdvancedActions(
    val onMethod: (PairMethod) -> Unit,
    val onCustom: (CustomPair) -> Unit,
    val onDelay: (Float) -> Unit,
    val onEditAsCustom: (PairScenario) -> Unit,
    val onLaunch: () -> Unit,
)

@Composable
internal fun PairAdvancedSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    launchSubtitle: String,
    values: PairAdvancedValues,
    actions: PairAdvancedActions,
) {
    SettingsSection(title = stringResource(R.string.app_pair_section_advanced)) {
        val state = stringResource(if (expanded) R.string.app_pair_advanced_hide else R.string.app_pair_advanced_show)
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_ADVANCED_TAG).semantics { stateDescription = state },
            title = stringResource(R.string.app_pair_advanced),
            subtitle = stringResource(R.string.app_pair_advanced_summary),
            onClick = onToggle,
            trailingContent = { SettingsButtonText(text = state) },
        )
    }
    if (!expanded) return
    SettingsSection(title = stringResource(R.string.app_pair_section_method)) {
        PairMethod.entries.forEach { option ->
            SettingsClickableRow(
                modifier = Modifier.testTag(APP_PAIR_METHOD_TAG_PREFIX + option.name),
                title = option.title,
                subtitle = option.summary,
                onClick = { actions.onMethod(option) },
                trailingContent =
                    if (option == values.method) {
                        { SettingsButtonText(text = stringResource(R.string.app_pair_method_selected)) }
                    } else {
                        null
                    },
            )
        }
    }
    if (values.method == PairMethod.CUSTOM) {
        PairCustomSections(
            custom = values.custom,
            delayMillis = values.delayMillis.toInt(),
            onChange = actions.onCustom,
        )
        EditAsCustomRow(values.loadable, actions.onEditAsCustom)
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_advanced_launch)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.app_pair_delay, values.delayMillis.toInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = APP_PAIR_DELAY_DESCRIPTION },
                value = values.delayMillis,
                onValueChange = actions.onDelay,
                valueRange = 0f..APP_PAIR_MAX_DELAY_MS.toFloat(),
                steps = APP_PAIR_MAX_DELAY_MS / DELAY_STEP_MS - 1,
            )
            SettingsSecondaryText(text = stringResource(R.string.app_pair_delay_note))
        }
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_LAUNCH_TAG),
            title = stringResource(R.string.app_pair_launch),
            subtitle = launchSubtitle,
            onClick = actions.onLaunch,
            trailingContent = null,
        )
    }
}

@Composable
private fun EditAsCustomRow(
    loadable: PairScenario?,
    onEditAsCustom: (PairScenario) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.app_pair_section_load)) {
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_EDIT_AS_CUSTOM_TAG),
            title = stringResource(R.string.app_pair_edit_as_custom),
            subtitle =
                loadable?.let { stringResource(R.string.app_pair_edit_as_custom_summary, it.logName) }
                    ?: stringResource(R.string.app_pair_edit_as_custom_none),
            enabled = loadable != null,
            onClick = { loadable?.let(onEditAsCustom) },
            trailingContent = null,
        )
    }
}
