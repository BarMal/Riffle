package com.riffle.app.launcher.pair

import android.content.Context
import android.widget.Toast
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter
import com.riffle.app.launcher.diagnostics.pair.AppPairExperiment
import com.riffle.app.launcher.diagnostics.pair.PairActivityHub
import com.riffle.app.launcher.diagnostics.pair.PairFlags
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.PairTarget
import com.riffle.core.domain.launcher.dock.PairApp
import com.riffle.core.domain.launcher.dock.PairLaunch
import com.riffle.core.domain.launcher.dock.PairLauncher
import com.riffle.core.domain.launcher.settings.PairRecipeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How a pair is started: the intent flags of each app's start, the wait between them, and whether both are started by
 * one `startActivities` call ([oneCall]). The one small thing to change when the device says the recipe is wrong
 * (docs/product/dock-modes-plan.md, "Pairs"); the owner picks it in Settings > About > Developer options > Pair recipe.
 * Never `MULTIPLE_TASK` (it pulled the wrong windows into a pane and left duplicate tasks) and never `CLEAR_TASK` (it
 * restarts the apps).
 */
data class PairRecipe(
    val firstFlags: Int,
    val secondFlags: Int,
    val delayMillis: Long,
    val oneCall: Boolean = false,
    val name: String = "custom",
) {
    companion object {
        /** How long the in-flight guard holds after a one-call start (the second start has no delay to hold it). */
        const val ONE_CALL_GUARD_MILLIS: Long = 1000

        fun forKind(kind: PairRecipeKind): PairRecipe =
            when (kind) {
                PairRecipeKind.ONE_CALL ->
                    PairRecipe(PairFlags.NEW_TASK, PairFlags.ADJACENT_NEW_TASK, ONE_CALL_GUARD_MILLIS, true, "ONE_CALL")
                PairRecipeKind.TWO_STARTS ->
                    PairRecipe(PairFlags.ADJACENT_NEW_TASK, PairFlags.ADJACENT_NEW_TASK, 600, false, "TWO_STARTS")
                PairRecipeKind.PLAIN_THEN_ADJACENT ->
                    PairRecipe(PairFlags.NEW_TASK, PairFlags.ADJACENT_NEW_TASK, 600, false, "PLAIN_THEN_ADJACENT")
            }

        /** The default: one `startActivities` call, each start as if by the one before it. */
        val DEFAULT = forKind(PairRecipeKind.DEFAULT)
    }
}

/** One launch as the experiment log keeps it: the recipe, the two packages' flags and whether it worked. */
data class PairLaunchRecord(
    val recipe: String,
    val first: PairStart,
    val second: PairStart,
    val error: String?,
)

/**
 * Starts a pair with the recipe [recipe] names at launch time, through [starter]. A one-call recipe is one
 * `startActivities`; the others start the first app now and the second after the recipe's delay, as a coroutine in
 * [scope] (the app's own, so it still fires when the launcher has been paused or stopped by the first app). One launch
 * is in flight at a time; a second tap meanwhile is ignored. If a start throws, [onProblem] is told which app could
 * not be opened and the other app is still opened (on its own, as a plain start). Every launch is told to [record].
 */
class AppPairLauncher(
    private val starter: () -> PairStarter,
    private val scope: CoroutineScope,
    private val onProblem: (String) -> Unit,
    private val recipe: () -> PairRecipe = { PairRecipe.DEFAULT },
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val record: (PairLaunchRecord) -> Unit = {},
) : PairLauncher {
    private var job: Job? = null

    val isInFlight: Boolean get() = job?.isActive == true

    override fun launch(pair: PairLaunch): Boolean {
        if (isInFlight) return false
        val chosen = recipe()
        job = scope.launch { run(chosen, pair.first.toTarget(), pair.second.toTarget()) }
        return true
    }

    /** Ends a launch that is waiting for its second start; the first app stays open. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    private suspend fun run(
        recipe: PairRecipe,
        first: PairTarget,
        second: PairTarget,
    ) {
        val platform = starter()
        val firstStart = PairStart(first, recipe.firstFlags)
        val secondStart = PairStart(second, recipe.secondFlags)
        if (recipe.oneCall) {
            val error = attemptAll(platform, listOf(firstStart, secondStart))
            record(PairLaunchRecord(recipe.name, firstStart, secondStart, error))
            if (error != null) {
                onProblem("Couldn't open ${first.label} and ${second.label} together")
                start(platform, PairStart(first, PairFlags.NEW_TASK), first)
                start(platform, PairStart(second, PairFlags.NEW_TASK), second)
            }
            pause(recipe.delayMillis)
            return
        }
        val firstOpened = start(platform, firstStart, first)
        if (firstOpened) pause(recipe.delayMillis)
        // With nothing to be beside, the second app opens on its own.
        val secondOpened =
            start(platform, if (firstOpened) secondStart else PairStart(second, PairFlags.NEW_TASK), second)
        record(
            PairLaunchRecord(
                recipe.name,
                firstStart,
                secondStart,
                if (firstOpened && secondOpened) null else "start failed",
            ),
        )
    }

    @Suppress("TooGenericExceptionCaught") // A failed start is a message and a log line, never a crash.
    private fun attemptAll(
        platform: PairStarter,
        starts: List<PairStart>,
    ): String? =
        try {
            platform.startAll(starts)
            null
        } catch (e: Exception) {
            e.javaClass.simpleName
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

    private fun PairApp.toTarget() = PairTarget(identity.packageName.value, identity.activityName.value, label)
}

/** The process-wide pair launcher: its scope is the app's, not an activity's. */
internal object AndroidPairLaunchers {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    fun create(
        activity: Context,
        recipe: () -> PairRecipeKind = { PairRecipeKind.DEFAULT },
    ): AppPairLauncher =
        AppPairLauncher(
            starter = { AndroidPairStarter(PairActivityHub.startContext(activity)) },
            scope = scope,
            recipe = { PairRecipe.forKind(recipe()) },
            record = { AppPairExperiment.shared.record(it) },
            onProblem = { message -> Toast.makeText(activity.applicationContext, message, Toast.LENGTH_SHORT).show() },
        )
}
