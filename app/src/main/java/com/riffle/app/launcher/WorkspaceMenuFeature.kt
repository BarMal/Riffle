package com.riffle.app.launcher

/**
 * The switch for the dock's workspace menu (#1351): off by default.
 *
 * Off, no workspace menu surface is composed, no dock affordance or accessibility action for it is
 * added, and dock pull, dock shelf and standard launcher behaviour are exactly as before. It is
 * independent of [DockShelfExpansion]: the menu revives the shelf's composables, not its gesture.
 * Only tests, previews and a development build turn it on.
 */
internal object WorkspaceMenuFeature {
    @Volatile
    var enabled: Boolean = false
}
