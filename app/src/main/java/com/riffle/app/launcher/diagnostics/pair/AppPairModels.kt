package com.riffle.app.launcher.diagnostics.pair

import android.content.Intent

/*
 * The "App pair experiment" under Settings > About > Diagnostics: does launching two apps as a split-screen pair
 * work on this phone? Plain Kotlin so every method can be tested with a fake [PairStarter]; the platform side is
 * AndroidPairStarter and PairTrampolineActivity. Nothing here is a feature: it only starts activities and records what
 * the calls returned.
 */

/** One launchable app, by component. Personal-profile apps only (see AppPairPage). */
data class PairTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
)

enum class PairMethod(
    val title: String,
    val summary: String,
) {
    SEQUENTIAL(
        "Sequential",
        "Start the first app, wait, then start the second one next to it (adjacent flag).",
    ),
    TRAMPOLINE_ADJACENT_SECOND(
        "Trampoline: adjacent second",
        "A transparent activity starts the first app normally, then the second with the adjacent flag.",
    ),
    TRAMPOLINE_ADJACENT_FIRST(
        "Trampoline: adjacent first",
        "A transparent activity starts the first app with the adjacent flag, then the second normally.",
    ),
    BOTH_ADJACENT(
        "Both adjacent",
        "Start both apps back to back, each with the adjacent flag.",
    ),
    START_ACTIVITIES_ARRAY(
        "One startActivities call",
        "Start both in one startActivities call, the second with the adjacent flag.",
    ),
}

/** Intent flags the experiment uses; the values are the framework's constants. */
object PairFlags {
    const val NEW_TASK: Int = Intent.FLAG_ACTIVITY_NEW_TASK
    const val ADJACENT_NEW_TASK: Int = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK
    const val ADJACENT_NEW_MULTIPLE_TASK: Int =
        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK

    fun describe(flags: Int): String =
        buildList {
            if (flags and Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0) add("LAUNCH_ADJACENT")
            if (flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) add("NEW_TASK")
            if (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK != 0) add("MULTIPLE_TASK")
        }.ifEmpty { listOf("no flags") }.joinToString("|")
}

/** One `startActivity` the experiment makes: the app's main activity with [flags]. */
data class PairStart(
    val target: PairTarget,
    val flags: Int,
)

/** The request a trampoline activity carries, and what it does with it (see [trampolineSteps]). */
data class TrampolineRequest(
    val first: PairTarget,
    val second: PairTarget,
    val adjacentFirst: Boolean,
)

/** The two starts a trampoline makes, in order. */
fun trampolineSteps(request: TrampolineRequest): List<PairStart> =
    if (request.adjacentFirst) {
        listOf(
            PairStart(request.first, PairFlags.ADJACENT_NEW_TASK),
            PairStart(request.second, PairFlags.NEW_TASK),
        )
    } else {
        listOf(
            PairStart(request.first, PairFlags.NEW_TASK),
            PairStart(request.second, PairFlags.ADJACENT_NEW_TASK),
        )
    }

/**
 * The platform side. Each call throws what `startActivity` throws; a call that returns normally only means the system
 * accepted the request, not that a window appeared (a blocked background start is silent).
 */
interface PairStarter {
    fun start(start: PairStart)

    /** One `startActivities(Intent[])` call with an intent per start, in order. */
    fun startAll(starts: List<PairStart>)

    /** Starts the trampoline activity in a new task; it makes the [trampolineSteps] and reports back. */
    fun startTrampoline(request: TrampolineRequest)
}

/** What one call did: [error] is null when it returned normally, else the exception class and message. */
data class StartOutcome(
    val step: String,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null
}

@Suppress("TooGenericExceptionCaught") // Any failure of a start is data for the log, never a crash.
internal inline fun attempt(
    step: String,
    block: () -> Unit,
): StartOutcome =
    try {
        block()
        StartOutcome(step)
    } catch (e: Exception) {
        StartOutcome(step, "${e.javaClass.simpleName}: ${e.message}")
    }

internal fun PairStart.describe(role: String): String = "$role ${target.packageName} [${PairFlags.describe(flags)}]"

/** One way of launching a pair. Add a method by adding an implementation and a [PairMethod]. */
interface PairStrategy {
    val method: PairMethod

    /** Starts the pair and returns one outcome per call made; never throws for a failed start. */
    suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome>
}

class SequentialStrategy(
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : PairStrategy {
    override val method = PairMethod.SEQUENTIAL

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> {
        val a = PairStart(first, PairFlags.NEW_TASK)
        val b = PairStart(second, PairFlags.ADJACENT_NEW_MULTIPLE_TASK)
        val outcomes = mutableListOf(attempt(a.describe("first")) { starter.start(a) })
        if (delayMillis > 0) pause(delayMillis)
        outcomes += attempt(b.describe("second after $delayMillis ms")) { starter.start(b) }
        return outcomes
    }
}

class TrampolineStrategy(
    private val adjacentFirst: Boolean,
) : PairStrategy {
    override val method =
        if (adjacentFirst) PairMethod.TRAMPOLINE_ADJACENT_FIRST else PairMethod.TRAMPOLINE_ADJACENT_SECOND

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> =
        listOf(
            attempt("trampoline activity [NEW_TASK]") {
                starter.startTrampoline(TrampolineRequest(first, second, adjacentFirst))
            },
        )
}

class BothAdjacentStrategy : PairStrategy {
    override val method = PairMethod.BOTH_ADJACENT

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> {
        val a = PairStart(first, PairFlags.ADJACENT_NEW_TASK)
        val b = PairStart(second, PairFlags.ADJACENT_NEW_MULTIPLE_TASK)
        return listOf(
            attempt(a.describe("first")) { starter.start(a) },
            attempt(b.describe("second")) { starter.start(b) },
        )
    }
}

class StartActivitiesArrayStrategy : PairStrategy {
    override val method = PairMethod.START_ACTIVITIES_ARRAY

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> {
        val a = PairStart(first, PairFlags.NEW_TASK)
        val b = PairStart(second, PairFlags.ADJACENT_NEW_MULTIPLE_TASK)
        return listOf(
            attempt("startActivities [${a.describe("first")}, ${b.describe("second")}]") {
                starter.startAll(listOf(a, b))
            },
        )
    }
}

fun pairStrategyFor(
    method: PairMethod,
    pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
): PairStrategy =
    when (method) {
        PairMethod.SEQUENTIAL -> SequentialStrategy(pause)
        PairMethod.TRAMPOLINE_ADJACENT_SECOND -> TrampolineStrategy(adjacentFirst = false)
        PairMethod.TRAMPOLINE_ADJACENT_FIRST -> TrampolineStrategy(adjacentFirst = true)
        PairMethod.BOTH_ADJACENT -> BothAdjacentStrategy()
        PairMethod.START_ACTIVITIES_ARRAY -> StartActivitiesArrayStrategy()
    }
