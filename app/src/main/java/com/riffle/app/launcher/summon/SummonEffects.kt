package com.riffle.app.launcher.summon

import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.summon.SummonEffect
import kotlin.math.abs

/** What the shell does for a gesture effect, if anything; the ring's own drawing reacts to the phase instead. */
internal fun summonActionFor(
    effect: SummonEffect,
    model: SummonRingModel,
): LauncherShellAction? =
    when (effect) {
        is SummonEffect.Launch ->
            when (val entry = model.entry(effect.path, effect.index)) {
                is SummonEntry.App -> LauncherShellAction.LaunchApp(entry.identity)
                is SummonEntry.Glance -> LauncherShellAction.OpenStream
                else -> null
            }
        is SummonEffect.LaunchFavourite ->
            model.favourites.getOrNull(effect.index)?.let {
                LauncherShellAction.LaunchApp(it.identity)
            }
        is SummonEffect.PassedThrough -> swipeAction(effect)
        else -> null
    }

/**
 * A swipe that began before the ring armed keeps Home's two most-used defaults: down for search, up for the Library.
 * Sideways swipes have no pages to turn here, so they do nothing.
 */
private fun swipeAction(swipe: SummonEffect.PassedThrough): LauncherShellAction? {
    val dx = swipe.to.x - swipe.from.x
    val dy = swipe.to.y - swipe.from.y
    return when {
        abs(dy) <= abs(dx) -> null
        dy > 0 -> LauncherShellAction.OpenSearch
        else -> LauncherShellAction.OpenAppDrawer
    }
}

/** The haptic for an effect: a soft start when the ring arms, ticks while aiming, a confirm on commit. */
internal fun summonHapticFor(effect: SummonEffect): LauncherHapticEvent? =
    when (effect) {
        SummonEffect.Armed -> LauncherHapticEvent.GESTURE_START
        is SummonEffect.Aimed, is SummonEffect.AimedDock, is SummonEffect.Previewed -> LauncherHapticEvent.DETENT
        is SummonEffect.OpenedFolder,
        is SummonEffect.WentBack,
        is SummonEffect.WentLive,
        is SummonEffect.ReachedReadout,
        SummonEffect.FavouritesShown,
        -> LauncherHapticEvent.BOUNDARY
        is SummonEffect.Launch, is SummonEffect.LaunchFavourite, is SummonEffect.KeepCard -> LauncherHapticEvent.COMMIT
        SummonEffect.MenuRequested, is SummonEffect.ReleasedOn -> LauncherHapticEvent.GESTURE_END
        else -> null
    }
