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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.MAX_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_DOCK_CAPACITY
import com.riffle.core.domain.launcher.home.MAX_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.home.MIN_DOCK_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_DOCK_CAPACITY
import com.riffle.core.domain.launcher.home.MIN_DOCK_CORNER_RADIUS_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_HOME_CONTROLS_SPACING_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_LIVE_APP_SLOTS
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
            liveAppSlots = dock.liveAppSlots,
        )
        DockVisibilitySetting(
            enabled = dock.isEnabled,
            onAction = onAction,
        )
        DockCapacitySetting(
            capacity = dock.capacity,
            onAction = onAction,
        )
        DockLiveAppSlotsSetting(
            slots = dock.liveAppSlots,
            notificationAccessStatus = notificationAccessStatus,
            onAction = onAction,
        )
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

/**
 * A one-line readout of the dock's pinned icons and its dynamic side, so the two settings read as one budget rather
 * than two numbers with no visible relationship.
 */
@Composable
private fun DockSizeSummary(
    capacity: Int,
    liveAppSlots: Int,
) {
    Text(
        text = dockSizeSummaryText(capacity, liveAppSlots),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun dockSizeSummaryText(
    capacity: Int,
    liveAppSlots: Int,
): String =
    when (liveAppSlots) {
        0 -> "Shows up to $capacity pinned icons, then now playing and the Stream button"
        1 -> "Shows up to $capacity pinned icons, then 1 live app, now playing and the Stream button"
        else -> "Shows up to $capacity pinned icons, then $liveAppSlots live apps, now playing and the Stream button"
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
 * How many live apps (apps with something new) the dynamic side shows, ahead of now playing and the Stream button. More
 * than this are reached in the Stream. Zero leaves the side with only now playing and the Stream button.
 */
@Composable
private fun DockLiveAppSlotsSetting(
    slots: Int,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column {
        DiscreteSettingSlider(
            title = stringResource(R.string.dock_live_app_slots_title),
            value = slots,
            valueRange = MIN_DOCK_LIVE_APP_SLOTS..MAX_DOCK_LIVE_APP_SLOTS,
            valueLabel = { count -> dockLiveAppSlotsLabel(count) },
            onValueChange = { value ->
                dockLiveAppSlotsActions(
                    slots = value,
                    wasSlots = slots,
                    notificationAccessStatus = notificationAccessStatus,
                ).forEach(onAction)
            },
        )
        Text(
            text = dockLiveAppSlotsSubtitle(slots, notificationAccessStatus),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun dockLiveAppSlotsLabel(count: Int): String =
    when (count) {
        0 -> "None"
        1 -> "1 app"
        else -> "$count apps"
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
                    DockBackgroundSizing.FIXED -> "Uses available width, showing more icons before scrolling"
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

/**
 * The actions for moving the live app slots to [slots]: the edit, and the notification access request when live apps
 * are turned on (from none) without the access they are read from.
 */
internal fun dockLiveAppSlotsActions(
    slots: Int,
    wasSlots: Int,
    notificationAccessStatus: NotificationAccessStatus,
): List<LauncherShellAction> =
    buildList {
        add(LauncherShellAction.SelectDockLiveAppSlots(slots))
        if (slots > 0 && wasSlots == 0 && notificationAccessStatus != NotificationAccessStatus.GRANTED) {
            add(LauncherShellAction.RequestNotificationAccess)
        }
    }

internal fun dockLiveAppSlotsSubtitle(
    slots: Int,
    notificationAccessStatus: NotificationAccessStatus,
): String {
    if (slots == 0) {
        return "The dock's second side shows only now playing and the Stream button"
    }

    return when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED -> "Apps with something new sit beside your pinned ones and open the Stream"
        NotificationAccessStatus.NOT_GRANTED -> "Live apps are on, but notification access is not allowed"
        NotificationAccessStatus.REVOKED -> "Live apps are on, but notification access was revoked"
        NotificationAccessStatus.UNKNOWN -> "Live apps are on, but notification access has not been checked"
    }
}
