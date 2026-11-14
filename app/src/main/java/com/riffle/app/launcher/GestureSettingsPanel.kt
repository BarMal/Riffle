package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import com.riffle.core.domain.launcher.settings.LauncherGestureSurface
import com.riffle.core.domain.launcher.settings.toHomeGesture

/**
 * Gestures: the fourteen swipes and pinches on Home, each a row whose menu picks its action. Draws as sections (one per
 * finger count), so the page itself adds none.
 */
@Composable
fun HomeSwipeGestureSetting(
    settings: GestureSettings,
    installedApps: List<InstalledApp>,
    appShortcutsByApp: AppShortcutsByApp,
    onAction: (LauncherShellAction) -> Unit,
) {
    val homeGestures = settings.homeGestures
    var targetPickerRequest by remember { mutableStateOf<GestureTargetPickerRequest?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        homeGestureConflictSummary(settings)?.let { summary ->
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        gestureGroups.forEach { group ->
            SettingsSection(title = group.title) {
                group.rows.forEach { row ->
                    HomeGestureRow(
                        gesture = row.gesture,
                        action = homeGestures.actionFor(row.gesture),
                        launchTarget = homeGestures.launchTargetFor(row.gesture),
                        installedApps = installedApps,
                        onAction = onAction,
                        onTargetPickerRequest = { gesture, action ->
                            targetPickerRequest = GestureTargetPickerRequest(gesture, action)
                        },
                    )
                }
            }
        }
        SettingsSection(title = stringResource(R.string.settings_gestures_reset_section)) {
            SettingsClickableRow(
                title = stringResource(R.string.settings_gestures_reset),
                subtitle = stringResource(R.string.settings_gestures_reset_subtitle),
                onClick = { onAction(LauncherShellAction.ResetHomeSwipeGestureActions) },
                trailingContent = {
                    SettingsButtonText(
                        text = stringResource(R.string.settings_gestures_reset_action),
                    )
                },
            )
        }
    }
    targetPickerRequest?.let { request ->
        GestureTargetPicker(
            request = request,
            installedApps = installedApps,
            appShortcutsByApp = appShortcutsByApp,
            onDismissRequest = { targetPickerRequest = null },
            onTargetSelected = { target ->
                onAction(
                    LauncherShellAction.SelectHomeGestureAction(
                        gesture = request.gesture,
                        action = request.action,
                        launchTarget = target,
                    ),
                )
                targetPickerRequest = null
            },
        )
    }
}

@Composable
private fun HomeGestureRow(
    gesture: HomeGesture,
    action: LauncherGestureAction,
    launchTarget: LauncherGestureLaunchTarget?,
    installedApps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    onTargetPickerRequest: (HomeGesture, LauncherGestureAction) -> Unit,
) {
    val isExpanded = remember { mutableStateOf(false) }

    Box {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(gesture.settingKey()),
            title = gesture.label,
            subtitle = action.targetLabel(launchTarget, installedApps),
            onClick = { isExpanded.value = true },
            trailingContent = { SettingsButtonText(text = stringResource(R.string.settings_gestures_change)) },
        )
        RiffleContextMenu(
            expanded = isExpanded.value,
            onDismissRequest = { isExpanded.value = false },
        ) {
            gestureActionOptions().forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.label) },
                    onClick = {
                        isExpanded.value = false
                        if (option.requiresLaunchTarget) {
                            onTargetPickerRequest(gesture, option)
                        } else {
                            onAction(LauncherShellAction.SelectHomeGestureAction(gesture = gesture, action = option))
                        }
                    },
                )
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
internal fun HomeGesture.settingKey(): SettingKey =
    when (this) {
        HomeGesture.ONE_FINGER_UP -> SettingKey.GESTURE_ONE_FINGER_UP
        HomeGesture.ONE_FINGER_DOWN -> SettingKey.GESTURE_ONE_FINGER_DOWN
        HomeGesture.ONE_FINGER_LEFT -> SettingKey.GESTURE_ONE_FINGER_LEFT
        HomeGesture.ONE_FINGER_RIGHT -> SettingKey.GESTURE_ONE_FINGER_RIGHT
        HomeGesture.TWO_FINGER_UP -> SettingKey.GESTURE_TWO_FINGER_UP
        HomeGesture.TWO_FINGER_DOWN -> SettingKey.GESTURE_TWO_FINGER_DOWN
        HomeGesture.TWO_FINGER_LEFT -> SettingKey.GESTURE_TWO_FINGER_LEFT
        HomeGesture.TWO_FINGER_RIGHT -> SettingKey.GESTURE_TWO_FINGER_RIGHT
        HomeGesture.THREE_FINGER_UP -> SettingKey.GESTURE_THREE_FINGER_UP
        HomeGesture.THREE_FINGER_DOWN -> SettingKey.GESTURE_THREE_FINGER_DOWN
        HomeGesture.THREE_FINGER_LEFT -> SettingKey.GESTURE_THREE_FINGER_LEFT
        HomeGesture.THREE_FINGER_RIGHT -> SettingKey.GESTURE_THREE_FINGER_RIGHT
        HomeGesture.PINCH_IN -> SettingKey.GESTURE_PINCH_IN
        HomeGesture.PINCH_OUT -> SettingKey.GESTURE_PINCH_OUT
    }

/** The actions a home gesture can be bound to, in menu order. */
internal fun gestureActionOptions(): List<LauncherGestureAction> =
    listOf(
        LauncherGestureAction.NONE,
        LauncherGestureAction.OPEN_SEARCH,
        LauncherGestureAction.OPEN_LIBRARY,
        LauncherGestureAction.OPEN_STREAM,
        LauncherGestureAction.OPEN_SETTINGS,
        LauncherGestureAction.ENTER_HOME_EDIT_MODE,
        LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW,
        LauncherGestureAction.ENTER_FULLSCREEN_HOME,
        LauncherGestureAction.SELECT_NEXT_HOME_PAGE,
        LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE,
        LauncherGestureAction.LAUNCH_APP,
        LauncherGestureAction.LAUNCH_APP_SHORTCUT,
    )

internal val LauncherGestureAction.label: String
    get() =
        when (this) {
            LauncherGestureAction.NONE -> "None"
            LauncherGestureAction.OPEN_SEARCH -> "Open search"
            LauncherGestureAction.OPEN_SETTINGS -> "Open settings"
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> "Edit Home"
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW -> "Page overview"
            LauncherGestureAction.ENTER_FULLSCREEN_HOME -> "Full-screen Home"
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> "Next page"
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> "Previous page"
            LauncherGestureAction.LAUNCH_APP -> "Launch app"
            LauncherGestureAction.LAUNCH_APP_SHORTCUT -> "Launch shortcut"
            LauncherGestureAction.OPEN_STREAM -> "Open Stream"
            LauncherGestureAction.OPEN_LIBRARY -> "Open Library"
        }

private val LauncherGestureAction.requiresLaunchTarget: Boolean
    get() = this == LauncherGestureAction.LAUNCH_APP || this == LauncherGestureAction.LAUNCH_APP_SHORTCUT

internal fun LauncherGestureAction.targetLabel(
    target: LauncherGestureLaunchTarget?,
    installedApps: List<InstalledApp>,
): String =
    when (target) {
        is LauncherGestureLaunchTarget.App ->
            "$label: ${installedApps.gestureTargetLabel(target) ?: target.identity.packageName.value}"
        is LauncherGestureLaunchTarget.Shortcut -> "$label: ${target.shortcut.longLabel ?: target.shortcut.shortLabel}"
        null -> label
    }

private fun List<InstalledApp>.gestureTargetLabel(target: LauncherGestureLaunchTarget.App): String? {
    return firstOrNull { app -> app.identity == target.identity }?.gesturePickerLabel(this)
}

internal fun InstalledApp.gesturePickerLabel(installedApps: List<InstalledApp>): String {
    val sameLabelApps = installedApps.filter { app -> app.label == label }
    val profileIsAmbiguous = sameLabelApps.any { app -> app.identity.profile != identity.profile }
    val activityIsAmbiguous =
        sameLabelApps
            .filter { app -> app.identity.profile == identity.profile }
            .map { app -> app.identity.activityName }
            .distinct()
            .size > 1
    val appLabel =
        if (profileIsAmbiguous) {
            identity.profile.profileDisplayLabel(label)
        } else {
            label
        }

    return if (activityIsAmbiguous) "$appLabel (${identity.activityName.value})" else appLabel
}

internal fun AppShortcut.gesturePickerLabel(
    shortcuts: List<AppShortcut>,
    installedApps: List<InstalledApp>,
): String {
    val shortcutLabel = longLabel ?: shortLabel
    val sameLabelShortcuts =
        shortcuts.filter { shortcut ->
            (shortcut.longLabel ?: shortcut.shortLabel) == shortcutLabel
        }
    if (sameLabelShortcuts.size == 1) return shortcutLabel

    val owningAppLabel =
        installedApps
            .firstOrNull { app -> app.identity == appIdentity }
            ?.gesturePickerLabel(installedApps)
            ?: appIdentity.packageName.value
    val sameAppShortcuts = sameLabelShortcuts.filter { shortcut -> shortcut.appIdentity == appIdentity }
    val shortcutIdSuffix = if (sameAppShortcuts.size > 1) " (${id.value})" else ""

    return "$shortcutLabel — $owningAppLabel$shortcutIdSuffix"
}

internal val HomeGesture.label: String
    get() =
        when (this) {
            HomeGesture.ONE_FINGER_UP -> "One-finger swipe up"
            HomeGesture.ONE_FINGER_DOWN -> "One-finger swipe down"
            HomeGesture.ONE_FINGER_LEFT -> "One-finger swipe left"
            HomeGesture.ONE_FINGER_RIGHT -> "One-finger swipe right"
            HomeGesture.TWO_FINGER_UP -> "Two-finger swipe up"
            HomeGesture.TWO_FINGER_DOWN -> "Two-finger swipe down"
            HomeGesture.TWO_FINGER_LEFT -> "Two-finger swipe left"
            HomeGesture.TWO_FINGER_RIGHT -> "Two-finger swipe right"
            HomeGesture.THREE_FINGER_UP -> "Three-finger swipe up"
            HomeGesture.THREE_FINGER_DOWN -> "Three-finger swipe down"
            HomeGesture.THREE_FINGER_LEFT -> "Three-finger swipe left"
            HomeGesture.THREE_FINGER_RIGHT -> "Three-finger swipe right"
            HomeGesture.PINCH_IN -> "Pinch in"
            HomeGesture.PINCH_OUT -> "Pinch out"
        }

internal fun homeGestureConflictSummary(settings: GestureSettings): String? =
    settings.conflicts
        .filter { conflict -> conflict.surface == LauncherGestureSurface.HOME_PAGE }
        .takeIf { conflicts -> conflicts.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = "Conflicting gestures: ") { conflict ->
            "${conflict.action.label}: ${conflict.gestures.joinToString { gesture -> gesture.toHomeGesture().label }}"
        }

private data class GestureRowState(
    val gesture: HomeGesture,
)

private data class GestureGroupState(
    val title: String,
    val rows: List<GestureRowState>,
)

private val gestureGroups =
    listOf(
        GestureGroupState(
            title = "One finger",
            rows =
                listOf(
                    GestureRowState(HomeGesture.ONE_FINGER_UP),
                    GestureRowState(HomeGesture.ONE_FINGER_DOWN),
                    GestureRowState(HomeGesture.ONE_FINGER_LEFT),
                    GestureRowState(HomeGesture.ONE_FINGER_RIGHT),
                ),
        ),
        GestureGroupState(
            title = "Two fingers",
            rows =
                listOf(
                    GestureRowState(HomeGesture.TWO_FINGER_UP),
                    GestureRowState(HomeGesture.TWO_FINGER_DOWN),
                    GestureRowState(HomeGesture.TWO_FINGER_LEFT),
                    GestureRowState(HomeGesture.TWO_FINGER_RIGHT),
                ),
        ),
        GestureGroupState(
            title = "Pinch",
            rows =
                listOf(
                    GestureRowState(HomeGesture.PINCH_IN),
                    GestureRowState(HomeGesture.PINCH_OUT),
                ),
        ),
        GestureGroupState(
            title = "Three fingers",
            rows =
                listOf(
                    GestureRowState(HomeGesture.THREE_FINGER_UP),
                    GestureRowState(HomeGesture.THREE_FINGER_DOWN),
                    GestureRowState(HomeGesture.THREE_FINGER_LEFT),
                    GestureRowState(HomeGesture.THREE_FINGER_RIGHT),
                ),
        ),
    )

private data class GestureTargetPickerRequest(
    val gesture: HomeGesture,
    val action: LauncherGestureAction,
)

@Composable
@Suppress("CyclomaticComplexMethod", "ComplexCondition")
private fun GestureTargetPicker(
    request: GestureTargetPickerRequest,
    installedApps: List<InstalledApp>,
    appShortcutsByApp: AppShortcutsByApp,
    onDismissRequest: () -> Unit,
    onTargetSelected: (LauncherGestureLaunchTarget) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val appMatches =
        installedApps
            .filter(InstalledApp::enabled)
            .filter { app -> normalizedQuery.isBlank() || app.label.contains(normalizedQuery, ignoreCase = true) }
    val shortcutMatches =
        appShortcutsByApp.values
            .flatten()
            .filter(AppShortcut::enabled)
            .filter { shortcut ->
                normalizedQuery.isBlank() ||
                    shortcut.shortLabel.contains(normalizedQuery, ignoreCase = true) ||
                    shortcut.longLabel?.contains(normalizedQuery, ignoreCase = true) == true
            }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text =
                    if (request.action == LauncherGestureAction.LAUNCH_APP) {
                        "Choose app"
                    } else {
                        "Choose shortcut"
                    },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                )
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    when (request.action) {
                        LauncherGestureAction.LAUNCH_APP ->
                            appMatches.forEach { app ->
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onTargetSelected(LauncherGestureLaunchTarget.App(app.identity)) },
                                ) { Text(app.gesturePickerLabel(appMatches)) }
                            }

                        LauncherGestureAction.LAUNCH_APP_SHORTCUT ->
                            shortcutMatches.forEach { shortcut ->
                                TextButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onTargetSelected(LauncherGestureLaunchTarget.Shortcut(shortcut)) },
                                ) { Text(shortcut.gesturePickerLabel(shortcutMatches, installedApps)) }
                            }

                        else -> Unit
                    }
                    if ((request.action == LauncherGestureAction.LAUNCH_APP && appMatches.isEmpty()) ||
                        (request.action == LauncherGestureAction.LAUNCH_APP_SHORTCUT && shortcutMatches.isEmpty())
                    ) {
                        Text(
                            text = "No matches",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
    )
}
