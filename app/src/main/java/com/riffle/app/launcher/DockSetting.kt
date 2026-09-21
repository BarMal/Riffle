@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.placeableDockPositions
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun DockSetting(
    dock: DockModel,
    viewMode: LauncherViewMode,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DockVisibilitySetting(
            enabled = dock.isEnabled,
            onAction = onAction,
        )
        DockNotificationCardsSetting(
            enabled = dock.showNotificationCards,
            notificationAccessStatus = notificationAccessStatus,
            onAction = onAction,
        )
        DockPositionSetting(
            position = dock.position,
            placeablePositions = viewMode.placeableDockPositions,
            onAction = onAction,
        )
        DockExpandableSetting(
            expandable = dock.isExpandable,
            onAction = onAction,
        )
        if (dock.isExpandable) {
            DockExpandAffordanceSetting(
                affordance = dock.expandAffordance,
                onAction = onAction,
            )
            DockPanelSetting(
                hasPanel = dock.panel != null,
                onAction = onAction,
            )
        }
        DockIconSizeSetting(
            sizeDp = dock.iconSizeDp,
            onAction = onAction,
        )
        DockBackgroundAlphaSetting(
            alphaPercent = dock.backgroundAlphaPercent,
            onAction = onAction,
        )
        DockVisualEffectSetting(
            effect = dock.visualEffect,
            onAction = onAction,
        )
        DockBackgroundSizingSetting(
            sizing = dock.backgroundSizing,
            onAction = onAction,
        )
        DockCornerRadiusSetting(
            cornerRadiusDp = dock.cornerRadiusDp,
            onAction = onAction,
        )
        DockHomeControlsSpacingSetting(
            spacingDp = dock.homeControlsSpacingDp,
            onAction = onAction,
        )
        DockAlignmentSetting(
            alignment = dock.alignment,
            onAction = onAction,
        )
    }
}

@Composable
private fun DockVisualEffectSetting(
    effect: DockVisualEffect,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Dock effect",
        subtitle = "${effect.name.lowercase().replaceFirstChar(Char::uppercase)} Material treatment",
        options = DockVisualEffect.entries,
        selected = effect,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockVisualEffect(candidate)) },
        label = { candidate -> candidate.name.lowercase().replaceFirstChar(Char::uppercase) },
    )
}

@Composable
private fun DockIconSizeSetting(
    sizeDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock height",
    value = sizeDp,
    valueRange = MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockIconSize(value)) },
)

@Composable
private fun DockBackgroundAlphaSetting(
    alphaPercent: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock background",
    value = alphaPercent,
    valueRange = MIN_DOCK_BACKGROUND_ALPHA_PERCENT..MAX_DOCK_BACKGROUND_ALPHA_PERCENT,
    valueLabel = { "$it%" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockBackgroundAlpha(value)) },
)

@Composable
private fun DockBackgroundSizingSetting(
    sizing: DockBackgroundSizing,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Dock width",
        subtitle =
            when (sizing) {
                DockBackgroundSizing.DYNAMIC -> "Fits dock items"
                DockBackgroundSizing.FIXED -> "Uses available width"
            },
        options = DockBackgroundSizing.entries,
        selected = sizing,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockBackgroundSizing(candidate)) },
        label = { candidate ->
            when (candidate) {
                DockBackgroundSizing.DYNAMIC -> "Fit content"
                DockBackgroundSizing.FIXED -> "Full width"
            }
        },
    )
}

@Composable
private fun DockCornerRadiusSetting(
    cornerRadiusDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Dock corner radius",
    value = cornerRadiusDp,
    valueRange = MIN_DOCK_CORNER_RADIUS_DP..MAX_DOCK_CORNER_RADIUS_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockCornerRadius(value)) },
)

@Composable
private fun DockHomeControlsSpacingSetting(
    spacingDp: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Grid to dock controls spacing",
    value = spacingDp,
    valueRange = MIN_DOCK_HOME_CONTROLS_SPACING_DP..MAX_DOCK_HOME_CONTROLS_SPACING_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockHomeControlsSpacing(value)) },
)

@Composable
private fun DockAlignmentSetting(
    alignment: DockAlignment,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Dock alignment",
        subtitle = "Places a content-sized dock on the home screen",
        options = DockAlignment.entries,
        selected = alignment,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockAlignment(candidate)) },
        label = { candidate ->
            when (candidate) {
                DockAlignment.START -> "Start"
                DockAlignment.CENTER -> "Center"
                DockAlignment.END -> "End"
            }
        },
    )
}

@Composable
private fun DockVisibilitySetting(
    enabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Show dock",
        subtitle = if (enabled) "Dock visible on home" else "Home grid uses dock space",
        checked = enabled,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockEnabled(value)) },
    )
}

/**
 * Which edge the dock occupies. Lives with the dock's other settings and therefore applies to the
 * layout the settings screen is showing, the same as every control beside it.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DockPositionSetting(
    position: DockPosition?,
    placeablePositions: List<DockPosition>,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsTextColumn(
            title = "Dock position",
            subtitle = dockPositionSubtitle(position, placeablePositions),
        )
        // Wrapping, because four edges do not fit across a phone: a plain Row measured the last
        // one to nothing, so a Cards layout could never reach whichever edge fell off the end.
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            placeablePositions.forEach { candidate ->
                TextButton(
                    modifier =
                        Modifier
                            .testTag("dock-position-${candidate.name}")
                            .semantics { selected = candidate == position },
                    enabled = candidate != position,
                    onClick = { onAction(LauncherShellAction.SelectDockPosition(candidate)) },
                ) {
                    SettingsButtonText(text = candidate.label())
                }
            }
        }
    }
}

/**
 * What the control says the dock is doing, which is not always what is stored.
 *
 * A layout can hold an edge it cannot place -- the top edge was offered to every layout before the
 * home dock's three were separated from the rail's four -- and saying "Top edge" while the dock
 * sits at the bottom is the papercut this whole change is about. Naming the mismatch is the one
 * honest option left, and tapping any offered edge leaves the state behind.
 */
private fun dockPositionSubtitle(
    position: DockPosition?,
    placeablePositions: List<DockPosition>,
): String =
    when {
        position == null -> "Following the active Cards template"
        position in placeablePositions -> position.label()
        else -> "${position.label()} is not available on this layout, so the dock is on the bottom edge"
    }

private fun DockPosition.label(): String =
    when (this) {
        DockPosition.LEFT -> "Left edge"
        DockPosition.RIGHT -> "Right edge"
        DockPosition.TOP -> "Top edge"
        DockPosition.BOTTOM -> "Bottom edge"
    }

@Composable
private fun DockExpandableSetting(
    expandable: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Expandable dock",
        subtitle =
            if (expandable) {
                "Dock opens a shelf for overflow and notification cards"
            } else {
                "Dock stays a single strip of shortcuts"
            },
        checked = expandable,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockExpandable(value)) },
    )
}

@Composable
private fun DockExpandAffordanceSetting(
    affordance: DockExpandAffordance,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Open the shelf with",
        subtitle =
            when (affordance) {
                DockExpandAffordance.GESTURE -> "Swipe up on the dock; the dock's swipe-up action is unused"
                DockExpandAffordance.BUTTON -> "A button on the dock; swipe up runs the dock's own action"
            },
        options = DockExpandAffordance.entries,
        selected = affordance,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockExpandAffordance(candidate)) },
        label = { candidate ->
            when (candidate) {
                DockExpandAffordance.GESTURE -> "Swipe"
                DockExpandAffordance.BUTTON -> "Button"
            }
        },
    )
}

@Composable
private fun DockPanelSetting(
    hasPanel: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Dock panel",
        subtitle =
            if (hasPanel) {
                "A small grid of widgets and shortcuts on the expanded dock"
            } else {
                "Turning this on adds an empty grid; turning it off removes it and anything on it"
            },
        checked = hasPanel,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectDockPanelEnabled(value)) },
    )
}

@Composable
private fun DockNotificationCardsSetting(
    enabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Dock notifications",
        subtitle = dockNotificationCardsSettingSubtitle(enabled, notificationAccessStatus),
        checked = enabled,
        onCheckedChange = { value ->
            dockNotificationCardsEnabledActions(
                enabled = value,
                wasEnabled = enabled,
                notificationAccessStatus = notificationAccessStatus,
            ).forEach(onAction)
        },
    )
}

internal fun dockNotificationCardsEnabledActions(
    enabled: Boolean,
    wasEnabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
): List<LauncherShellAction> =
    buildList {
        add(LauncherShellAction.SelectDockNotificationCardsEnabled(enabled))
        if (enabled && !wasEnabled && notificationAccessStatus != NotificationAccessStatus.GRANTED) {
            add(LauncherShellAction.RequestNotificationAccess)
        }
    }

internal fun dockNotificationCardsSettingSubtitle(
    enabled: Boolean,
    notificationAccessStatus: NotificationAccessStatus,
): String {
    if (!enabled) {
        return "Dock only shows what you pinned to it"
    }

    return when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED -> "Dock shows apps with notifications beside your pinned ones"
        NotificationAccessStatus.NOT_GRANTED -> "Notification cards are on, but access is not allowed"
        NotificationAccessStatus.REVOKED -> "Notification cards are on, but access was revoked"
        NotificationAccessStatus.UNKNOWN -> "Notification cards are on, but access has not been checked"
    }
}
