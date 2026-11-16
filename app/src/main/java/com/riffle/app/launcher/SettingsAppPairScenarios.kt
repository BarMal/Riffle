package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.PairLogEntry
import com.riffle.app.launcher.diagnostics.pair.PairScenario

// The "Run a scenario" section of the "App pair experiment" page (SettingsAppPairPage.kt): one row and one tap each.

internal const val APP_PAIR_SCENARIO_TAG_PREFIX = "appPair.scenario."
internal const val APP_PAIR_SCENARIO_RESULT_TAG_PREFIX = "appPair.scenarioResult."

private const val DISABLED_ROW_ALPHA = 0.5f

/** The scenarios, in the order the owner runs them, with the delay line and the hint while two apps are missing. */
@Composable
internal fun PairScenarioSection(
    enabled: Boolean,
    delayMillis: Int,
    log: List<PairLogEntry>,
    onRun: (PairScenario) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.app_pair_section_scenarios)) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.app_pair_scenarios_delay, delayMillis),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!enabled) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(R.string.app_pair_scenarios_needs_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PairScenario.entries.forEach { scenario ->
            val latest = log.firstOrNull { it.scenario == scenario.logName }
            SettingsClickableRow(
                modifier = Modifier.testTag(APP_PAIR_SCENARIO_TAG_PREFIX + scenario.name).alpha(rowAlpha(enabled)),
                title = stringResource(scenario.titleRes()),
                subtitle = stringResource(scenario.summaryRes()),
                enabled = enabled,
                onClick = { onRun(scenario) },
                trailingContent = { ScenarioTrailing(scenario, latest) },
            )
        }
    }
}

private fun rowAlpha(enabled: Boolean) = if (enabled) 1f else DISABLED_ROW_ALPHA

/** "Run", and under it a small label with the verdict of the scenario's most recent entry, if it has one. */
@Composable
private fun ScenarioTrailing(
    scenario: PairScenario,
    latest: PairLogEntry?,
) {
    Column(horizontalAlignment = Alignment.End) {
        SettingsButtonText(text = stringResource(R.string.app_pair_scenario_run))
        if (latest != null) {
            Text(
                modifier = Modifier.testTag(APP_PAIR_SCENARIO_RESULT_TAG_PREFIX + scenario.name),
                text = resultLabel(latest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun resultLabel(entry: PairLogEntry): String {
    val verdict =
        entry.verdict?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.app_pair_scenario_not_marked)
    return if (entry.duplicatesInRecents) {
        verdict + ", " + stringResource(R.string.app_pair_scenario_duplicates)
    } else {
        verdict
    }
}

private fun PairScenario.titleRes(): Int =
    when (this) {
        PairScenario.RESET_BOTH -> R.string.app_pair_scenario_reset_both
        PairScenario.ONE_CALL_RESET_BOTH -> R.string.app_pair_scenario_one_call_reset_both
        PairScenario.FRESH_BOTH -> R.string.app_pair_scenario_fresh_both
        PairScenario.REUSE_BOTH -> R.string.app_pair_scenario_reuse_both
        PairScenario.REUSE_FIRST_FRESH_SECOND -> R.string.app_pair_scenario_reuse_first_fresh_second
        PairScenario.FRESH_FIRST_REUSE_SECOND -> R.string.app_pair_scenario_fresh_first_reuse_second
        PairScenario.ONE_CALL -> R.string.app_pair_scenario_one_call
    }

private fun PairScenario.summaryRes(): Int =
    when (this) {
        PairScenario.RESET_BOTH -> R.string.app_pair_scenario_reset_both_summary
        PairScenario.ONE_CALL_RESET_BOTH -> R.string.app_pair_scenario_one_call_reset_both_summary
        PairScenario.FRESH_BOTH -> R.string.app_pair_scenario_fresh_both_summary
        PairScenario.REUSE_BOTH -> R.string.app_pair_scenario_reuse_both_summary
        PairScenario.REUSE_FIRST_FRESH_SECOND -> R.string.app_pair_scenario_reuse_first_fresh_second_summary
        PairScenario.FRESH_FIRST_REUSE_SECOND -> R.string.app_pair_scenario_fresh_first_reuse_second_summary
        PairScenario.ONE_CALL -> R.string.app_pair_scenario_one_call_summary
    }

/** The newest entry with the prominent verdict buttons and the Duplicates switch, then Copy log. */
@Composable
internal fun PairLatestResultSection(
    log: List<PairLogEntry>,
    copied: Boolean,
    experiment: AppPairExperiment,
    onCopy: () -> Unit,
) {
    SettingsSection(title = stringResource(R.string.app_pair_section_latest)) {
        val newest = log.firstOrNull()
        if (newest == null) {
            SettingsListRow(title = stringResource(R.string.app_pair_log_empty))
        } else {
            PairEntryWithVerdict(newest, prominent = true, experiment = experiment)
        }
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_COPY_TAG),
            title = stringResource(R.string.app_pair_copy_log),
            subtitle =
                stringResource(
                    if (copied) R.string.app_pair_log_copied else R.string.app_pair_copy_log_subtitle,
                ),
            onClick = onCopy,
            trailingContent = null,
        )
    }
}

/** Every entry but the newest, with text-button verdicts; absent while there is nothing older. */
@Composable
internal fun PairEarlierResultsSection(
    older: List<PairLogEntry>,
    experiment: AppPairExperiment,
) {
    if (older.isEmpty()) return
    SettingsSection(title = stringResource(R.string.app_pair_section_earlier)) {
        older.forEach { PairEntryWithVerdict(it, prominent = false, experiment = experiment) }
    }
}

@Composable
private fun PairEntryWithVerdict(
    entry: PairLogEntry,
    prominent: Boolean,
    experiment: AppPairExperiment,
) {
    PairLogItem(entry)
    PairVerdictControls(
        entry = entry,
        prominent = prominent,
        onVerdict = { experiment.setVerdict(entry.id, it) },
        onDuplicates = { experiment.setDuplicates(entry.id, it) },
    )
}
