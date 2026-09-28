package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

/**
 * Cards' dock-edge companion, redesigned from two separately-shaped floating "pills" (an identity
 * pill and a pin/overflow capsule -- see #1301/#1306) into one card-shaped panel, per the reported
 * UX complaint that "the UX should be cards and not pills": a vertical dock especially made two
 * independently-sized floating pieces read as visual clutter overlapping the rest of the chrome.
 * #1306 patched the immediate overlap bugs (an unbounded-width label, an action row that could grow
 * past its own bounds) without changing that two-piece shape; this composable is the follow-up that
 * actually replaces it, so it renders that prior patch moot rather than building on it.
 *
 * Everything the two pills used to expose -- the app icon, the "AppName · N cards" identity label
 * (with its accessibility semantics and stage-navigation custom actions), the pin toggle, and the
 * overflow menu -- lives inside this one [GlassSurface], as one coherent card. [position] only picks
 * whether that content lays out as a single row (horizontal dock: icon+label read left-to-right
 * beside the dock's own icon run) or as two stacked rows (vertical dock: identity above,
 * pin/overflow below, so the card is never wider than the strip it sits beside). [DockEdgeCompanionSlot]
 * still does the actual placement beside the dock's measured bounds -- this composable knows nothing
 * about where on screen it ends up, same as the header it replaces.
 *
 * The panel bounds its own width and height directly ([CARDS_DOCK_EDGE_CARD_PANEL_MAX_WIDTH_DP],
 * [CARDS_DOCK_EDGE_CARD_PANEL_MAX_HEIGHT_DP]) rather than relying on two independently-sized pills
 * each staying small by accident -- a single card is easier to bound sensibly since this composable
 * now owns its whole internal layout.
 */
@Composable
internal fun CardsDockEdgeCardPanel(
    selectedStage: AppStage?,
    allNotificationsSelected: Boolean,
    stages: List<AppStage>,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    position: DockPosition,
    onAction: (LauncherShellAction) -> Unit,
) {
    // selectedStage stays the last real selection while the merged page is showing (so leaving
    // "All" returns to it), so "a real stage is showing" needs its own explicit gate.
    val shownStage = selectedStage?.takeUnless { allNotificationsSelected }
    val label =
        when {
            allNotificationsSelected -> CARDS_ALL_ENTRY_LABEL
            shownStage != null -> stageLabel(shownStage.id, state)
            else -> "Cards"
        }
    val summary = adaptiveStageHeaderSummary(shownStage, allNotificationsSelected, stages)
    val shownApp = shownStage?.let { stage -> state.installedAppsByStageId[stage.id] }

    val identity: @Composable () -> Unit = {
        CardsDockEdgeCardPanelIdentity(
            label = label,
            summary = summary,
            shownApp = shownApp,
            allNotificationsSelected = allNotificationsSelected,
            shownStage = shownStage,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
    }
    val controls: @Composable () -> Unit = {
        CardsDockEdgeCardPanelControls(shownStage = shownStage, shownApp = shownApp, state = state, onAction = onAction)
    }

    GlassSurface(
        modifier =
            Modifier
                .testTag(CARDS_DOCK_EDGE_CARD_PANEL_TEST_TAG)
                .widthIn(max = CARDS_DOCK_EDGE_CARD_PANEL_MAX_WIDTH_DP.dp)
                .heightIn(max = CARDS_DOCK_EDGE_CARD_PANEL_MAX_HEIGHT_DP.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        if (position.isHorizontalEdge) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // controls() (pin + overflow) is measured at its own natural size first -- weighting
                // identity() instead of it guarantees the pin/overflow touch targets are never
                // squeezed out of place by a long "AppName · N cards" label; identity()'s own Text
                // already ellipsizes into whatever width that leaves it.
                Box(modifier = Modifier.weight(1f, fill = false)) { identity() }
                controls()
            }
        } else {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                identity()
                controls()
            }
        }
    }
}

/**
 * Tags the panel's single [GlassSurface] container -- one node for the whole card, where the old
 * two-pill header had no equivalent single tag because there was no single element to tag.
 */
internal const val CARDS_DOCK_EDGE_CARD_PANEL_TEST_TAG = "cards-dock-edge-card-panel"

/** The panel's own width/height bounds, so one card can't reproduce the old pills' unbounded growth. */
internal const val CARDS_DOCK_EDGE_CARD_PANEL_MAX_WIDTH_DP = 260
internal const val CARDS_DOCK_EDGE_CARD_PANEL_MAX_HEIGHT_DP = 220

/**
 * The panel's identity content: [CARDS_DOCK_EDGE_CARD_PANEL_ICON_SIZE_DP] icon plus the
 * "AppName · N cards" label, carrying the same accessibility semantics the old identity pill had --
 * [contentDescription]/[stateDescription]/[liveRegion] plus stage-to-stage [customActions] --
 * moved here unchanged, just no longer wrapped in its own separate glass surface.
 */
@Composable
private fun CardsDockEdgeCardPanelIdentity(
    label: String,
    summary: String?,
    shownApp: InstalledApp?,
    allNotificationsSelected: Boolean,
    shownStage: AppStage?,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (shownApp != null) {
            LauncherAppIcon(
                identity = shownApp.identity,
                label = label,
                iconLoader = appIconLoader,
                modifier = Modifier.size(CARDS_DOCK_EDGE_CARD_PANEL_ICON_SIZE_DP.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .padding(start = if (shownApp != null) 10.dp else 0.dp)
                    .testTag(ADAPTIVE_STAGE_STAGE_HEADER_TEST_TAG)
                    .semantics {
                        contentDescription = "Cards stage: $label"
                        stateDescription =
                            when {
                                allNotificationsSelected -> "Showing every stage's notifications"
                                shownStage != null -> shownStage.adaptiveStageStageStateDescription()
                                else -> "No stage selected"
                            }
                        liveRegion = LiveRegionMode.Polite
                        // Stage-to-stage navigation for TalkBack/switch users, mirroring the
                        // "Previous card"/"Next card" CustomAccessibilityAction precedent used
                        // for intra-stack card navigation elsewhere in this file.
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Previous stage") {
                                    onAction(LauncherShellAction.SelectPreviousAppStage)
                                    true
                                },
                                CustomAccessibilityAction("Next stage") {
                                    onAction(LauncherShellAction.SelectNextAppStage)
                                    true
                                },
                            )
                    },
        ) {
            // A single compact line ("WhatsApp · 10 cards") rather than a title plus a separate
            // eyebrow line -- the card has no vertical room to spare for a two-line header,
            // especially stacked above the pin/overflow row on a vertical dock.
            Text(
                text = listOfNotNull(label, summary).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The identity content's icon slot. */
private const val CARDS_DOCK_EDGE_CARD_PANEL_ICON_SIZE_DP = 28

/** The pin-toggle + overflow controls, unchanged in behaviour from the capsule they replace. */
@Composable
private fun CardsDockEdgeCardPanelControls(
    shownStage: AppStage?,
    shownApp: InstalledApp?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Pin appears once, as a toggle, and only for a real stage -- "All" is not a stage.
        if (shownStage != null) {
            IconToggleButton(
                checked = shownStage.isPinned,
                onCheckedChange = { onAction(LauncherShellAction.ToggleAppStagePinned(shownStage.id)) },
                modifier =
                    Modifier.semantics {
                        contentDescription = ADAPTIVE_STAGE_PIN_TOGGLE_LABEL
                        stateDescription = if (shownStage.isPinned) "Pinned" else "Not pinned"
                    },
            ) {
                Icon(
                    imageVector = if (shownStage.isPinned) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                )
            }
        }
        CardsDockEdgeCardPanelOverflowMenu(
            shownApp = shownApp,
            notificationAccessStatus = state.notificationAccessStatus,
            onAction = onAction,
        )
    }
}

/**
 * The panel's overflow: always present, whatever is showing -- a stage, "All", or nothing yet --
 * so Settings and the Cards controls are never a dead end (#1212). Before, it hid on "All" and when
 * no stage was selected, which left Cards with no path to Settings at all on a full dock.
 */
@Composable
private fun CardsDockEdgeCardPanelOverflowMenu(
    shownApp: InstalledApp?,
    notificationAccessStatus: NotificationAccessStatus,
    onAction: (LauncherShellAction) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val requestAddStage = LocalAdaptiveStageAddStageRequest.current
    val choose: (() -> Unit) -> Unit = { choice ->
        expanded = false
        choice()
    }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = ADAPTIVE_STAGE_OVERFLOW_LABEL },
        ) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }
        RiffleContextMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            shownApp?.let { app ->
                DropdownMenuItem(
                    text = { Text("Open ${app.label}") },
                    onClick = { choose { onAction(LauncherShellAction.LaunchApp(app.identity)) } },
                )
                DropdownMenuItem(
                    text = { Text("App info") },
                    onClick = { choose { onAction(LauncherShellAction.OpenAppInfo(app.identity)) } },
                )
            }
            DropdownMenuItem(
                text = { Text("Add stage") },
                onClick = { choose(requestAddStage) },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (notificationAccessStatus == NotificationAccessStatus.GRANTED) {
                            "Notification access"
                        } else {
                            "Allow notification access"
                        },
                    )
                },
                onClick = { choose { onAction(LauncherShellAction.RequestNotificationAccess) } },
            )
            DropdownMenuItem(
                text = { Text("Cards appearance") },
                onClick = {
                    choose { onAction(LauncherShellAction.OpenSettingsPage(SettingsPage.ADAPTIVE_STAGE_APPEARANCE)) }
                },
            )
            DropdownMenuItem(
                text = { Text("Home mode") },
                onClick = { choose { onAction(LauncherShellAction.OpenSettingsPage(SettingsPage.LAYOUT)) } },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = { choose { onAction(LauncherShellAction.OpenSettings) } },
            )
        }
    }
}

/** A short count for the panel's identity line, or null when there is nothing to say. */
private fun adaptiveStageHeaderSummary(
    shownStage: AppStage?,
    allNotificationsSelected: Boolean,
    stages: List<AppStage>,
): String? {
    val count =
        when {
            allNotificationsSelected -> stages.sumOf { stage -> stage.content.size }
            shownStage != null -> shownStage.content.size
            else -> return null
        }
    return when {
        count > 0 -> "$count ${if (count == 1) "card" else "cards"}"
        shownStage?.isPinned == true -> "Pinned, nothing new"
        else -> "Nothing new"
    }
}
