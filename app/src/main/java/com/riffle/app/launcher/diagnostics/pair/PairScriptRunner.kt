package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the scripted run knows about one of its launches; written into the entry and printed in the log. */
data class PairScriptInfo(
    /** Identifies the run (its start time), so a second run's entries are not mixed with the first's. */
    val runId: Long,
    /** 1-based step number. */
    val step: Int,
    val total: Int,
    val firstOpen: Boolean,
    val secondOpen: Boolean,
    /** What starting the launcher's Home activity did: `ok` or `refused: ...`; null before the attempt. */
    val returnAttempt: String? = null,
    /** How the launcher came back (see the docs); null until it has, or when it never left. */
    val returned: String? = null,
    val notes: List<String> = emptyList(),
)

/** Where the script stands, for the page. */
sealed interface PairScriptState {
    object Idle : PairScriptState

    /** The run did not start because these packages have no launch intent. */
    data class Missing(
        val packages: List<String>,
    ) : PairScriptState

    /** [step] is 1-based. */
    data class Running(
        val step: Int,
        val total: Int,
        val name: String,
    ) : PairScriptState

    data class Finished(
        val steps: Int,
    ) : PairScriptState

    data class Stopped(
        val step: Int,
        val total: Int,
    ) : PairScriptState
}

/** Whether the launcher's own activity is in the RESUMED state. */
interface PairScriptLifecycle {
    val resumed: StateFlow<Boolean>
}

fun interface PairScriptToast {
    fun show(text: String)
}

fun interface PairScriptLog {
    fun update(
        entryId: Long,
        change: (PairScriptInfo) -> PairScriptInfo,
    )
}

/** The platform side of a run: finding the two apps, firing a step, and trying to get back to the launcher. */
interface PairScriptLauncher {
    /** The launchable target of [packageName], or null when it has no launch intent. */
    fun resolve(packageName: String): PairTarget?

    /** Launches [step] and returns the id of the log entry it wrote; [info] goes into that entry. */
    suspend fun fire(
        step: PairScriptStep,
        info: PairScriptInfo,
        first: PairTarget,
        second: PairTarget,
    ): Long

    /** Starts the launcher's own Home activity; throws what the system throws when it refuses. */
    fun returnToLauncher()
}

/**
 * Runs [PairScript.steps] one after another. A step fires only while the launcher is resumed and has been for
 * [PairScript.SETTLE_MS]; after it, the runner holds for [PairScript.DWELL_MS] so the screen recording shows the
 * result, tries to start the launcher's Home activity, and goes on when the launcher is next resumed, whether that
 * attempt worked or the owner swiped Home. There is no timeout: only [stop] ends a run early. The run is one coroutine
 * in [scope] (the app's own scope, so it outlives the Settings page being left); every wait in it is a flow wait or a
 * `delay`, so cancelling it cancels whatever is pending. All calls are expected on the main thread.
 */
class PairScriptRunner(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val lifecycle: PairScriptLifecycle,
    private val toast: PairScriptToast,
    private val log: PairScriptLog,
    private val steps: List<PairScriptStep> = PairScript.steps,
) {
    private val mutableState = MutableStateFlow<PairScriptState>(PairScriptState.Idle)
    private var job: Job? = null

    val state: StateFlow<PairScriptState> = mutableState

    /**
     * Starts a run over the two packages. Returns false, and starts nothing, when a run is under way; when either
     * package cannot be launched the state says which and nothing is started either.
     */
    fun start(
        launcher: PairScriptLauncher,
        firstPackage: String,
        secondPackage: String,
    ): Boolean {
        if (mutableState.value is PairScriptState.Running) return false
        val first = launcher.resolve(firstPackage)
        val second = launcher.resolve(secondPackage)
        val ready = first != null && second != null
        if (first != null && second != null) {
            mutableState.value = PairScriptState.Running(1, steps.size, steps.first().name)
            val runId = clock()
            job = scope.launch { run(launcher, first, second, runId) }
        } else {
            val missing = listOfNotNull(firstPackage.takeIf { first == null }, secondPackage.takeIf { second == null })
            mutableState.value = PairScriptState.Missing(missing)
        }
        return ready
    }

    /** Ends the run: pending waits are cancelled and no further step fires. */
    fun stop() {
        val running = mutableState.value as? PairScriptState.Running ?: return
        job?.cancel()
        job = null
        mutableState.value = PairScriptState.Stopped(running.step, running.total)
    }

    private suspend fun run(
        launcher: PairScriptLauncher,
        first: PairTarget,
        second: PairTarget,
        runId: Long,
    ) {
        steps.forEachIndexed { index, step ->
            val number = index + 1
            mutableState.value = PairScriptState.Running(number, steps.size, step.name)
            awaitSettled()
            toast.show("Step $number of ${steps.size}: ${step.name}")
            val info = PairScriptInfo(runId, number, steps.size, step.bothOpen, step.bothOpen)
            val entryId = launcher.fire(step, info, first, second)
            afterFire(launcher, entryId)
        }
        toast.show("Script finished")
        mutableState.value = PairScriptState.Finished(steps.size)
    }

    /** Waits until the launcher is resumed and has stayed resumed for the settle time. */
    private suspend fun awaitSettled() {
        while (true) {
            lifecycle.resumed.first { it }
            if (withTimeoutOrNull(PairScript.SETTLE_MS) { lifecycle.resumed.first { !it } } == null) return
        }
    }

    private suspend fun afterFire(
        launcher: PairScriptLauncher,
        entryId: Long,
    ) {
        val firedAt = clock()
        val left = withTimeoutOrNull(PairScript.DWELL_MS) { lifecycle.resumed.first { !it } } != null
        if (!left) {
            log.update(entryId) { it.copy(notes = it.notes + "launcher stayed in front") }
            return
        }
        delay((PairScript.DWELL_MS - (clock() - firedAt)).coerceAtLeast(0))
        if (lifecycle.resumed.value) {
            val text = "by the user after ${clock() - firedAt} ms (before the return attempt)"
            log.update(entryId) { it.copy(returned = text) }
            return
        }
        returnToLauncher(launcher, entryId)
    }

    @Suppress("TooGenericExceptionCaught") // A refused start is data for the log, never a crash.
    private suspend fun returnToLauncher(
        launcher: PairScriptLauncher,
        entryId: Long,
    ) {
        val error =
            try {
                launcher.returnToLauncher()
                null
            } catch (e: Exception) {
                "${e.javaClass.simpleName}: ${e.message}"
            }
        val attemptedAt = clock()
        log.update(entryId) { it.copy(returnAttempt = error?.let { e -> "refused: $e" } ?: "ok") }
        val back = withTimeoutOrNull(PairScript.RETURN_WAIT_MS) { lifecycle.resumed.first { it } } != null
        if (back) {
            val ms = clock() - attemptedAt
            val automatic = error == null && ms <= PairScript.AUTO_RETURN_MS
            val text = if (automatic) "automatically after $ms ms" else "by the user after $ms ms"
            log.update(entryId) { it.copy(returned = text) }
            return
        }
        log.update(entryId) { it.copy(returned = "not within ${PairScript.RETURN_WAIT_MS / MS_PER_SECOND} s") }
        lifecycle.resumed.first { it }
        val ms = clock() - attemptedAt
        log.update(entryId) { it.copy(returned = "${it.returned}, then by the user after $ms ms") }
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
