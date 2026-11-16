package com.riffle.app.launcher

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.diagnostics.pair.AndroidPairFactsSource
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.CustomPair
import com.riffle.app.launcher.diagnostics.pair.PairActivityHub
import com.riffle.app.launcher.diagnostics.pair.PairFactsSource
import com.riffle.app.launcher.diagnostics.pair.PairLaunchOptions
import com.riffle.app.launcher.diagnostics.pair.PairLaunchWatcher
import com.riffle.app.launcher.diagnostics.pair.PairLogEntry
import com.riffle.app.launcher.diagnostics.pair.PairMethod
import com.riffle.app.launcher.diagnostics.pair.PairScenario
import com.riffle.app.launcher.diagnostics.pair.PairScriptRunner
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.PairTarget
import com.riffle.app.launcher.diagnostics.pair.toPlainText
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.layoutPosture
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val APP_PAIR_FIRST_TAG = "appPair.first"
internal const val APP_PAIR_SECOND_TAG = "appPair.second"
internal const val APP_PAIR_LAUNCH_TAG = "appPair.launch"
internal const val APP_PAIR_COPY_TAG = "appPair.copy"
internal const val APP_PAIR_LOG_TAG = "appPair.log"
internal const val APP_PAIR_PICK_TAG_PREFIX = "appPair.pick."
private const val MAX_PICKER_ROWS = 40
private const val DEFAULT_DELAY_MS = 600

/** What the page launches with and logs into; absent in the app, where the platform implementations are used. */
internal class AppPairEnvironment(
    val experiment: AppPairExperiment,
    val starter: PairStarter,
    val facts: PairFactsSource,
    val watcher: PairLaunchWatcher = PairLaunchWatcher.None,
    /** The scripted run and what it needs from the platform; absent in the app, where the hub's runner is used. */
    val script: AppPairScriptEnvironment? = null,
)

/** The script's runner, the two packages "Run the whole script" preselects, and how a package is resolved. */
internal class AppPairScriptEnvironment(
    val runner: PairScriptRunner,
    val packages: Pair<String, String>,
    val resolve: (String) -> PairTarget?,
    val returnHome: () -> Unit,
)

internal val LocalAppPairEnvironment = compositionLocalOf<AppPairEnvironment?> { null }

private enum class PairSlot { FIRST, SECOND }

/**
 * "App pair experiment": an experimental diagnostics page under About. Two pickers, one button per scenario, the latest
 * result with its verdict buttons, a collapsed Advanced part and an in-memory result log that can be copied. Nothing
 * is stored and nothing outside this page uses it.
 */
@Composable
@Suppress("LongMethod")
internal fun SettingsAppPairPageContent(state: SettingsSurfaceState) {
    val context = LocalContext.current
    val environment = LocalAppPairEnvironment.current
    val experiment = environment?.experiment ?: AppPairExperiment.shared
    val starter = environment?.starter ?: remember(context) { AndroidPairStarter(context) }
    val posture =
        state.selectedLayoutDeviceClass.let { "${it.name} (${it.layoutPosture()}), from the Settings layout tab" }
    val facts =
        environment?.facts ?: remember(context, posture) { AndroidPairFactsSource(context) { posture } }
    val watcher = environment?.watcher ?: PairActivityHub.recorder
    val iconLoader = remember(context) { PackageManagerAppIconLoader(context.packageManager) }
    val scope = rememberCoroutineScope()

    var first by remember { mutableStateOf<InstalledApp?>(null) }
    var second by remember { mutableStateOf<InstalledApp?>(null) }
    var method by remember { mutableStateOf(PairMethod.SEQUENTIAL) }
    var delayMillis by remember { mutableFloatStateOf(DEFAULT_DELAY_MS.toFloat()) }
    var custom by remember { mutableStateOf(CustomPair.DEFAULT) }
    var firstWasOpen by remember { mutableStateOf(false) }
    var secondWasOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val log by experiment.log.collectAsState()
    val delayValue = delayMillis.roundToInt()
    val a = first
    val b = second

    fun launch(
        launchMethod: PairMethod,
        options: PairLaunchOptions,
    ) {
        if (a == null || b == null) return
        copied = false
        scope.launch {
            experiment.launch(
                launchMethod,
                a.toPairTarget(),
                b.toPairTarget(),
                delayValue.toLong(),
                starter,
                facts,
                options,
            )
        }
    }

    PairScriptHost(
        environment = environment,
        wiring = PairScriptWiring(experiment, watcher, posture),
        state = state,
        chosen = first to second,
        onPreselect = { one, two ->
            if (one != null) first = one
            if (two != null) second = two
        },
    )
    PairAppsSection(
        state = state,
        iconLoader = iconLoader,
        chosen = first to second,
        wasOpen = firstWasOpen to secondWasOpen,
        onPicked = { slot, app -> if (slot == PairSlot.FIRST) first = app else second = app },
        onOpenChange = { slot, open -> if (slot == PairSlot.FIRST) firstWasOpen = open else secondWasOpen = open },
    )
    PairScenarioSection(
        enabled = canRunPair(a, b),
        delayMillis = delayValue,
        log = log,
        onRun = { scenario ->
            launch(
                scenario.method,
                PairLaunchOptions(scenario.custom, firstWasOpen, secondWasOpen, watcher, scenario.logName),
            )
        },
    )
    PairLatestResultSection(
        log = log,
        copied = copied,
        experiment = experiment,
        onCopy = {
            copyPairLog(context, log)
            copied = true
        },
    )
    PairAdvancedSection(
        expanded = advancedOpen,
        onToggle = { advancedOpen = !advancedOpen },
        launchSubtitle = pairLaunchSubtitle(a, b),
        values =
            PairAdvancedValues(
                method,
                custom,
                delayMillis,
                log.firstNotNullOfOrNull { PairScenario.fromLogName(it.scenario) },
            ),
        actions =
            PairAdvancedActions(
                onMethod = { method = it },
                onCustom = { custom = it },
                onDelay = { delayMillis = it },
                onEditAsCustom = {
                    custom = it.custom
                    method = PairMethod.CUSTOM
                },
                onLaunch = { launch(method, PairLaunchOptions(custom, firstWasOpen, secondWasOpen, watcher)) },
            ),
    )
    PairEarlierResultsSection(log.drop(1), experiment)
}

/** The two pickers, the picker list while one is open, and the "was already open" ticks under them. */
@Composable
private fun PairAppsSection(
    state: SettingsSurfaceState,
    iconLoader: AppIconLoader,
    chosen: Pair<InstalledApp?, InstalledApp?>,
    wasOpen: Pair<Boolean, Boolean>,
    onPicked: (PairSlot, InstalledApp) -> Unit,
    onOpenChange: (PairSlot, Boolean) -> Unit,
) {
    var picking by remember { mutableStateOf<PairSlot?>(null) }
    SettingsSection(title = stringResource(R.string.app_pair_section_apps)) {
        PairSlotRow(
            title = stringResource(R.string.app_pair_first),
            app = chosen.first,
            iconLoader = iconLoader,
            tag = APP_PAIR_FIRST_TAG,
            onClick = { picking = if (picking == PairSlot.FIRST) null else PairSlot.FIRST },
        )
        PairSlotRow(
            title = stringResource(R.string.app_pair_second),
            app = chosen.second,
            iconLoader = iconLoader,
            tag = APP_PAIR_SECOND_TAG,
            onClick = { picking = if (picking == PairSlot.SECOND) null else PairSlot.SECOND },
        )
        SettingsSwitchRow(
            modifier = Modifier.testTag(APP_PAIR_FIRST_OPEN_TAG),
            title = stringResource(R.string.app_pair_first_open),
            subtitle = stringResource(R.string.app_pair_already_open_summary),
            checked = wasOpen.first,
            onCheckedChange = { onOpenChange(PairSlot.FIRST, it) },
        )
        SettingsSwitchRow(
            modifier = Modifier.testTag(APP_PAIR_SECOND_OPEN_TAG),
            title = stringResource(R.string.app_pair_second_open),
            subtitle = stringResource(R.string.app_pair_already_open_summary),
            checked = wasOpen.second,
            onCheckedChange = { onOpenChange(PairSlot.SECOND, it) },
        )
    }
    picking?.let { slot ->
        PairPicker(
            apps = state.installedApps,
            iconLoader = iconLoader,
            onPicked = { app ->
                onPicked(slot, app)
                picking = null
            },
        )
    }
}

/** Scenarios need two different apps; the same package twice would only reopen one app. */
internal fun canRunPair(
    first: InstalledApp?,
    second: InstalledApp?,
): Boolean = first != null && second != null && first.identity.packageName != second.identity.packageName

@Composable
private fun pairLaunchSubtitle(
    first: InstalledApp?,
    second: InstalledApp?,
): String =
    if (first != null && second != null) {
        stringResource(R.string.app_pair_launch_subtitle, first.label, second.label)
    } else {
        stringResource(R.string.app_pair_launch_needs_apps)
    }

@Composable
private fun PairSlotRow(
    title: String,
    app: InstalledApp?,
    iconLoader: AppIconLoader,
    tag: String,
    onClick: () -> Unit,
) {
    SettingsClickableRow(
        modifier = Modifier.testTag(tag),
        title = title,
        subtitle =
            app?.let { it.label + "\n" + it.identity.packageName.value }
                ?: stringResource(R.string.app_pair_choose),
        onClick = onClick,
        trailingContent =
            app?.let { chosen ->
                { LauncherAppIcon(chosen.identity, chosen.label, iconLoader, Modifier.size(40.dp)) }
            },
    )
}

@Composable
private fun PairPicker(
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onPicked: (InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) { pairCandidates(apps, query) }
    AppSearchField(
        modifier = Modifier.fillMaxWidth(),
        query = query,
        onQueryChanged = { query = it },
        label = stringResource(R.string.app_pair_search_label),
    )
    SettingsSection(title = stringResource(R.string.app_pair_choose)) {
        if (shown.isEmpty()) SettingsListRow(title = stringResource(R.string.app_publish_no_apps))
        shown.take(MAX_PICKER_ROWS).forEach { app ->
            SettingsClickableRow(
                modifier = Modifier.testTag(APP_PAIR_PICK_TAG_PREFIX + app.identity.packageName.value),
                title = app.label,
                subtitle = app.identity.packageName.value,
                onClick = { onPicked(app) },
                trailingContent = { LauncherAppIcon(app.identity, app.label, iconLoader, Modifier.size(40.dp)) },
            )
        }
    }
    if (shown.size > MAX_PICKER_ROWS) {
        SettingsSecondaryText(text = stringResource(R.string.app_publish_more_apps, MAX_PICKER_ROWS, shown.size))
    }
}

/** One log entry, read as a single unit by a screen reader. */
@Composable
internal fun PairLogItem(entry: PairLogEntry) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(APP_PAIR_LOG_TAG)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = entry.toPlainText(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Personal-profile apps only: the launcher gateway cannot add intent flags, so other profiles are left out. */
internal fun pairCandidates(
    apps: List<InstalledApp>,
    query: String,
): List<InstalledApp> {
    val needle = query.trim()
    return apps
        .filter {
            it.enabled && it.visibility == AppVisibility.VISIBLE && it.identity.profile.type == AppProfileType.PERSONAL
        }
        .distinctBy { it.identity.packageName.value to it.identity.activityName.value }
        .filter {
            needle.isEmpty() ||
                it.label.contains(needle, ignoreCase = true) ||
                it.identity.packageName.value.contains(needle, ignoreCase = true)
        }
        .sortedBy { it.label.lowercase() }
}

private fun InstalledApp.toPairTarget() = PairTarget(identity.packageName.value, identity.activityName.value, label)

private fun copyPairLog(
    context: Context,
    log: List<PairLogEntry>,
) = copyPairText(context, log.toPlainText())
