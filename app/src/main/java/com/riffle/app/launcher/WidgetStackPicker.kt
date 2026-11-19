package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.resizeConstraintsFor
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

/**
 * The widgets that can join a stack of [span]: the ones whose resize information permits it (Riffle's own by their
 * kind's limits), judged by the rule the add itself uses, so a widget listed is a widget the stack takes.
 */
internal fun List<InstalledWidgetProvider>.takingStackSpan(
    span: GridSpan,
    grid: GridDimensions,
    availableWidthDp: Int,
    availableHeightDp: Int,
): List<InstalledWidgetProvider> =
    filter { provider ->
        val constraints =
            GlanceWidgets.kindOf(provider.identity)?.resizeConstraints
                ?: provider.dimensions.resizeConstraintsFor(
                    supportsHorizontalResize = provider.supportsHorizontalResize,
                    supportsVerticalResize = provider.supportsVerticalResize,
                    grid = grid,
                    availableWidthDp = availableWidthDp,
                    availableHeightDp = availableHeightDp,
                )
        constraints.permits(span)
    }

/** The request that adds [provider] to the stack [stackItemId]. */
internal fun InstalledWidgetProvider.requestAddToStack(stackItemId: LauncherItemId) =
    requestAddWidgetAction(WidgetAddTarget.HOME_STACK).copy(targetStackId = stackItemId)
