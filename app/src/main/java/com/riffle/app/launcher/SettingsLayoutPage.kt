package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.PageIndicatorPlacement
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings

/** The test tag of one posture's option: `layout-<posture>-<setting>-<option>`, in lower case. */
internal fun layoutOptionTestTag(
    posture: LayoutPosture,
    setting: String,
    option: String,
): String = "layout-${posture.name.lowercase()}-$setting-${option.lowercase()}"

/** The Dock edges the layout page offers: the three the Dock can stand on. */
internal val layoutDockEdges: List<DockPosition> =
    listOf(DockPosition.BOTTOM, DockPosition.LEFT, DockPosition.RIGHT)

/**
 * Settings > Layout (ADR 0003, Amendment 2): the layout is a setting per posture. A Folded section and an Unfolded
 * section each hold the Dock edge, which way the app pages scroll, where the page dots sit and whether dragging along
 * them scrolls quickly. Every one is a plain choice or switch, so each has a route that needs no gesture.
 */
@Composable
internal fun SettingsLayoutPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = stringResource(R.string.settings_layout_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LayoutPosture.entries.forEach { posture ->
        SettingsSection(
            title =
                stringResource(
                    if (posture == LayoutPosture.FOLDED) {
                        R.string.settings_layout_section_folded
                    } else {
                        R.string.settings_layout_section_unfolded
                    },
                ),
        ) {
            SettingsPostureLayoutRows(
                posture = posture,
                dockEdge = state.postureDockEdges.getValue(posture),
                settings = state.settings.layout.forPosture(posture),
                onAction = onAction,
            )
        }
    }
}

private enum class LayoutRow { DOCK_EDGE, PAGE_SCROLL, INDICATOR, QUICK_SCROLL, APPS_BEHIND }

/** The setting a posture's row is, for search to scroll to it. */
private fun layoutSettingKey(
    posture: LayoutPosture,
    row: LayoutRow,
): SettingKey =
    when (posture) {
        LayoutPosture.FOLDED ->
            when (row) {
                LayoutRow.DOCK_EDGE -> SettingKey.LAYOUT_FOLDED_DOCK_EDGE
                LayoutRow.PAGE_SCROLL -> SettingKey.LAYOUT_FOLDED_PAGE_SCROLL
                LayoutRow.INDICATOR -> SettingKey.LAYOUT_FOLDED_INDICATOR
                LayoutRow.QUICK_SCROLL -> SettingKey.LAYOUT_FOLDED_QUICK_SCROLL
                LayoutRow.APPS_BEHIND -> SettingKey.LAYOUT_FOLDED_APP_PEEK_THROUGH
            }

        LayoutPosture.UNFOLDED ->
            when (row) {
                LayoutRow.DOCK_EDGE -> SettingKey.LAYOUT_UNFOLDED_DOCK_EDGE
                LayoutRow.PAGE_SCROLL -> SettingKey.LAYOUT_UNFOLDED_PAGE_SCROLL
                LayoutRow.INDICATOR -> SettingKey.LAYOUT_UNFOLDED_INDICATOR
                LayoutRow.QUICK_SCROLL -> SettingKey.LAYOUT_UNFOLDED_QUICK_SCROLL
                LayoutRow.APPS_BEHIND -> SettingKey.LAYOUT_UNFOLDED_APP_PEEK_THROUGH
            }
    }

@Composable
@Suppress("LongMethod")
private fun SettingsPostureLayoutRows(
    posture: LayoutPosture,
    dockEdge: DockPosition,
    settings: PostureLayoutSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    // The Dock page's own row: the same label, subtitle and options, the same stored value.
    SettingsDockPositionRow(
        position = dockEdge,
        placeablePositions = layoutDockEdges,
        onSelect = { edge -> onAction(LauncherShellAction.SelectPostureDockEdge(posture, edge)) },
        modifier = Modifier.settingAnchor(layoutSettingKey(posture, LayoutRow.DOCK_EDGE)),
        optionTag = { edge -> layoutOptionTestTag(posture, "dock", edge.name) },
    )
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(layoutSettingKey(posture, LayoutRow.PAGE_SCROLL)),
        title = stringResource(R.string.settings_layout_page_scroll_title),
        subtitle = stringResource(R.string.settings_layout_page_scroll_subtitle),
        options = PageScrollDirection.entries,
        selected = settings.pageScroll,
        onSelect = { direction -> onAction(LauncherShellAction.SelectPageScroll(posture, direction)) },
        label = { direction ->
            stringResource(
                if (direction == PageScrollDirection.HORIZONTAL) {
                    R.string.settings_layout_scroll_horizontal
                } else {
                    R.string.settings_layout_scroll_vertical
                },
            )
        },
        optionTag = { direction -> layoutOptionTestTag(posture, "scroll", direction.name) },
    )
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(layoutSettingKey(posture, LayoutRow.INDICATOR)),
        title = stringResource(R.string.settings_layout_indicator_title),
        subtitle = stringResource(R.string.settings_layout_indicator_subtitle),
        options = PageIndicatorPlacement.entries,
        selected = settings.indicator,
        onSelect = { placement -> onAction(LauncherShellAction.SelectPageIndicator(posture, placement)) },
        label = { placement ->
            stringResource(
                if (placement == PageIndicatorPlacement.BESIDE_DOCK) {
                    R.string.settings_layout_indicator_beside
                } else {
                    R.string.settings_layout_indicator_off
                },
            )
        },
        optionTag = { placement -> layoutOptionTestTag(posture, "indicator", placement.name) },
    )
    SettingsSwitchRow(
        modifier =
            Modifier
                .settingAnchor(layoutSettingKey(posture, LayoutRow.QUICK_SCROLL))
                .testTag(layoutOptionTestTag(posture, "quick", "scroll")),
        title = stringResource(R.string.settings_layout_quick_scroll_title),
        subtitle =
            stringResource(
                if (settings.showsDots) {
                    R.string.settings_layout_quick_scroll_subtitle
                } else {
                    R.string.settings_layout_quick_scroll_off_subtitle
                },
            ),
        checked = settings.quickScroll && settings.showsDots,
        enabled = settings.showsDots,
        onCheckedChange = { on -> onAction(LauncherShellAction.SetQuickScroll(posture, on)) },
    )
    // The same value, label and subtitle Settings > Stream > Cards shows for this posture.
    SettingsAppsBehindCardsRow(
        posture = posture,
        on = settings.appPeekThrough,
        onAction = onAction,
        modifier = Modifier.settingAnchor(layoutSettingKey(posture, LayoutRow.APPS_BEHIND)),
        tag = layoutOptionTestTag(posture, "peek", "through"),
    )
}

/**
 * "Show apps behind the cards" for one [posture]: the one row both Settings > Layout and Settings > Stream > Cards
 * draw, so the two entry points read identically and, being one stored value, always agree.
 */
@Composable
internal fun SettingsAppsBehindCardsRow(
    posture: LayoutPosture,
    on: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
    tag: String,
) {
    SettingsSwitchRow(
        modifier = modifier.testTag(tag),
        title = stringResource(R.string.settings_layout_peek_through_title),
        subtitle =
            stringResource(
                if (on) R.string.settings_layout_peek_through_on else R.string.settings_layout_peek_through_off,
            ),
        checked = on,
        onCheckedChange = { enabled -> onAction(LauncherShellAction.SetAppPeekThrough(posture, enabled)) },
    )
}
