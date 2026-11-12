package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.workspace.SourceContentLevelsController
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelRow

internal const val PRIVACY_SCREEN_SWITCH_TAG = "privacy-screen-switch"
internal const val PRIVACY_LEVELS_PREVIEW_OFF_TAG = "privacy-levels-preview-off"

internal fun contentLevelTestTag(
    id: SourceId,
    level: SourceContentLevel,
): String = "content-level-${id.value}-${level.storedId}"

/**
 * Privacy and access: the access each part of Riffle can use (with its state), then what the Stream and widgets show.
 * Without a workspace host (tests, nothing loaded) the content levels show a note. Adds no permission, prompt or
 * background work.
 */
@Composable
internal fun SettingsPrivacyPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val host = LocalWorkspaceSettingsHost.current
    val controller = host?.contentLevels
    val rows = if (controller == null) null else rememberContentLevelRows(controller)
    SettingsPermissionsSection(
        homeRoleStatus = state.homeRoleStatus,
        firstRunStatus = state.firstRunStatus,
        notificationAccessStatus = state.notificationAccessStatus,
        overlayDockPermissionStatus = state.overlayDockPermissionStatus,
        calendarAccessStatus = state.calendarAccessStatus,
        onAction = onAction,
    )
    SettingsPrivacySections(
        protectScreenContent = state.settings.privacy.protectScreenContent,
        onProtectScreenContent = { enabled -> onAction(LauncherShellAction.SetScreenContentProtection(enabled)) },
        contentLevels = rows,
        onSelectLevel = { id, level -> controller?.setLevel(id, level) },
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
 * The Privacy sections over plain values, so screenshot tests draw them without a runtime. [contentLevels] null means
 * the sources are not loaded.
 */
@Composable
internal fun SettingsPrivacySections(
    protectScreenContent: Boolean,
    onProtectScreenContent: (Boolean) -> Unit,
    contentLevels: List<ContentLevelRow>?,
    onSelectLevel: (SourceId, SourceContentLevel) -> Unit,
) {
    SettingsSection(title = PrivacySettingsText.SCREEN_TITLE) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.PRIVACY_SCREEN_PROTECTION).testTag(PRIVACY_SCREEN_SWITCH_TAG),
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
                    modifier =
                        Modifier.settingAnchor(
                            SettingKey.PRIVACY_CONTENT_LEVEL,
                        ).testTag(PRIVACY_LEVELS_PREVIEW_OFF_TAG),
                    title = PrivacySettingsText.LEVELS_PREVIEW_OFF,
                )
            contentLevels.isEmpty() ->
                SettingsListRow(
                    modifier = Modifier.settingAnchor(SettingKey.PRIVACY_CONTENT_LEVEL),
                    title = PrivacySettingsText.LEVELS_NONE,
                )
            else -> {
                PrivacyNote(PrivacySettingsText.LEVELS_NOTE)
                contentLevels.forEachIndexed { index, row ->
                    ContentLevelOptions(
                        modifier =
                            if (index == 0) {
                                Modifier.settingAnchor(
                                    SettingKey.PRIVACY_CONTENT_LEVEL,
                                )
                            } else {
                                Modifier
                            },
                        id = row.id,
                        sourceTitle = row.title,
                        current = row.level,
                        onSelect = { level -> onSelectLevel(row.id, level) },
                    )
                }
            }
        }
    }
    SettingsSection(title = PrivacySettingsText.PROFILES_TITLE) {
        PrivacyNote(PrivacySettingsText.PROFILES_BODY)
        PrivacyNote(PrivacySettingsText.STORAGE_BODY)
    }
}

/**
 * The three content levels of one source as a choice row (a radio group, each option one 48 dp target spoken with the
 * source's name and what it shows).
 */
@Composable
internal fun ContentLevelOptions(
    id: SourceId,
    sourceTitle: String,
    current: SourceContentLevel,
    onSelect: (SourceContentLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsChoiceRow(
        modifier = modifier,
        title = sourceTitle,
        subtitle = PrivacySettingsText.summary(current),
        options = SourceContentLevel.entries,
        selected = current,
        onSelect = onSelect,
        label = { level -> PrivacySettingsText.label(level) },
        optionTag = { level -> contentLevelTestTag(id, level) },
        optionDescription = { level -> PrivacySettingsText.optionSpoken(sourceTitle, level) },
    )
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
