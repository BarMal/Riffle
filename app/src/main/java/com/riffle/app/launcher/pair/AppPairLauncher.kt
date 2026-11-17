package com.riffle.app.launcher.pair

import android.content.Context
import android.widget.Toast
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter
import com.riffle.app.launcher.diagnostics.pair.PairActivityHub
import com.riffle.app.launcher.diagnostics.pair.PairFlags
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.PairTarget
import com.riffle.core.domain.launcher.dock.PairLauncher
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.AppShortcutItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How a pair is started: the intent flags of each app's start and the wait between them. The one small thing to change
 * when the device says the recipe is wrong (docs/product/dock-modes-plan.md, App pairs: the recipe and its evidence).
 * Never `MULTIPLE_TASK` (it pulled the wrong windows into a pane and left duplicate tasks) and never `CLEAR_TASK` (it
 * restarts the apps).
 */
data class PairRecipe(
    val firstFlags: Int,
    val secondFlags: Int,
    val delayMillis: Long,
) {
    companion object {
        /** "Reuse both": each app is started adjacent, in its existing task, 600 ms apart. */
        val DEFAULT =
            PairRecipe(
                firstFlags = PairFlags.ADJACENT_NEW_TASK,
                secondFlags = PairFlags.ADJACENT_NEW_TASK,
                delayMillis = 600,
            )
    }
}

/**
 * Starts a pair with [recipe] through [starter]: the first app now, the second after the recipe's delay. The second
 * start is a coroutine in [scope] (the app's own, so it still fires when the launcher has been paused or stopped by
 * the first app); one launch is in flight at a time, and a second tap meanwhile is ignored. If a start throws,
 * [onProblem] is told which app could not be opened and the other app is still opened (on its own, as a plain start,
 * when it is the first that failed).
 */
class AppPairLauncher(
    private val starter: () -> PairStarter,
    private val scope: CoroutineScope,
    private val onProblem: (String) -> Unit,
    private val recipe: PairRecipe = PairRecipe.DEFAULT,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : PairLauncher {
    private var job: Job? = null

    val isInFlight: Boolean get() = job?.isActive == true

    override fun launch(pair: AppPairItem): Boolean {
        if (isInFlight) return false
        job = scope.launch { run(pair.first.toTarget(), pair.second.toTarget()) }
        return true
    }

    /** Ends a launch that is waiting for its second start; the first app stays open. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun run(
        first: PairTarget,
        second: PairTarget,
    ) {
        val platform = starter()
        val firstOpened = start(platform, PairStart(first, recipe.firstFlags), first)
        if (firstOpened) pause(recipe.delayMillis)
        // With nothing to be beside, the second app opens on its own.
        start(platform, PairStart(second, if (firstOpened) recipe.secondFlags else PairFlags.NEW_TASK), second)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // A failed start is a message, never a crash.
    private fun start(
        platform: PairStarter,
        start: PairStart,
        target: PairTarget,
    ): Boolean =
        try {
            platform.start(start)
            true
        } catch (e: Exception) {
            onProblem("Couldn't open ${target.label}")
            false
        }

    private fun AppShortcutItem.toTarget() =
        PairTarget(appIdentity.packageName.value, appIdentity.activityName.value, label)
}

/** The process-wide pair launcher: its scope is the app's, not an activity's. */
internal object AndroidPairLaunchers {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    fun create(activity: Context): AppPairLauncher =
        AppPairLauncher(
            starter = { AndroidPairStarter(PairActivityHub.startContext(activity)) },
            scope = scope,
            onProblem = { message -> Toast.makeText(activity.applicationContext, message, Toast.LENGTH_SHORT).show() },
        )
}
