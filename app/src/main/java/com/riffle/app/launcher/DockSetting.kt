@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_CAPACITY
import com.riffle.core.domain.launcher.home.MAX_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_NOTIFICATION_SLOT_COUNT
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_CAPACITY
import com.riffle.core.domain.launcher.home.MIN_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_NOTIFICATION_SLOT_COUNT
import com.riffle.core.domain.launcher.home.sharedDockPositions
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

@Composable
internal fun DockSetting(
    dock: DockModel,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DockSizeSummary(
            capacity = dock.capacity,
            notificationSlotCount = dock.notificationSlotCount,
            showNotificationCards = dock.showNotificationCards,
        )
        DockVisibilitySetting(
            enabled = dock.isEnabled,
            onAction = onAction,
        )
        DockCapacitySetting(
            capacity = dock.capacity,
            onAction = onAction,
        )
        DockNotificationCardsSetting(
            enabled = dock.showNotificationCards,
            notificationAccessStatus = notificationAccessStatus,
            onAction = onAction,
        )
        if (dock.showNotificationCards) {
            DockNotificationSlotCountSetting(
                slotCount = dock.notificationSlotCount,
                onAction = onAction,
            )
        }
        DockPositionSetting(
            position = dock.position,
            // One dock shared by every mode (#1205), so only the edges every mode can draw it on.
            placeablePositions = sharedDockPositions,
            onAction = onAction,
        )
        // Hidden while shelf expansion is switched off launcher-wide: the settings would do nothing.
        if (DockShelfExpansion.enabled) {
            DockExpandableSetting(
                expandable = dock.isExpandable,
                onAction = onAction,
            )
        }
        if (DockShelfExpansion.enabled && dock.isExpandable) {
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
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsTextColumn(
            title = "Dock effect",
            subtitle = "${effect.name.lowercase().replaceFirstChar(Char::uppercase)} Material treatment",
        )
        DockVisualEffect.entries.forEach { candidate ->
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = candidate != effect,
                onClick = { onAction(LauncherShellAction.SelectDockVisualEffect(candidate)) },
            ) {
                SettingsButtonText(text = candidate.name.lowercase().replaceFirstChar(Char::uppercase))
            }
        }
    }
}

/**
 * A one-line readout of the dock's total visible size and, once notifications are on, how that
 * total splits between the two sections -- so the two sliders below read as one budget rather than
 * two numbers with no visible relationship.
 */
@Composable
private fun DockSizeSummary(
    capacity: Int,
    notificationSlotCount: Int,
    showNotificationCards: Boolean,
) {
    Text(
        text = dockSizeSummaryText(capacity, notificationSlotCount, showNotificationCards),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun dockSizeSummaryText(
    capacity: Int,
    notificationSlotCount: Int,
    showNotificationCards: Boolean,
): String =
    if (showNotificationCards) {
        val total = capacity + notificationSlotCount
        "Shows up to $total icons: $capacity pinned, $notificationSlotCount for notifications"
    } else {
        "Shows up to $capacity pinned icons"
    }

/**
 * The ceiling on how many pinned icons show before the static side scrolls -- a setting independent
 * of notification space, though a busy notification section can still leave the static side less
 * room than this alone would draw, scrolling the rest.
 */
@Composable
private fun DockCapacitySetting(
    capacity: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Pinned icons",
    value = capacity,
    valueRange = MIN_DOCK_CAPACITY..MAX_DOCK_CAPACITY,
    valueLabel = { count -> if (count == 1) "1 icon" else "$count icons" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockCapacity(value)) },
)

/**
 * How many notification icons show at once before that section scrolls, independent of how many
 * pinned icons the dock has. Fewer notifications than this shrinks the section; more scrolls.
 */
@Composable
private fun DockNotificationSlotCountSetting(
    slotCount: Int,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Notification space",
    value = slotCount,
    valueRange = MIN_DOCK_NOTIFICATION_SLOT_COUNT..MAX_DOCK_NOTIFICATION_SLOT_COUNT,
    valueLabel = { count -> if (count == 1) "1 icon" else "$count icons" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockNotificationSlotCount(value)) },
)

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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Dock width",
            subtitle =
                when (sizing) {
                    DockBackgroundSizing.DYNAMIC -> "Fits dock items"
                    DockBackgroundSizing.FIXED -> "Uses available width"
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = sizing != DockBackgroundSizing.DYNAMIC,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.DYNAMIC)) },
            ) {
                SettingsButtonText(text = "Fit content")
            }
            TextButton(
                enabled = sizing != DockBackgroundSizing.FIXED,
                onClick = { onAction(LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.FIXED)) },
            ) {
                SettingsButtonText(text = "Full width")
            }
        }
    }
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
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsTextColumn(
            title = "Dock alignment",
            subtitle = "Places a content-sized dock on the home screen",
        )
        DockAlignment.entries.forEach { candidate ->
            TextButton(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { selected = candidate == alignment },
                enabled = candidate != alignment,
                onClick = { onAction(LauncherShellAction.SelectDockAlignment(candidate)) },
            ) {
                SettingsButtonText(
                    text =
                        when (candidate) {
                            DockAlignment.START -> "Start"
                            DockAlignment.CENTER -> "Center"
                            DockAlignment.END -> "End"
                        },
                )
            }
        }
    }
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Open the shelf with",
            subtitle =
                when (affordance) {
                    DockExpandAffordance.GESTURE -> "Swipe up on the dock"
                    DockExpandAffordance.BUTTON -> "A button on the dock; swiping it does nothing"
                },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                modifier = Modifier.testTag("dock-expand-affordance-${DockExpandAffordance.GESTURE.name}"),
                enabled = affordance != DockExpandAffordance.GESTURE,
                onClick = {
                    onAction(LauncherShellAction.SelectDockExpandAffordance(DockExpandAffordance.GESTURE))
                },
            ) {
                SettingsButtonText(text = "Swipe")
            }
            TextButton(
                modifier = Modifier.testTag("dock-expand-affordance-${DockExpandAffordance.BUTTON.name}"),
                enabled = affordance != DockExpandAffordance.BUTTON,
                onClick = {
                    onAction(LauncherShellAction.SelectDockExpandAffordance(DockExpandAffordance.BUTTON))
                },
            ) {
                SettingsButtonText(text = "Button")
            }
        }
    }
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
