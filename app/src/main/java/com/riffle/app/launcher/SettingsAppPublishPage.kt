package com.riffle.app.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.AndroidAppPublishSource
import com.riffle.app.launcher.diagnostics.AppPublishReport
import com.riffle.app.launcher.diagnostics.PublishSection
import com.riffle.app.launcher.diagnostics.SectionProblem
import com.riffle.app.launcher.diagnostics.detailLines
import com.riffle.app.launcher.diagnostics.headline
import com.riffle.app.launcher.diagnostics.orderPublishApps
import com.riffle.app.launcher.diagnostics.toPlainText
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_APPS_LISTED = 60

/**
 * "What apps publish": a read-only diagnostics page under About. Lists the installed apps (those with a notification
 * or media session now first); an app opens to what it exposes to a launcher, as shapes only (see
 * `diagnostics/AppPublishModels.kt`). Nothing on this page changes any setting.
 */
@Composable
internal fun SettingsAppPublishPageContent(state: SettingsSurfaceState) {
    val context = LocalContext.current
    val source = remember(context) { AndroidAppPublishSource(context.applicationContext) }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    val app = selected
    if (app == null) {
        AppPublishAppList(apps = state.installedApps, source = source, onSelected = { selected = it })
    } else {
        BackHandler { selected = null }
        AppPublishDetail(app = app, source = source, onBack = { selected = null })
    }
}

@Composable
private fun AppPublishAppList(
    apps: List<InstalledApp>,
    source: AndroidAppPublishSource,
    onSelected: (InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var livePackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(source) { livePackages = withContext(Dispatchers.IO) { source.packagesWithLiveContent() } }
    val ordered = remember(apps, livePackages, query) { orderPublishApps(apps, livePackages, query) }

    AppSearchField(
        modifier = Modifier.fillMaxWidth(),
        query = query,
        onQueryChanged = { query = it },
        label = stringResource(R.string.app_publish_search_label),
    )
    SettingsSection(title = stringResource(R.string.settings_page_app_publish)) {
        if (ordered.isEmpty()) {
            SettingsListRow(title = stringResource(R.string.app_publish_no_apps))
        }
        ordered.take(MAX_APPS_LISTED).forEach { app ->
            val live = app.identity.packageName.value in livePackages
            SettingsClickableRow(
                title = app.label,
                subtitle =
                    if (live) {
                        app.identity.packageName.value + "\n" + stringResource(R.string.app_publish_live_app)
                    } else {
                        app.identity.packageName.value
                    },
                onClick = { onSelected(app) },
                trailingContent = null,
            )
        }
    }
    if (ordered.size > MAX_APPS_LISTED) {
        SettingsSecondaryText(
            text = stringResource(R.string.app_publish_more_apps, MAX_APPS_LISTED, ordered.size),
        )
    }
}

@Composable
private fun AppPublishDetail(
    app: InstalledApp,
    source: AndroidAppPublishSource,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var reloads by remember { mutableIntStateOf(0) }
    var report by remember(app) { mutableStateOf<AppPublishReport?>(null) }
    var copied by remember(app) { mutableStateOf(false) }
    LaunchedEffect(app, reloads) { report = withContext(Dispatchers.IO) { source.load(app) } }
    val current = report

    SettingsSection(title = app.label) {
        SettingsClickableRow(
            title = stringResource(R.string.app_publish_back),
            onClick = onBack,
            trailingContent = null,
        )
        SettingsClickableRow(
            title = stringResource(R.string.app_publish_copy),
            subtitle =
                if (copied) {
                    stringResource(R.string.app_publish_copied)
                } else {
                    stringResource(R.string.app_publish_copy_subtitle)
                },
            onClick = {
                current?.let {
                    copyReport(context, it)
                    copied = true
                }
            },
            trailingContent = null,
        )
        SettingsClickableRow(
            title = stringResource(R.string.app_publish_refresh),
            subtitle = stringResource(R.string.app_publish_refresh_subtitle),
            onClick = {
                copied = false
                reloads++
            },
            trailingContent = null,
        )
    }
    if (current == null) {
        SettingsSecondaryText(text = stringResource(R.string.app_publish_loading, app.label))
        return
    }
    PublishSectionBlock(stringResource(R.string.app_publish_section_shortcuts), current.shortcuts) { _, item ->
        PublishItem(item.headline(), item.detailLines())
    }
    PublishSectionBlock(stringResource(R.string.app_publish_section_notifications), current.notifications) { _, item ->
        PublishItem(item.headline(), item.detailLines())
    }
    PublishSectionBlock(stringResource(R.string.app_publish_section_media), current.media) { index, item ->
        PublishItem(stringResource(R.string.app_publish_media_session, index + 1), item.detailLines())
    }
    PublishSectionBlock(stringResource(R.string.app_publish_section_widgets), current.widgets) { _, item ->
        PublishItem(item.headline(), item.detailLines())
    }
}

@Composable
private fun <T> PublishSectionBlock(
    title: String,
    section: PublishSection<T>,
    item: @Composable (Int, T) -> Unit,
) {
    SettingsSection(title = title) {
        val problem = section.problem
        when {
            problem != null -> SettingsListRow(title = stringResource(problem.messageRes()))
            section.items.isEmpty() -> SettingsListRow(title = stringResource(R.string.app_publish_empty))
            else -> section.items.forEachIndexed { index, value -> item(index, value) }
        }
    }
}

/** One published item: a heading and its technical detail lines, read as a single unit by a screen reader. */
@Composable
private fun PublishItem(
    headline: String,
    lines: List<String>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = headline, style = MaterialTheme.typography.bodyLarge)
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SectionProblem.messageRes(): Int =
    when (this) {
        SectionProblem.LAUNCHER_ROLE_MISSING -> R.string.app_publish_problem_role
        SectionProblem.NOTIFICATION_ACCESS_MISSING -> R.string.app_publish_problem_notification_access
        SectionProblem.UNAVAILABLE -> R.string.app_publish_problem_unavailable
    }

private fun copyReport(
    context: Context,
    report: AppPublishReport,
) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(report.appLabel, report.toPlainText()))
    }
}
