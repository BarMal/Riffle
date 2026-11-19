package com.riffle.app.launcher.search.wider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.groupNameRes
import com.riffle.app.launcher.launcherPanelContentColor
import com.riffle.app.launcher.launcherPanelSurfaceColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.search.wider.SearchGroup

internal const val WIDER_ANSWER_PANEL_TEST_TAG = "wider-answer-panel"
internal const val WIDER_GROUPS_PANEL_TEST_TAG = "wider-groups-panel"

/** The tallest the groups' panel stands before it scrolls: the apps and the field keep their room. */
private val WiderGroupsMaxHeight = 320.dp

/** The calculator's answer on its own Thick glass: the first thing read, nearest the field in search from the Dock. */
@Composable
internal fun WiderAnswerPanel(
    model: WiderSearchModel<*>,
    modifier: Modifier = Modifier,
) {
    if (model.merged.answers.isEmpty()) return
    GlassSurface(
        modifier = modifier.fillMaxWidth().testTag(WIDER_ANSWER_PANEL_TEST_TAG),
        weight = GlassWeight.Thick,
        shape = LocalLauncherCardShape.current,
        tint = launcherPanelSurfaceColor(),
        contentColor = launcherPanelContentColor(),
    ) {
        Column {
            model.merged.answers.forEachIndexed { index, answer ->
                WiderResultRow(
                    answer,
                    WiderRowPosition(stringResource(R.string.search_group_calculator), index, 1),
                    model,
                )
            }
        }
    }
}

/**
 * The groups below the apps, on one Thick glass panel: a header per group when more than one thing has results, up to
 * the cap's worth of rows, and "Show N more" where a group is cut. [reverse] draws them for results that grow up from
 * the field (the first group and its best row nearest the apps and the field).
 */
@Composable
@Suppress("LongMethod")
internal fun WiderGroupsPanel(
    model: WiderSearchModel<*>,
    reverse: Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = model.merged.groups
    if (groups.isEmpty()) return
    val ordered = if (reverse) groups.asReversed() else groups
    GlassSurface(
        modifier = modifier.fillMaxWidth().testTag(WIDER_GROUPS_PANEL_TEST_TAG),
        weight = GlassWeight.Thick,
        shape = LocalLauncherCardShape.current,
        tint = launcherPanelSurfaceColor(),
        contentColor = launcherPanelContentColor(),
    ) {
        Column(
            modifier =
                Modifier
                    .heightIn(max = WiderGroupsMaxHeight)
                    .verticalScroll(rememberScrollState(), reverseScrolling = reverse),
        ) {
            ordered.forEach { group -> WiderGroup(group, model, reverse) }
        }
    }
}

@Composable
private fun WiderGroup(
    group: SearchGroup,
    model: WiderSearchModel<*>,
    reverse: Boolean,
) {
    val name = stringResource(group.source.groupNameRes())
    val rows = if (reverse) group.rows.asReversed() else group.rows
    Column(modifier = Modifier.semantics { isTraversalGroup = true }) {
        if (model.merged.showHeaders) WiderGroupHeader(group.source)
        if (reverse && group.hiddenCount > 0) {
            WiderShowMore(group.source, group.hiddenCount, onClick = { model.showMore(group.source) })
        }
        if (group.needsPermission && !reverse) {
            WiderAllowRow(group.source, onAllow = { model.requestAccess(group.source) })
        }
        rows.forEachIndexed { shown, result ->
            val index = if (reverse) group.rows.lastIndex - shown else shown
            WiderResultRow(result, WiderRowPosition(name, index, group.rows.size), model)
        }
        if (group.needsPermission && reverse) {
            WiderAllowRow(group.source, onAllow = { model.requestAccess(group.source) })
        }
        if (!reverse && group.hiddenCount > 0) {
            WiderShowMore(group.source, group.hiddenCount, onClick = { model.showMore(group.source) })
        }
    }
}
