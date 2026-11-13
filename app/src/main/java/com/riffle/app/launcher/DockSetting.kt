@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
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
        DockPositionSetting(
            position = dock.position,
            // One dock shared by every mode (#1205), so only the edges every mode can draw it on.
            placeablePositions = sharedDockPositions,
            onAction = onAction,
        )
        DockIconSizeSetting(
            sizeDp = dock.iconSizeDp,
            onAction = onAction,
        )
        DockCapacitySetting(
            capacity = dock.capacity,
            onAction = onAction,
        )
        DockBackgroundSizingSetting(
            sizing = dock.backgroundSizing,
            onAction = onAction,
        )
        DockAlignmentSetting(
            alignment = dock.alignment,
            onAction = onAction,
        )
        DockCornerRadiusSetting(
            cornerRadiusDp = dock.cornerRadiusDp,
            onAction = onAction,
        )
        DockBackgroundAlphaSetting(
            alphaPercent = dock.backgroundAlphaPercent,
            onAction = onAction,
        )
        DockHomeControlsSpacingSetting(
            spacingDp = dock.homeControlsSpacingDp,
            onAction = onAction,
        )
    }
}

/** The dock's live apps: its own section on the Dock page, apart from the strip's look. */
@Composable
internal fun DockDynamicSideSetting(
    dock: DockModel,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    DockLiveAppSlotsSetting(
        slots = dock.liveAppSlots,
        notificationAccessStatus = notificationAccessStatus,
        onAction = onAction,
    )
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
        0 -> "Shows up to $capacity pinned icons, then now playing"
        1 -> "Shows up to $capacity pinned icons, then 1 live app and now playing"
        else -> "Shows up to $capacity pinned icons, then $liveAppSlots live apps and now playing"
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
    title = SettingKey.DOCK_PINNED_ICONS.label(),
    modifier = Modifier.settingAnchor(SettingKey.DOCK_PINNED_ICONS),
    value = capacity,
    valueRange = MIN_DOCK_CAPACITY..MAX_DOCK_CAPACITY,
    valueLabel = { count -> if (count == 1) "1 icon" else "$count icons" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectDockCapacity(value)) },
)

/**
 * How many live apps (apps with something new) the dynamic side shows, ahead of now playing. More
 * than this are reached in the Stream. Zero leaves the side with only now playing.
 */
@Composable
private fun DockLiveAppSlotsSetting(
    slots: Int,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column {
        DiscreteSettingSlider(
            modifier = Modifier.settingAnchor(SettingKey.DOCK_LIVE_APPS),
            title = SettingKey.DOCK_LIVE_APPS.label(),
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
    title = SettingKey.DOCK_ICON_SIZE.label(),
    modifier = Modifier.settingAnchor(SettingKey.DOCK_ICON_SIZE),
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
    title = SettingKey.DOCK_BACKGROUND.label(),
    modifier = Modifier.settingAnchor(SettingKey.DOCK_BACKGROUND),
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
        modifier = Modifier.settingAnchor(SettingKey.DOCK_WIDTH),
        title = SettingKey.DOCK_WIDTH.label(),
        subtitle =
            when (sizing) {
                DockBackgroundSizing.DYNAMIC -> "Fits dock items"
                DockBackgroundSizing.FIXED -> "Uses available width, showing more icons before scrolling"
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
    title = SettingKey.DOCK_CORNER_RADIUS.label(),
    modifier = Modifier.settingAnchor(SettingKey.DOCK_CORNER_RADIUS),
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
    title = SettingKey.DOCK_GRID_SPACING.label(),
    modifier = Modifier.settingAnchor(SettingKey.DOCK_GRID_SPACING),
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
        modifier = Modifier.settingAnchor(SettingKey.DOCK_ALIGNMENT),
        title = SettingKey.DOCK_ALIGNMENT.label(),
        subtitle = "Places a content-sized dock on the home screen",
        options = DockAlignment.entries,
        selected = alignment,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockAlignment(candidate)) },
        label = { candidate ->
            when (candidate) {
                DockAlignment.START -> "Start"
                DockAlignment.CENTER -> "Centre"
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
        modifier = Modifier.settingAnchor(SettingKey.DOCK_SHOW),
        title = SettingKey.DOCK_SHOW.label(),
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
private fun DockPositionSetting(
    position: DockPosition?,
    placeablePositions: List<DockPosition>,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.DOCK_POSITION),
        title = SettingKey.DOCK_POSITION.label(),
        subtitle = dockPositionSubtitle(position, placeablePositions),
        options = placeablePositions,
        selected = position?.takeIf { it in placeablePositions },
        onSelect = { candidate -> onAction(LauncherShellAction.SelectDockPosition(candidate)) },
        label = { candidate -> candidate.label() },
        optionTag = { candidate -> "dock-position-${candidate.name}" },
    )
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
        position == null -> "Bottom edge"
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
        return "The dock's second side shows only now playing"
    }

    return when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED -> "Apps with something new sit beside your pinned ones and open the Stream"
        NotificationAccessStatus.NOT_GRANTED -> "Live apps are on, but notification access is not allowed"
        NotificationAccessStatus.REVOKED -> "Live apps are on, but notification access was revoked"
        NotificationAccessStatus.UNKNOWN -> "Live apps are on, but notification access has not been checked"
    }
}
