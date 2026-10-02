package com.riffle.app.launcher

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.ics.IcsAddResult
import com.riffle.app.launcher.ics.IcsFeedRow
import com.riffle.app.launcher.ics.IcsFeedsText
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId

internal const val ICS_FEEDS_EMPTY_TEST_TAG = "ics-feeds-empty"
internal const val ICS_FEEDS_REFRESH_TEST_TAG = "ics-feeds-refresh"
internal const val ICS_FEEDS_SUMMARY_TEST_TAG = "ics-feeds-summary"

internal fun icsFeedRowTestTag(id: IcsFeedId): String = "ics-feed-row-${id.value}"

/**
 * Settings > Calendar feeds (ICS). A developer page like Sources: only reachable while Workspaces (preview) is
 * on, because the feeds only feed that preview. While open it follows the feed list and refresh status.
 */
@Composable
internal fun SettingsIcsFeedsPageContent() {
    val controller = LocalWorkspaceSettingsHost.current?.icsFeeds
    if (controller == null) {
        SettingsPreviewOffNote()
    } else {
        DisposableEffect(controller) {
            controller.open()
            onDispose { controller.close() }
        }
        val state by controller.state.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
            IcsFeedsListContent(
                state = state,
                onRefresh = { controller.refreshAll() },
                onToggle = controller::setEnabled,
                onRemove = controller::remove,
            )
            SettingsSection(title = "Add a feed") {
                IcsAddFeedForm(onAdd = controller::add)
            }
        }
    }
}

/** The intro, refresh row and feed rows over a fixed state; no controller and no text fields. */
@Composable
internal fun IcsFeedsListContent(
    state: IcsFeedsUiState,
    onRefresh: () -> Unit,
    onToggle: (IcsFeedId, Boolean) -> Unit,
    onRemove: (IcsFeedId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            text = IcsFeedsText.INTRO,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSection(title = IcsFeedsText.TITLE) {
            IcsRefreshRow(state = state, onRefresh = onRefresh)
            if (state.rows.isEmpty()) {
                SettingsListRow(
                    modifier = Modifier.testTag(ICS_FEEDS_EMPTY_TEST_TAG),
                    title = if (state.loaded) "No calendar feeds" else "Loading",
                    subtitle = if (state.loaded) "Add a link below to see its events in Workspaces." else null,
                )
            }
            state.rows.forEach { row -> IcsFeedRowItem(row = row, onToggle = onToggle, onRemove = onRemove) }
        }
    }
}

@Composable
internal fun IcsRefreshRow(
    state: IcsFeedsUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = IcsFeedsText.summary(state.lastReport)
    SettingsListRow(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(ICS_FEEDS_SUMMARY_TEST_TAG),
        title = "Refresh calendar feeds",
        subtitle =
            when {
                state.refreshing -> "Refreshing…"
                else -> summary ?: "Fetch the latest events now. Feeds are only fetched when you ask."
            },
        trailingContent = {
            TextButton(
                modifier = Modifier.testTag(ICS_FEEDS_REFRESH_TEST_TAG),
                enabled = !state.refreshing && state.rows.any(IcsFeedRow::enabled),
                onClick = onRefresh,
            ) {
                SettingsButtonText(text = "Refresh")
            }
        },
    )
}

@Composable
private fun IcsFeedRowItem(
    row: IcsFeedRow,
    onToggle: (IcsFeedId, Boolean) -> Unit,
    onRemove: (IcsFeedId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(icsFeedRowTestTag(row.id)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            title = row.name,
            subtitle = "${row.host}. ${icsFeedStatus(row)}",
        )
        Switch(checked = row.enabled, onCheckedChange = { enabled -> onToggle(row.id, enabled) })
        TextButton(onClick = { onRemove(row.id) }) { SettingsButtonText(text = "Remove") }
    }
}

private fun icsFeedStatus(row: IcsFeedRow): String {
    val failure = row.lastFailure
    val updated = row.lastUpdatedAtEpochMillis
    return when {
        !row.enabled -> "Off"
        row.refreshing -> "Refreshing…"
        failure != null -> "Last refresh failed: ${IcsFeedsText.failureLabel(failure)}"
        updated != null -> "Updated ${DateUtils.getRelativeTimeSpanString(updated)}"
        else -> "Not refreshed yet"
    }
}

/** Name and link fields. The link is never shown back after it is added: only its host is. */
@Composable
private fun IcsAddFeedForm(onAdd: (String, String) -> IcsAddResult) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = {
                url = it
                error = null
            },
            label = { Text("Calendar link (https .ics)") },
            isError = error != null,
            supportingText = { error?.let { message -> Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    val result = onAdd(name, url)
                    error = IcsFeedsText.addResultLabel(result)
                    if (result == IcsAddResult.ADDED) {
                        name = ""
                        url = ""
                    }
                },
            ) {
                SettingsButtonText(text = "Add feed")
            }
        }
    }
}
