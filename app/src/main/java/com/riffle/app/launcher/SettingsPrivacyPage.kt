package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.workspace.SourceContentLevelsController
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelRow

internal const val PRIVACY_SCREEN_SWITCH_TAG = "privacy-screen-switch"
internal const val PRIVACY_LEVELS_PREVIEW_OFF_TAG = "privacy-levels-preview-off"
internal const val PRIVACY_OPEN_EXCLUSIONS_TAG = "privacy-open-exclusions"
internal const val PRIVACY_OPEN_SOURCES_TAG = "privacy-open-sources"

internal fun contentLevelTestTag(
    id: SourceId,
    level: SourceContentLevel,
): String = "content-level-${id.value}-${level.storedId}"

/**
 * Settings > Privacy (design section 6). A main page: the screenshot and recents switch, the content level of each
 * privacy-sensitive source (S9: applied on the launcher's home, whose lens pages read them) and links to Hidden items
 * and Sources. Without a workspace host (tests, nothing loaded) only the switch and a note show. Adds no permission,
 * prompt or background work.
 */
@Composable
internal fun SettingsPrivacyPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    val controller = host?.contentLevels
    val rows = if (controller == null) null else rememberContentLevelRows(controller)
    SettingsPrivacySections(
        protectScreenContent = state.settings.privacy.protectScreenContent,
        onProtectScreenContent = { enabled -> onAction(LauncherShellAction.SetScreenContentProtection(enabled)) },
        contentLevels = rows,
        onSelectLevel = { id, level -> controller?.setLevel(id, level) },
        onOpenExclusions = host?.let { { onPageSelected(SettingsPage.EXCLUSIONS) } },
        onOpenSources = host?.let { { onPageSelected(SettingsPage.SOURCES) } },
    )
}

/** The current rows of [controller], following its levels while composed. */
@Composable
internal fun rememberContentLevelRows(controller: SourceContentLevelsController): List<ContentLevelRow> {
    DisposableEffect(controller) {
        controller.open()
        onDispose { controller.close() }
    }
    val levels by controller.levels.collectAsState()
    return remember(controller, levels) { controller.rows(levels) }
}

/**
 * The Privacy page over plain values, so screenshot tests draw it without a runtime. [contentLevels] null means the
 * Workspaces preview is off; [onOpenExclusions] and [onOpenSources] null hide those links (their pages need it).
 */
@Suppress("LongParameterList") // Fixed-state page content: the page's values and its callbacks.
@Composable
internal fun SettingsPrivacySections(
    protectScreenContent: Boolean,
    onProtectScreenContent: (Boolean) -> Unit,
    contentLevels: List<ContentLevelRow>?,
    onSelectLevel: (SourceId, SourceContentLevel) -> Unit,
    onOpenExclusions: (() -> Unit)?,
    onOpenSources: (() -> Unit)?,
) {
    SettingsSection(title = PrivacySettingsText.SCREEN_TITLE) {
        SettingsSwitchRow(
            modifier = Modifier.testTag(PRIVACY_SCREEN_SWITCH_TAG),
            title = PrivacySettingsText.SCREEN_SWITCH,
            subtitle = if (protectScreenContent) PrivacySettingsText.SCREEN_ON else PrivacySettingsText.SCREEN_OFF,
            checked = protectScreenContent,
            onCheckedChange = onProtectScreenContent,
        )
    }
    SettingsSection(title = PrivacySettingsText.LEVELS_TITLE) {
        when {
            contentLevels == null ->
                SettingsListRow(
                    modifier = Modifier.testTag(PRIVACY_LEVELS_PREVIEW_OFF_TAG),
                    title = PrivacySettingsText.LEVELS_PREVIEW_OFF,
                )
            contentLevels.isEmpty() -> SettingsListRow(title = PrivacySettingsText.LEVELS_NONE)
            else -> {
                PrivacyNote(PrivacySettingsText.LEVELS_NOTE)
                contentLevels.forEach { row ->
                    ContentLevelOptions(
                        id = row.id,
                        sourceTitle = row.title,
                        current = row.level,
                        onSelect = { level -> onSelectLevel(row.id, level) },
                        showHeading = true,
                    )
                }
            }
        }
    }
    if (onOpenExclusions != null || onOpenSources != null) {
        SettingsSection(title = PrivacySettingsText.HIDDEN_TITLE) {
            onOpenExclusions?.let { open ->
                SettingsClickableRow(
                    modifier = Modifier.testTag(PRIVACY_OPEN_EXCLUSIONS_TAG),
                    title = ExclusionsSettingsText.TITLE,
                    subtitle = ExclusionsSettingsText.ROW_SUBTITLE,
                    onClick = open,
                )
            }
            onOpenSources?.let { open ->
                SettingsClickableRow(
                    modifier = Modifier.testTag(PRIVACY_OPEN_SOURCES_TAG),
                    title = PrivacySettingsText.SOURCES_ROW,
                    subtitle = PrivacySettingsText.SOURCES_ROW_SUBTITLE,
                    onClick = open,
                )
            }
        }
    }
    SettingsSection(title = PrivacySettingsText.PROFILES_TITLE) {
        PrivacyNote(PrivacySettingsText.PROFILES_BODY)
        PrivacyNote(PrivacySettingsText.STORAGE_BODY)
    }
}

/**
 * The three content levels of one source as a radio group (one focusable row per option, 48 dp or taller, spoken
 * with the source's name). Used on the Privacy page ([showHeading]) and on the source's own page.
 */
@Composable
internal fun ContentLevelOptions(
    id: SourceId,
    sourceTitle: String,
    current: SourceContentLevel,
    onSelect: (SourceContentLevel) -> Unit,
    showHeading: Boolean = false,
) {
    Column(modifier = Modifier.selectableGroup()) {
        if (showHeading) {
            Text(
                modifier =
                    Modifier
                        .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs)
                        .semantics { heading() },
                text = sourceTitle,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        SourceContentLevel.entries.forEach { level ->
            val isSelected = level == current
            ListItem(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RiffleShapes.medium)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { if (!isSelected) onSelect(level) },
                        ).testTag(contentLevelTestTag(id, level))
                        .semantics(mergeDescendants = true) {
                            contentDescription = PrivacySettingsText.optionSpoken(sourceTitle, level)
                            if (isSelected) stateDescription = PrivacySettingsText.SELECTED
                        },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                headlineContent = { Text(text = PrivacySettingsText.label(level)) },
                supportingContent = { Text(text = PrivacySettingsText.summary(level)) },
            )
        }
    }
}

@Composable
private fun PrivacyNote(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = RiffleSpacing.m),
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
