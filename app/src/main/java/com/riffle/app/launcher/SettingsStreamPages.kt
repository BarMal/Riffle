@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.titleRes
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
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

internal fun streamMoveTestTag(
    section: StreamSection,
    delta: Int,
): String = "settings-stream-move-${section.storedId}-${if (delta < 0) "up" else "down"}"

internal fun StreamSection.settingKey(): SettingKey =
    when (this) {
        StreamSection.NOW -> SettingKey.STREAM_SECTION_NOW
        StreamSection.NOTIFICATIONS -> SettingKey.STREAM_SECTION_NOTIFICATIONS
        StreamSection.RECENTS -> SettingKey.STREAM_SECTION_RECENTS
        StreamSection.AGENDA -> SettingKey.STREAM_SECTION_AGENDA
        StreamSection.TASKS -> SettingKey.STREAM_SECTION_TASKS
        StreamSection.FEEDS -> SettingKey.STREAM_SECTION_FEEDS
    }

/** The sub-page that holds a section's own settings, or null when it has none. */
internal fun StreamSection.settingsPage(): SettingsPage? =
    when (this) {
        StreamSection.NOTIFICATIONS -> SettingsPage.STREAM_NOTIFICATIONS
        StreamSection.AGENDA -> SettingsPage.STREAM_AGENDA
        StreamSection.TASKS -> SettingsPage.STREAM_TASKS
        StreamSection.FEEDS -> SettingsPage.STREAM_FEEDS
        StreamSection.NOW, StreamSection.RECENTS -> null
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
    )
}

/** The Stream page over a plain [layout], so screenshot tests draw it without a repository. */
@Composable
internal fun SettingsStreamSections(
    layout: StreamLayout,
    onLayoutAction: (StreamLayoutAction) -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(R.string.settings_stream_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            section.settingsPage()?.let { page ->
                SettingsClickableRow(
                    title = stringResource(page.titleRes),
                    subtitle = stringResource(page.summaryRes),
                    onClick = { onOpenPage(page) },
                )
            }
        }
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

/** Stream > Tasks: no provider yet; the next release connects one. Nothing to enter and nothing is stored. */
@Composable
internal fun SettingsStreamTasksPageContent() {
    SettingsSection(title = stringResource(R.string.settings_page_stream_tasks)) {
        SettingsListRow(
            modifier = Modifier.settingAnchor(SettingKey.TASKS_PROVIDER),
            title = SettingKey.TASKS_PROVIDER.label(),
            subtitle = stringResource(R.string.settings_tasks_not_connected),
        )
    }
}

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
