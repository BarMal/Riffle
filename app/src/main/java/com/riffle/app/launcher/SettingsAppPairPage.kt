package com.riffle.app.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
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
internal const val APP_PAIR_METHOD_TAG_PREFIX = "appPair.method."
private const val MAX_PICKER_ROWS = 40
private const val MAX_DELAY_MS = 2000
private const val DELAY_STEP_MS = 100
private const val DEFAULT_DELAY_MS = 600

/** What the page launches with and logs into; absent in the app, where the platform implementations are used. */
internal class AppPairEnvironment(
    val experiment: AppPairExperiment,
    val starter: PairStarter,
    val facts: PairFactsSource,
    val watcher: PairLaunchWatcher = PairLaunchWatcher.None,
)

internal val LocalAppPairEnvironment = compositionLocalOf<AppPairEnvironment?> { null }

private enum class PairSlot { FIRST, SECOND }

/**
 * "App pair experiment": an experimental diagnostics page under About. Two pickers, a method, Launch and an in-memory
 * result log that can be copied. Nothing is stored and nothing outside this page uses it.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
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
    var picking by remember { mutableStateOf<PairSlot?>(null) }
    var method by remember { mutableStateOf(PairMethod.SEQUENTIAL) }
    var delayMillis by remember { mutableFloatStateOf(DEFAULT_DELAY_MS.toFloat()) }
    var custom by remember { mutableStateOf(CustomPair.DEFAULT) }
    var firstWasOpen by remember { mutableStateOf(false) }
    var secondWasOpen by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val log by experiment.log.collectAsState()
    val delayValue = delayMillis.roundToInt()

    SettingsSection(title = stringResource(R.string.settings_page_app_pair)) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.app_pair_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_apps)) {
        PairSlotRow(
            title = stringResource(R.string.app_pair_first),
            app = first,
            iconLoader = iconLoader,
            tag = APP_PAIR_FIRST_TAG,
            onClick = { picking = if (picking == PairSlot.FIRST) null else PairSlot.FIRST },
        )
        PairSlotRow(
            title = stringResource(R.string.app_pair_second),
            app = second,
            iconLoader = iconLoader,
            tag = APP_PAIR_SECOND_TAG,
            onClick = { picking = if (picking == PairSlot.SECOND) null else PairSlot.SECOND },
        )
    }
    picking?.let { slot ->
        PairPicker(
            apps = state.installedApps,
            iconLoader = iconLoader,
            onPicked = { app ->
                if (slot == PairSlot.FIRST) first = app else second = app
                picking = null
            },
        )
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_method)) {
        PairMethod.entries.forEach { option ->
            SettingsClickableRow(
                modifier = Modifier.testTag(APP_PAIR_METHOD_TAG_PREFIX + option.name),
                title = option.title,
                subtitle = option.summary,
                onClick = { method = option },
                trailingContent =
                    if (option == method) {
                        { SettingsButtonText(text = stringResource(R.string.app_pair_method_selected)) }
                    } else {
                        null
                    },
            )
        }
    }
    if (method == PairMethod.CUSTOM) {
        PairCustomSections(custom = custom, delayMillis = delayValue, onChange = { custom = it })
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_already_open)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.app_pair_delay, delayValue),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Delay before the second app" },
                value = delayMillis,
                onValueChange = { delayMillis = it },
                valueRange = 0f..MAX_DELAY_MS.toFloat(),
                steps = MAX_DELAY_MS / DELAY_STEP_MS - 1,
            )
            SettingsSecondaryText(text = stringResource(R.string.app_pair_delay_note))
        }
        SettingsSwitchRow(
            modifier = Modifier.testTag(APP_PAIR_FIRST_OPEN_TAG),
            title = stringResource(R.string.app_pair_first_open),
            subtitle = stringResource(R.string.app_pair_already_open_summary),
            checked = firstWasOpen,
            onCheckedChange = { firstWasOpen = it },
        )
        SettingsSwitchRow(
            modifier = Modifier.testTag(APP_PAIR_SECOND_OPEN_TAG),
            title = stringResource(R.string.app_pair_second_open),
            subtitle = stringResource(R.string.app_pair_already_open_summary),
            checked = secondWasOpen,
            onCheckedChange = { secondWasOpen = it },
        )
        val a = first
        val b = second
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_LAUNCH_TAG),
            title = stringResource(R.string.app_pair_launch),
            subtitle =
                if (a != null && b != null) {
                    stringResource(R.string.app_pair_launch_subtitle, a.label, b.label)
                } else {
                    stringResource(R.string.app_pair_launch_needs_apps)
                },
            onClick = {
                if (a != null && b != null) {
                    copied = false
                    scope.launch {
                        experiment.launch(
                            method,
                            a.toPairTarget(),
                            b.toPairTarget(),
                            delayValue.toLong(),
                            starter,
                            facts,
                            options = PairLaunchOptions(custom, firstWasOpen, secondWasOpen, watcher),
                        )
                    }
                }
            },
            trailingContent = null,
        )
    }
    SettingsSection(title = stringResource(R.string.app_pair_section_log)) {
        SettingsClickableRow(
            modifier = Modifier.testTag(APP_PAIR_COPY_TAG),
            title = stringResource(R.string.app_pair_copy_log),
            subtitle =
                if (copied) {
                    stringResource(R.string.app_pair_log_copied)
                } else {
                    stringResource(R.string.app_pair_copy_log_subtitle)
                },
            onClick = {
                copyPairLog(context, log)
                copied = true
            },
            trailingContent = null,
        )
        if (log.isEmpty()) SettingsListRow(title = stringResource(R.string.app_pair_log_empty))
        log.forEachIndexed { index, entry ->
            PairLogItem(entry)
            PairVerdictControls(
                entry = entry,
                prominent = index == 0,
                onVerdict = { experiment.setVerdict(entry.id, it) },
                onDuplicates = { experiment.setDuplicates(entry.id, it) },
            )
        }
    }
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
private fun PairLogItem(entry: PairLogEntry) {
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
) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("App pair experiment", log.toPlainText()))
    }
}
