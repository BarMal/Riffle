package com.riffle.app.launcher

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.AndroidPairFactsSource
import com.riffle.app.launcher.diagnostics.pair.AndroidPairScriptPlatform
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.ExperimentScriptLauncher
import com.riffle.app.launcher.diagnostics.pair.PairActivityHub
import com.riffle.app.launcher.diagnostics.pair.PairLaunchWatcher
import com.riffle.app.launcher.diagnostics.pair.PairScriptApps
import com.riffle.app.launcher.diagnostics.pair.PairScriptKind
import com.riffle.app.launcher.diagnostics.pair.PairScriptState
import com.riffle.app.launcher.diagnostics.pair.toScriptPlainText
import com.riffle.core.domain.launcher.apps.InstalledApp

// The "Script" section of the "App pair experiment" page (SettingsAppPairPage.kt): one button that runs every
// scenario in sequence (PairScript, PairScriptRunner), its progress line and the oldest-first copy of its log.

internal const val APP_PAIR_SCRIPT_RUN_TAG = "appPair.script.run"
internal const val APP_PAIR_SCRIPT_STOP_TAG = "appPair.script.stop"
internal const val APP_PAIR_SCRIPT_STATUS_TAG = "appPair.script.status"
internal const val APP_PAIR_SCRIPT_CHOSEN_TAG = "appPair.script.chosen"
internal const val APP_PAIR_HELD_RUN_TAG = "appPair.script.heldRun"
internal const val APP_PAIR_HELD_HINT_TAG = "appPair.script.heldHint"
internal const val APP_PAIR_HELD_CHOSEN_TAG = "appPair.script.heldChosen"
internal const val APP_PAIR_PROBE_RUN_TAG = "appPair.script.probeRun"
internal const val APP_PAIR_PROBE_HINT_TAG = "appPair.script.probeHint"
internal const val APP_PAIR_PROBE_CHOSEN_TAG = "appPair.script.probeChosen"

private const val DISABLED_ROW_ALPHA = 0.5f

/** What the host needs from the page besides the environment: the shared log, the watcher and the layout posture. */
internal class PairScriptWiring(
    val experiment: AppPairExperiment,
    val watcher: PairLaunchWatcher,
    val posture: String,
)

/**
 * The script section with its wiring: the runner (the hub's, in the app), the launcher it is started with, and the two
 * buttons. Pressing "Run the whole script" selects the hard-coded pair in the pickers, then starts the run.
 */
@Composable
internal fun PairScriptHost(
    environment: AppPairEnvironment?,
    wiring: PairScriptWiring,
    state: SettingsSurfaceState,
    chosen: Pair<InstalledApp?, InstalledApp?>,
    onPreselect: (InstalledApp?, InstalledApp?) -> Unit,
) {
    val context = LocalContext.current
    val scriptEnvironment = environment?.script
    val runner = scriptEnvironment?.runner ?: PairActivityHub.scriptRunner
    val scriptState by runner.state.collectAsState()
    val packages = scriptEnvironment?.packages ?: (PairScriptApps.FIRST_PACKAGE to PairScriptApps.SECOND_PACKAGE)
    val log by wiring.experiment.log.collectAsState()
    var copied by remember { mutableStateOf<ScriptCopy?>(null) }
    val appContext = context.applicationContext
    val launcher =
        ExperimentScriptLauncher(
            wiring.experiment,
            starter = { environment?.starter ?: AndroidPairStarter(PairActivityHub.startContext(context)) },
            facts = {
                environment?.facts
                    ?: AndroidPairFactsSource(PairActivityHub.startContext(context)) { wiring.posture }
            },
            watcher = wiring.watcher,
            resolver = scriptEnvironment?.resolve ?: { AndroidPairScriptPlatform.resolve(appContext, it) },
            home = scriptEnvironment?.returnHome ?: { AndroidPairScriptPlatform.returnToLauncher(appContext) },
        )
    val (a, b) = chosen
    PairScriptSection(
        values =
            PairScriptSectionValues(
                scriptState,
                canRunPair(a, b),
                log.mapNotNull { it.script?.kind }.toSet(),
                copied,
            ),
        actions =
            PairScriptSectionActions(
                onRun = { kind ->
                    val (one, two) = packages
                    val apps = state.installedApps
                    onPreselect(
                        apps.firstOrNull { it.identity.packageName.value == one },
                        apps.firstOrNull { it.identity.packageName.value == two },
                    )
                    copied = null
                    runner.start(launcher, one, two, kind)
                },
                onStop = runner::stop,
                onRunChosen = { kind ->
                    if (a != null && b != null) {
                        copied = null
                        runner.start(launcher, a.identity.packageName.value, b.identity.packageName.value, kind)
                    }
                },
                onCopy = {
                    copyPairText(context, log.toScriptPlainText(kind = it.kind))
                    copied = it
                },
                onShare = { sharePairText(context, log.toScriptPlainText(kind = it.kind)) },
            ),
    )
}

internal class PairScriptSectionValues(
    val state: PairScriptState,
    val canRunChosen: Boolean,
    /** The kinds of script run the log holds entries of; a copy row is enabled when its kind is among them. */
    val kindsInLog: Set<PairScriptKind>,
    val copied: ScriptCopy?,
)

internal class PairScriptSectionActions(
    val onRun: (PairScriptKind) -> Unit,
    val onStop: () -> Unit,
    val onRunChosen: (PairScriptKind) -> Unit,
    val onCopy: (ScriptCopy) -> Unit,
    val onShare: (ScriptCopy) -> Unit,
)

@Composable
internal fun PairScriptSection(
    values: PairScriptSectionValues,
    actions: PairScriptSectionActions,
) {
    val running = values.state is PairScriptState.Running
    SettingsSection(title = stringResource(R.string.app_pair_section_script)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!running) {
                PairRunButton(APP_PAIR_HELD_RUN_TAG, R.string.app_pair_held_run) { actions.onRun(PairScriptKind.HELD) }
                PairHint(APP_PAIR_HELD_HINT_TAG, R.string.app_pair_held_hint)
                PairRunButton(APP_PAIR_PROBE_RUN_TAG, R.string.app_pair_probe_run) {
                    actions.onRun(PairScriptKind.PROBES)
                }
                PairHint(APP_PAIR_PROBE_HINT_TAG, R.string.app_pair_probe_hint)
            }
            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(if (running) APP_PAIR_SCRIPT_STOP_TAG else APP_PAIR_SCRIPT_RUN_TAG),
                onClick = if (running) actions.onStop else ({ actions.onRun(PairScriptKind.SCENARIOS) }),
            ) {
                Text(stringResource(if (running) R.string.app_pair_script_stop else R.string.app_pair_script_run))
            }
            Text(
                modifier = Modifier.testTag(APP_PAIR_SCRIPT_STATUS_TAG),
                text = scriptStatusText(values.state),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val chosenEnabled = values.canRunChosen && !running
        PairChosenRow(APP_PAIR_SCRIPT_CHOSEN_TAG, chosenEnabled, PairChosenText.NINE) {
            actions.onRunChosen(PairScriptKind.SCENARIOS)
        }
        PairChosenRow(APP_PAIR_HELD_CHOSEN_TAG, chosenEnabled, PairChosenText.HELD) {
            actions.onRunChosen(PairScriptKind.HELD)
        }
        PairChosenRow(APP_PAIR_PROBE_CHOSEN_TAG, chosenEnabled, PairChosenText.PROBES) {
            actions.onRunChosen(PairScriptKind.PROBES)
        }
        PairScriptCopyRows(values, actions)
    }
}

@Composable
private fun PairRunButton(
    tag: String,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Button(modifier = Modifier.fillMaxWidth().testTag(tag), onClick = onClick) { Text(stringResource(label)) }
}

@Composable
private fun PairHint(
    tag: String,
    @StringRes text: Int,
) {
    Text(modifier = Modifier.testTag(tag), text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

private enum class PairChosenText(
    @StringRes val title: Int,
    @StringRes val summary: Int,
) {
    NINE(R.string.app_pair_script_run_chosen, R.string.app_pair_script_run_chosen_summary),
    HELD(R.string.app_pair_held_run_chosen, R.string.app_pair_held_run_chosen_summary),
    PROBES(R.string.app_pair_probe_run_chosen, R.string.app_pair_probe_run_chosen_summary),
}

/** "Run the ... with these apps": enabled once two different apps are chosen and no run is under way. */
@Composable
private fun PairChosenRow(
    tag: String,
    enabled: Boolean,
    text: PairChosenText,
    onClick: () -> Unit,
) {
    SettingsClickableRow(
        modifier = Modifier.testTag(tag).alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
        title = stringResource(text.title),
        subtitle = stringResource(text.summary),
        enabled = enabled,
        onClick = onClick,
        trailingContent = null,
    )
}

/** The script copy rows: the latest run of any kind (once there is one), then one row per kind of run. */
@Composable
private fun PairScriptCopyRows(
    values: PairScriptSectionValues,
    actions: PairScriptSectionActions,
) {
    ScriptCopy.entries.forEach { target ->
        val has = if (target.kind == null) values.kindsInLog.isNotEmpty() else target.kind in values.kindsInLog
        if (target.kind == null && !has) return@forEach
        val title =
            when (target) {
                ScriptCopy.ANY -> R.string.app_pair_script_copy
                ScriptCopy.HELD -> R.string.app_pair_copy_held
                ScriptCopy.PROBES -> R.string.app_pair_copy_probe
                ScriptCopy.NINE -> R.string.app_pair_copy_nine
            }
        val summary =
            when {
                values.copied == target -> R.string.app_pair_log_copied
                target.kind == null -> R.string.app_pair_script_copy_summary
                has -> R.string.app_pair_copy_kind_summary
                else -> R.string.app_pair_copy_kind_none
            }
        PairCopyRow(
            title = stringResource(title),
            subtitle = stringResource(summary),
            tag = target.tag,
            enabled = has,
            onCopy = { actions.onCopy(target) },
            onShare = { actions.onShare(target) },
        )
    }
}

@Composable
private fun scriptStatusText(state: PairScriptState): String =
    when (state) {
        PairScriptState.Idle -> stringResource(R.string.app_pair_script_hint)
        is PairScriptState.Running -> stringResource(R.string.app_pair_script_step, state.step, state.total, state.name)
        is PairScriptState.Finished -> stringResource(R.string.app_pair_script_finished, state.steps)
        is PairScriptState.Stopped -> stringResource(R.string.app_pair_script_stopped, state.step, state.total)
        is PairScriptState.Missing ->
            stringResource(R.string.app_pair_script_missing, state.packages.joinToString(", "))
    }

internal fun copyPairText(
    context: android.content.Context,
    text: String,
) {
    runCatching {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("App pair experiment", text))
    }
}
