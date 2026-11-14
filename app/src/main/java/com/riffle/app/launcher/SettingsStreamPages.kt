@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.titleRes
import com.riffle.app.launcher.stream.todoist.TasksSettingsController
import com.riffle.app.launcher.stream.todoist.TasksSettingsState
import com.riffle.core.domain.launcher.split.supportsSplit
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.StreamWideView
import com.riffle.core.domain.launcher.stream.TaskConnectionState
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * The Stream's layouts, which Settings edits through the very same repository the Stream's own menus write to (one
 * stored value, two ways to change it). Null where the shell has no Stream (tests, previews without one).
 */
internal val LocalStreamLayoutRepository = staticCompositionLocalOf<CachedStreamLayoutRepository?> { null }

internal fun streamSectionTestTag(section: StreamSection): String = "settings-stream-section-${section.storedId}"

internal fun streamShowAsTestTag(
    section: StreamSection,
    view: StreamView,
): String = "settings-stream-show-as-${section.storedId}-${view.storedId}"

internal fun streamWideViewTestTag(view: StreamWideView): String = "settings-stream-wide-${view.storedId}"

internal fun streamMoveTestTag(
    section: StreamSection,
    delta: Int,
): String = "settings-stream-move-${section.storedId}-${if (delta < 0) "up" else "down"}"

internal fun StreamSection.settingKey(): SettingKey =
    when (this) {
        StreamSection.NOW -> SettingKey.STREAM_SECTION_NOW
        StreamSection.NOTIFICATIONS -> SettingKey.STREAM_SECTION_NOTIFICATIONS
        StreamSection.RECENTS -> SettingKey.STREAM_SECTION_RECENTS
        StreamSection.TODAY -> SettingKey.STREAM_SECTION_TODAY
        StreamSection.FEEDS -> SettingKey.STREAM_SECTION_FEEDS
        // Derived from the Dock's pinned apps: it is never listed in Settings, so it has no row to anchor.
        StreamSection.SHORTCUTS -> SettingKey.STREAM_SECTION_NOW
    }

/** The sub-pages that hold a section's own settings: Today has two (its calendar and its tasks). */
internal fun StreamSection.settingsPages(): List<SettingsPage> =
    when (this) {
        StreamSection.NOTIFICATIONS -> listOf(SettingsPage.STREAM_NOTIFICATIONS)
        StreamSection.TODAY -> listOf(SettingsPage.STREAM_AGENDA, SettingsPage.STREAM_TASKS)
        StreamSection.FEEDS -> listOf(SettingsPage.STREAM_FEEDS)
        StreamSection.NOW, StreamSection.RECENTS, StreamSection.SHORTCUTS -> emptyList()
    }

/** Stream: the six sections, each on or off, in order, shown as a Stack or a List, then a sub-page where it has one. */
@Composable
internal fun SettingsStreamPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val repository = LocalStreamLayoutRepository.current
    SettingsPostureTabs(state = state, onAction = onAction)
    if (repository == null) {
        SettingsPreviewOffNote()
        return
    }
    LaunchedEffect(repository) { repository.initialize() }
    val layouts by repository.layouts.collectAsState()
    val deviceClass = state.selectedLayoutDeviceClass
    SettingsStreamSections(
        layout = layouts.forPosture(deviceClass),
        onLayoutAction = { action -> repository.update(deviceClass, action) },
        onOpenPage = onPageSelected,
        showWideView = deviceClass.supportsSplit(),
    )
}

/** The Stream page over a plain [layout], so screenshot tests draw it without a repository. */
@Composable
internal fun SettingsStreamSections(
    layout: StreamLayout,
    onLayoutAction: (StreamLayoutAction) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    /** An expanded posture: the whole Stream can show as Pages or as the Stage view. */
    showWideView: Boolean = false,
) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(R.string.settings_stream_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showWideView) SettingsStreamWideView(layout, onLayoutAction)
    layout.sections.forEachIndexed { index, entry ->
        val section = entry.section
        val name = stringResource(section.titleRes())
        SettingsSection(title = name) {
            SettingsSwitchRow(
                modifier = Modifier.settingAnchor(section.settingKey()).testTag(streamSectionTestTag(section)),
                title = stringResource(R.string.stream_show_hidden_section, name),
                subtitle =
                    if (entry.enabled) {
                        stringResource(R.string.settings_stream_section_on)
                    } else {
                        stringResource(R.string.settings_stream_section_off)
                    },
                checked = entry.enabled,
                onCheckedChange = { enabled -> onLayoutAction(StreamLayoutAction.SetEnabled(section, enabled)) },
            )
            SettingsListRow(
                title = stringResource(R.string.settings_stream_order),
                subtitle = stringResource(R.string.settings_stream_position, index + 1, layout.sections.size),
                trailingContent = {
                    Row {
                        MoveButton(
                            section = section,
                            name = name,
                            delta = -1,
                            enabled = index > 0,
                            onClick = { onLayoutAction(StreamLayoutAction.Move(section, -1)) },
                        )
                        MoveButton(
                            section = section,
                            name = name,
                            delta = 1,
                            enabled = index < layout.sections.lastIndex,
                            onClick = { onLayoutAction(StreamLayoutAction.Move(section, 1)) },
                        )
                    }
                },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.stream_show_as),
                subtitle = stringResource(R.string.settings_stream_show_as_subtitle),
                options = StreamView.entries,
                selected = entry.view,
                onSelect = { view -> onLayoutAction(StreamLayoutAction.SetView(section, view)) },
                label = { view -> stringResource(view.titleRes()) },
                optionTag = { view -> streamShowAsTestTag(section, view) },
                optionDescription = { view ->
                    stringResource(R.string.settings_stream_show_as_option, name, stringResource(view.titleRes()))
                },
            )
            section.settingsPages().forEach { page ->
                SettingsClickableRow(
                    title = stringResource(page.titleRes),
                    subtitle = stringResource(page.summaryRes),
                    onClick = { onOpenPage(page) },
                )
            }
        }
    }
}

/** Show the Stream as Pages or Stage: the whole Stream's view on an expanded posture. */
@Composable
private fun SettingsStreamWideView(
    layout: StreamLayout,
    onLayoutAction: (StreamLayoutAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.stream_title)) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_stream_wide_title),
            subtitle = stringResource(R.string.settings_stream_wide_subtitle),
            options = StreamWideView.entries,
            selected = layout.wideView,
            onSelect = { view -> onLayoutAction(StreamLayoutAction.SetWideView(view)) },
            label = { view -> stringResource(view.titleRes()) },
            optionTag = { view -> streamWideViewTestTag(view) },
            optionDescription = { view ->
                stringResource(R.string.settings_stream_wide_option, stringResource(view.titleRes()))
            },
        )
    }
}

@Composable
private fun MoveButton(
    section: StreamSection,
    name: String,
    delta: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description =
        stringResource(
            if (delta < 0) R.string.settings_stream_move_up_named else R.string.settings_stream_move_down_named,
            name,
        )
    TextButton(
        modifier = Modifier.testTag(streamMoveTestTag(section, delta)).semantics { contentDescription = description },
        enabled = enabled,
        onClick = onClick,
    ) {
        SettingsButtonText(text = stringResource(if (delta < 0) R.string.stream_move_up else R.string.stream_move_down))
    }
}

/** Stream > Notifications: the source behind the section, and the rules that hide notifications from it. */
@Composable
internal fun SettingsStreamNotificationsPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
) {
    StreamSourceSections(
        ids = listOf(SourceIds.NOTIFICATIONS),
        anchor = SettingKey.NOTIFICATIONS_SOURCE,
        calendarAccess = state.calendarAccessStatus,
    )
    SettingsSection(title = stringResource(R.string.settings_page_hidden_items)) {
        SettingsClickableRow(
            title = ExclusionsSettingsText.TITLE,
            subtitle = ExclusionsSettingsText.ROW_SUBTITLE,
            onClick = { onPageSelected(SettingsPage.HIDDEN_ITEMS) },
        )
    }
}

/** Stream > Agenda: the device calendar, whether calendar feeds join it, and the feeds themselves. */
@Composable
internal fun SettingsStreamAgendaPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
) {
    StreamSourceSections(
        ids = listOf(SourceIds.CALENDAR),
        anchor = SettingKey.AGENDA_CALENDAR,
        calendarAccess = state.calendarAccessStatus,
    )
    SettingsSection(title = stringResource(R.string.settings_page_calendar_feeds)) {
        AgendaIncludeFeedsSetting(state = state)
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_calendar_feeds),
            subtitle = stringResource(R.string.settings_page_calendar_feeds_summary),
            onClick = { onPageSelected(SettingsPage.CALENDAR_FEEDS) },
        )
    }
}

/** Whether the Agenda also lists events from calendar feeds: a Stream option, stored with the layout. */
@Composable
private fun AgendaIncludeFeedsSetting(state: SettingsSurfaceState) {
    val repository = LocalStreamLayoutRepository.current
    val layouts = repository?.layouts?.collectAsState()?.value
    val deviceClass = state.selectedLayoutDeviceClass
    val options = layouts?.forPosture(deviceClass)?.options
    SettingsSwitchRow(
        modifier = Modifier.settingAnchor(SettingKey.AGENDA_INCLUDE_FEEDS),
        title = SettingKey.AGENDA_INCLUDE_FEEDS.label(),
        subtitle = stringResource(R.string.settings_agenda_include_feeds_subtitle),
        enabled = repository != null,
        checked = options?.agenda?.includeCalendarFeeds ?: true,
        onCheckedChange = { include ->
            if (repository != null && options != null) {
                repository.update(
                    deviceClass,
                    StreamLayoutAction.SetOptions(
                        options.copy(agenda = options.agenda.copy(includeCalendarFeeds = include)),
                    ),
                )
            }
        },
    )
}

/** The Tasks settings (the Todoist token), provided by the shell; null where there is none (previews). */
internal val LocalTasksSettings = staticCompositionLocalOf<TasksSettingsController?> { null }

internal const val TASKS_TOKEN_FIELD_TEST_TAG = "settings-tasks-token"
internal const val TASKS_SAVE_TEST_TAG = "settings-tasks-save"
internal const val TASKS_REMOVE_TEST_TAG = "settings-tasks-remove"
internal const val TASKS_STATUS_TEST_TAG = "settings-tasks-status"

/** Stream > Tasks: connect Todoist with a personal API token, or remove it. */
@Composable
internal fun SettingsStreamTasksPageContent() {
    val controller = LocalTasksSettings.current
    if (controller == null) {
        SettingsPreviewOffNote()
    } else {
        val state by controller.state.collectAsState()
        SettingsTasksContent(state = state, onSave = controller::save, onRemove = controller::remove)
    }
}

/**
 * The token form over a fixed [state]. The token field is cleared on Save and never filled from storage: once saved
 * the page only says Connected. It is kept out of saved instance state on purpose.
 */
@Composable
internal fun SettingsTasksContent(
    state: TasksSettingsState,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    val connected = state.connection == TaskConnectionState.CONNECTED
    val rejected = state.connection == TaskConnectionState.TOKEN_REJECTED
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l)) {
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            text = stringResource(R.string.settings_tasks_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSection(title = stringResource(R.string.settings_page_stream_tasks)) {
            SettingsListRow(
                modifier = Modifier.settingAnchor(SettingKey.TASKS_PROVIDER).testTag(TASKS_STATUS_TEST_TAG),
                title = stringResource(R.string.settings_tasks_status_title),
                subtitle =
                    stringResource(
                        when (state.connection) {
                            TaskConnectionState.CONNECTED -> R.string.settings_tasks_status_connected
                            TaskConnectionState.TOKEN_REJECTED -> R.string.settings_tasks_status_rejected
                            TaskConnectionState.NOT_CONNECTED -> R.string.settings_tasks_status_not_connected
                        },
                    ),
            )
            if (!connected) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RiffleSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
                ) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth().testTag(TASKS_TOKEN_FIELD_TEST_TAG),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_tasks_token_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                        TextButton(
                            modifier = Modifier.testTag(TASKS_SAVE_TEST_TAG),
                            enabled = token.isNotBlank(),
                            onClick = {
                                onSave(token)
                                token = ""
                            },
                        ) { SettingsButtonText(stringResource(R.string.settings_tasks_save)) }
                        TextButton(
                            onClick = { uriHandler.openUri(TODOIST_DEVELOPER_SETTINGS_URL) },
                        ) { SettingsButtonText(stringResource(R.string.settings_tasks_find_token)) }
                    }
                }
            }
            if (connected || rejected) {
                SettingsListRow(
                    title = stringResource(R.string.settings_tasks_remove_title),
                    subtitle = stringResource(R.string.settings_tasks_remove_summary),
                    trailingContent = {
                        TextButton(modifier = Modifier.testTag(TASKS_REMOVE_TEST_TAG), onClick = onRemove) {
                            SettingsButtonText(stringResource(R.string.settings_tasks_remove))
                        }
                    },
                )
            }
        }
        Text(
            modifier = Modifier.padding(horizontal = RiffleSpacing.s),
            text = stringResource(R.string.settings_tasks_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val TODOIST_DEVELOPER_SETTINGS_URL = "https://app.todoist.com/app/settings/integrations/developer"

/** Stream > Feeds: the source, the feed list, adding a feed and the opt-in background refresh. */
@Composable
internal fun SettingsStreamFeedsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    StreamSourceSections(
        ids = listOf(SourceIds.RSS),
        anchor = SettingKey.FEEDS_SOURCE,
        calendarAccess = state.calendarAccessStatus,
    )
    SettingsRssPageContent(state = state, onAction = onAction)
}
