package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutKey

// Reading and writing CardsSettings.dockPositionByLayout, which is a map rather than a single value
// so that each layout answers for itself. These live beside the setting rather than in
// LauncherSettings.kt only because that file is at detekt's TooManyFunctions ceiling; the pairing
// with stagePreferencesFor/withStagePreferences is the shape to follow for more per-layout settings.

/** The user's chosen dock edge for [layoutKey], or `null` when they have not chosen one there. */
fun CardsSettings.dockPositionFor(layoutKey: HomeLayoutKey): DockPosition? = dockPositionByLayout[layoutKey]

fun CardsSettings.withDockPosition(
    layoutKey: HomeLayoutKey,
    position: DockPosition,
): CardsSettings = copy(dockPositionByLayout = dockPositionByLayout + (layoutKey to position))
