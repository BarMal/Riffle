package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import com.riffle.core.domain.launcher.settings.LauncherGestureSurface
import com.riffle.core.domain.launcher.settings.toHomeGesture

@Composable
fun HomeSwipeGestureSetting(
    settings: GestureSettings,
    installedApps: List<InstalledApp>,
    appShortcutsByApp: AppShortcutsByApp,
    onAction: (LauncherShellAction) -> Unit,
) {
    val homeGestures = settings.homeGestures
    var targetPickerRequest by remember { mutableStateOf<GestureTargetPickerRequest?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Home gestures",
            style = MaterialTheme.typography.bodyLarge,
        )
        homeGestureConflictSummary(settings)?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        gestureGroups.forEach { group ->
            GestureGroup(
                title = group.title,
                rows = group.rows,
                settings = homeGestures,
                installedApps = installedApps,
                onAction = onAction,
                onTargetPickerRequest = { gesture, action ->
                    targetPickerRequest = GestureTargetPickerRequest(gesture, action)
                },
            )
        }
        TextButton(onClick = { onAction(LauncherShellAction.ResetHomeSwipeGestureActions) }) {
            SettingsButtonText(text = "Reset")
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
private fun GestureGroup(
    title: String,
    rows: List<GestureRowState>,
    settings: HomeGestureSettings,
    installedApps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    onTargetPickerRequest: (HomeGesture, LauncherGestureAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        rows.forEach { row ->
            HomeGestureRow(
                label = row.label,
                gesture = row.gesture,
                action = settings.actionFor(row.gesture),
                launchTarget = settings.launchTargetFor(row.gesture),
                installedApps = installedApps,
                onAction = onAction,
                onTargetPickerRequest = onTargetPickerRequest,
            )
        }
    }
}

@Composable
private fun HomeGestureRow(
    label: String,
    gesture: HomeGesture,
    action: LauncherGestureAction,
    launchTarget: LauncherGestureLaunchTarget?,
    installedApps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    onTargetPickerRequest: (HomeGesture, LauncherGestureAction) -> Unit,
) {
    val isExpanded = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium,
        )
        Column {
            TextButton(onClick = { isExpanded.value = true }) {
                SettingsButtonText(text = action.targetLabel(launchTarget, installedApps))
            }
            DropdownMenu(
                expanded = isExpanded.value,
                onDismissRequest = { isExpanded.value = false },
            ) {
                LauncherGestureAction.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label) },
                        onClick = {
                            isExpanded.value = false
                            if (option.requiresLaunchTarget) {
                                onTargetPickerRequest(gesture, option)
                            } else {
                                onAction(
                                    LauncherShellAction.SelectHomeGestureAction(
                                        gesture = gesture,
                                        action = option,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

internal val LauncherGestureAction.label: String
    get() =
        when (this) {
            LauncherGestureAction.NONE -> "Disabled"
            LauncherGestureAction.OPEN_APP_DRAWER -> "Apps"
            LauncherGestureAction.OPEN_NOTIFICATIONS -> "Notifications"
            LauncherGestureAction.OPEN_SEARCH -> "Search"
            LauncherGestureAction.OPEN_SETTINGS -> "Settings"
            LauncherGestureAction.ENTER_HOME_EDIT_MODE -> "Edit home"
            LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW -> "Manage pages"
            LauncherGestureAction.ENTER_FULLSCREEN_HOME -> "Fullscreen home"
            LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> "Next page"
            LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> "Previous page"
            LauncherGestureAction.LAUNCH_APP -> "Launch app"
            LauncherGestureAction.LAUNCH_APP_SHORTCUT -> "Launch shortcut"
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
    val sameLabelShortcuts = shortcuts.filter { shortcut -> (shortcut.longLabel ?: shortcut.shortLabel) == shortcutLabel }
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
            HomeGesture.ONE_FINGER_UP -> "Swipe up"
            HomeGesture.ONE_FINGER_DOWN -> "Swipe down"
            HomeGesture.ONE_FINGER_LEFT -> "Swipe left"
            HomeGesture.ONE_FINGER_RIGHT -> "Swipe right"
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
    val label: String,
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
                    GestureRowState("Swipe up", HomeGesture.ONE_FINGER_UP),
                    GestureRowState("Swipe down", HomeGesture.ONE_FINGER_DOWN),
                    GestureRowState("Swipe left", HomeGesture.ONE_FINGER_LEFT),
                    GestureRowState("Swipe right", HomeGesture.ONE_FINGER_RIGHT),
                ),
        ),
        GestureGroupState(
            title = "Two fingers",
            rows =
                listOf(
                    GestureRowState("Swipe up", HomeGesture.TWO_FINGER_UP),
                    GestureRowState("Swipe down", HomeGesture.TWO_FINGER_DOWN),
                    GestureRowState("Swipe left", HomeGesture.TWO_FINGER_LEFT),
                    GestureRowState("Swipe right", HomeGesture.TWO_FINGER_RIGHT),
                ),
        ),
        GestureGroupState(
            title = "Pinch",
            rows =
                listOf(
                    GestureRowState("Pinch in", HomeGesture.PINCH_IN),
                    GestureRowState("Pinch out", HomeGesture.PINCH_OUT),
                ),
        ),
        GestureGroupState(
            title = "Three fingers",
            rows =
                listOf(
                    GestureRowState("Swipe up", HomeGesture.THREE_FINGER_UP),
                    GestureRowState("Swipe down", HomeGesture.THREE_FINGER_DOWN),
                    GestureRowState("Swipe left", HomeGesture.THREE_FINGER_LEFT),
                    GestureRowState("Swipe right", HomeGesture.THREE_FINGER_RIGHT),
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
