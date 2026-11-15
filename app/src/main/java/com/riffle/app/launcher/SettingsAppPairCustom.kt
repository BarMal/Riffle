package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.CustomAppFlags
import com.riffle.app.launcher.diagnostics.pair.CustomPair
import com.riffle.app.launcher.diagnostics.pair.CustomSwitch
import com.riffle.app.launcher.diagnostics.pair.PairLogEntry
import com.riffle.app.launcher.diagnostics.pair.PairPreset
import com.riffle.app.launcher.diagnostics.pair.PairVerdict
import com.riffle.app.launcher.diagnostics.pair.isOn
import com.riffle.app.launcher.diagnostics.pair.with

// The Custom recipe and the after-the-fact note of the "App pair experiment" page (SettingsAppPairPage.kt).

internal const val APP_PAIR_PRESET_TAG_PREFIX = "appPair.preset."
internal const val APP_PAIR_SWITCH_TAG_PREFIX = "appPair.switch."
internal const val APP_PAIR_ONE_CALL_TAG = "appPair.oneCall"
internal const val APP_PAIR_RESULT_FLAGS_TAG = "appPair.resultFlags"
internal const val APP_PAIR_FIRST_OPEN_TAG = "appPair.open.first"
internal const val APP_PAIR_SECOND_OPEN_TAG = "appPair.open.second"
internal const val APP_PAIR_VERDICT_TAG_PREFIX = "appPair.verdict."
internal const val APP_PAIR_DUPLICATES_TAG_PREFIX = "appPair.duplicates."

/** Test tag of the switch [switch] of the app in [slot] ("first" or "second") of the Custom recipe. */
internal fun appPairSwitchTag(
    slot: String,
    switch: CustomSwitch,
) = "$APP_PAIR_SWITCH_TAG_PREFIX$slot.${switch.name}"

@Composable
internal fun PairCustomSections(
    custom: CustomPair,
    delayMillis: Int,
    onChange: (CustomPair) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.app_pair_section_custom)) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.app_pair_custom_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_presets)) {
        PairPreset.entries.forEach { preset ->
            SettingsClickableRow(
                modifier = Modifier.testTag(APP_PAIR_PRESET_TAG_PREFIX + preset.name),
                title = stringResource(preset.titleRes()),
                subtitle = stringResource(preset.summaryRes()),
                onClick = { onChange(preset.pair) },
                trailingContent = { SettingsButtonText(text = stringResource(R.string.app_pair_preset_use)) },
            )
        }
    }
    SettingsSection(title = stringResource(R.string.app_pair_switches_first)) {
        CustomSwitchRows("first", custom.first) { onChange(custom.copy(first = it)) }
    }
    SettingsSection(title = stringResource(R.string.app_pair_switches_second)) {
        CustomSwitchRows("second", custom.second) { onChange(custom.copy(second = it)) }
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_resulting)) {
        SettingsSwitchRow(
            modifier = Modifier.testTag(APP_PAIR_ONE_CALL_TAG),
            title = stringResource(R.string.app_pair_one_call),
            subtitle = stringResource(R.string.app_pair_one_call_summary),
            checked = custom.oneCall,
            onCheckedChange = { onChange(custom.copy(oneCall = it)) },
        )
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(APP_PAIR_RESULT_FLAGS_TAG),
            style = MaterialTheme.typography.bodyMedium,
            text =
                stringResource(
                    R.string.app_pair_resulting_flags,
                    custom.first.description,
                    custom.second.description,
                    if (custom.oneCall) {
                        stringResource(R.string.app_pair_resulting_timing_one_call)
                    } else {
                        stringResource(R.string.app_pair_resulting_timing_delay, delayMillis)
                    },
                ),
        )
    }
}

@Composable
private fun CustomSwitchRows(
    slot: String,
    flags: CustomAppFlags,
    onChange: (CustomAppFlags) -> Unit,
) {
    CustomSwitch.entries.forEach { switch ->
        SettingsSwitchRow(
            modifier = Modifier.testTag(appPairSwitchTag(slot, switch)),
            title = switch.label,
            subtitle = stringResource(switch.summaryRes()),
            checked = flags.isOn(switch),
            onCheckedChange = { onChange(flags.with(switch, it)) },
        )
    }
}

private fun CustomSwitch.summaryRes(): Int =
    when (this) {
        CustomSwitch.ADJACENT -> R.string.app_pair_switch_adjacent_summary
        CustomSwitch.MULTIPLE_TASK -> R.string.app_pair_switch_multiple_summary
        CustomSwitch.CLEAR_TASK -> R.string.app_pair_switch_clear_summary
        CustomSwitch.NO_ANIMATION -> R.string.app_pair_switch_no_animation_summary
    }

private fun PairPreset.titleRes(): Int =
    when (this) {
        PairPreset.FRESH_BOTH -> R.string.app_pair_preset_fresh_both
        PairPreset.REUSE_BOTH -> R.string.app_pair_preset_reuse_both
        PairPreset.RESET_BOTH -> R.string.app_pair_preset_reset_both
        PairPreset.REUSE_FIRST_FRESH_SECOND -> R.string.app_pair_preset_reuse_first_fresh_second
        PairPreset.FRESH_FIRST_REUSE_SECOND -> R.string.app_pair_preset_fresh_first_reuse_second
        PairPreset.ONE_CALL_RESET_BOTH -> R.string.app_pair_preset_one_call_reset_both
    }

private fun PairPreset.summaryRes(): Int =
    when (this) {
        PairPreset.FRESH_BOTH -> R.string.app_pair_preset_fresh_both_summary
        PairPreset.REUSE_BOTH -> R.string.app_pair_preset_reuse_both_summary
        PairPreset.RESET_BOTH -> R.string.app_pair_preset_reset_both_summary
        PairPreset.REUSE_FIRST_FRESH_SECOND -> R.string.app_pair_preset_reuse_first_fresh_second_summary
        PairPreset.FRESH_FIRST_REUSE_SECOND -> R.string.app_pair_preset_fresh_first_reuse_second_summary
        PairPreset.ONE_CALL_RESET_BOTH -> R.string.app_pair_preset_one_call_reset_both_summary
    }

private fun PairVerdict.labelRes(): Int =
    when (this) {
        PairVerdict.SPLIT -> R.string.app_pair_verdict_split
        PairVerdict.NO_SPLIT -> R.string.app_pair_verdict_no_split
        PairVerdict.SPLIT_UNEVEN -> R.string.app_pair_verdict_uneven
    }

/** The after-the-fact note for one entry: three verdict buttons (filled for the newest entry) and a Recents toggle. */
@Composable
internal fun PairVerdictControls(
    entry: PairLogEntry,
    prominent: Boolean,
    onVerdict: (PairVerdict) -> Unit,
    onDuplicates: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (prominent) {
            Text(text = stringResource(R.string.app_pair_verdict_prompt), style = MaterialTheme.typography.titleSmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PairVerdict.entries.forEach { verdict ->
                val selected = entry.verdict == verdict
                val modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("$APP_PAIR_VERDICT_TAG_PREFIX${entry.id}.${verdict.name}")
                        .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
                val label: @Composable () -> Unit = {
                    Text(
                        text = stringResource(verdict.labelRes()),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                val contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                when {
                    selected ->
                        Button(
                            modifier = modifier,
                            onClick = { onVerdict(verdict) },
                            contentPadding = contentPadding,
                        ) { label() }
                    prominent ->
                        OutlinedButton(
                            modifier = modifier,
                            onClick = { onVerdict(verdict) },
                            contentPadding = contentPadding,
                        ) { label() }
                    else ->
                        TextButton(
                            modifier = modifier,
                            onClick = { onVerdict(verdict) },
                            contentPadding = contentPadding,
                        ) { label() }
                }
            }
        }
    }
    SettingsSwitchRow(
        modifier = Modifier.testTag("$APP_PAIR_DUPLICATES_TAG_PREFIX${entry.id}"),
        title = stringResource(R.string.app_pair_duplicates),
        subtitle = stringResource(R.string.app_pair_duplicates_summary),
        checked = entry.duplicatesInRecents,
        onCheckedChange = onDuplicates,
    )
}
