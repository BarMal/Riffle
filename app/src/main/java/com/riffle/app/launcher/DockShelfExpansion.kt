package com.riffle.app.launcher

/**
 * The switch for dock shelf expansion (dock-pull revision, Decision 7): disabled for now, to be
 * revisited once the dock pull owns the gesture away from the dock's edge.
 *
 * Off, no dock can open its shelf -- by swipe or by button -- whatever its per-layout
 * [com.riffle.core.domain.launcher.home.DockModel.isExpandable] setting says, and a swipe away from
 * the dock edge that the shelf used to claim does nothing. The shelf code stays in place behind it.
 * Only tests of that dormant code turn it on (and back off).
 */
internal object DockShelfExpansion {
    @Volatile
    var enabled: Boolean = false
}
