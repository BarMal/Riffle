package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge

/**
 * What the strip holds: the contextual mode's one row, the static side alone, or the static side, a rule and the
 * dynamic section.
 */
@Composable
@Suppress("LongParameterList")
internal fun DockStripBody(
    dock: DockModel,
    surfaceMetrics: DockSurfaceMetrics,
    showDynamicSection: Boolean,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
    position: DockPosition,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview?,
    dynamicEntries: List<DockDynamicEntry>,
    onDynamicEntryDelegated: (String) -> Unit,
) {
    val runsHorizontally = position.isHorizontalEdge
    val staticSide: @Composable (suppressEndFade: Boolean) -> Unit = { suppressEndFade ->
        if (surfaceMetrics.renderedSlotCount > 0 && surfaceMetrics.contentViewportMainAxisDp > 0) {
            DockSlotStrip(
                dock = dock,
                renderedSlotCount = surfaceMetrics.renderedSlotCount,
                contentViewportMainAxisDp = surfaceMetrics.contentViewportMainAxisDp,
                slotMetrics = surfaceMetrics.slotMetrics,
                isEditing = isEditing,
                presentation = presentation,
                appIconLoader = appIconLoader,
                position = position,
                widgetPickerDockPreview = widgetPickerDockPreview,
                suppressEndFade = suppressEndFade,
            )
        }
    }
    val contextualRun = surfaceMetrics.contextualRun
    // Nothing dynamic to show is the common case and stays exactly as it was: one strip,
    // centred, with no arrangement wrapped around it to shift it by a fraction of a pixel.
    if (contextualRun != null) {
        DockContextualRow(
            dock = dock,
            surfaceMetrics = surfaceMetrics,
            run = contextualRun,
            isEditing = isEditing,
            presentation = presentation,
            appIconLoader = appIconLoader,
            position = position,
            widgetPickerDockPreview = widgetPickerDockPreview,
            dynamicEntries = dynamicEntries,
            onDynamicEntryDelegated = onDynamicEntryDelegated,
        )
    } else if (!showDynamicSection) {
        staticSide(false)
    } else {
        DockSectionRun(runsHorizontally = runsHorizontally) {
            staticSide(true)
            DockSectionDivider(runsHorizontally = runsHorizontally)
            DockDynamicSection(
                entries = dynamicEntries,
                slotMetrics = surfaceMetrics.slotMetrics,
                mainAxisDp = surfaceMetrics.dynamicSectionMainAxisDp,
                runsHorizontally = runsHorizontally,
                appIconLoader = appIconLoader,
                interactions = presentation.interactions,
                onEntryDelegated = onDynamicEntryDelegated,
                suppressStartFade = true,
            )
        }
    }
}

/** The dock's two sections laid end to end, whichever way the dock runs. */
@Composable
internal fun DockSectionRun(
    runsHorizontally: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (runsHorizontally) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/**
 * The rule between the static side and the dynamic one.
 *
 * A visible seam on purpose. The two sides answer to different things -- one to what the user
 * pinned, one to what has just arrived -- and running them together would make the dock look like
 * it rearranges itself.
 */
@Composable
internal fun DockSectionDivider(runsHorizontally: Boolean) {
    val thickness = DOCK_SECTION_DIVIDER_MAIN_AXIS_DP.dp
    Box(
        modifier =
            if (runsHorizontally) {
                Modifier.width(thickness).fillMaxHeight()
            } else {
                Modifier.height(thickness).fillMaxWidth()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .then(
                        if (runsHorizontally) {
                            Modifier.width(DOCK_SECTION_DIVIDER_RULE_DP.dp).fillMaxHeight()
                        } else {
                            Modifier.height(DOCK_SECTION_DIVIDER_RULE_DP.dp).fillMaxWidth()
                        },
                    )
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DOCK_SECTION_DIVIDER_ALPHA)),
        )
    }
}

private const val DOCK_SECTION_DIVIDER_RULE_DP = 1
private const val DOCK_SECTION_DIVIDER_ALPHA = 0.3f
