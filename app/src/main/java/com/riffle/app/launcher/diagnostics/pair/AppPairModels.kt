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
    CUSTOM(
        "Custom",
        "Round 2: choose the flags for each app yourself, or load a scenario's flags.",
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
            if (flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0) add("CLEAR_TASK")
            if (flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0) add("NO_ANIMATION")
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
    custom: CustomPair = CustomPair.DEFAULT,
    pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
): PairStrategy =
    when (method) {
        PairMethod.SEQUENTIAL -> SequentialStrategy(pause)
        PairMethod.TRAMPOLINE_ADJACENT_SECOND -> TrampolineStrategy(adjacentFirst = false)
        PairMethod.TRAMPOLINE_ADJACENT_FIRST -> TrampolineStrategy(adjacentFirst = true)
        PairMethod.BOTH_ADJACENT -> BothAdjacentStrategy()
        PairMethod.START_ACTIVITIES_ARRAY -> StartActivitiesArrayStrategy()
        PairMethod.CUSTOM -> CustomStrategy(custom, pause)
    }

/** The two flags the five fixed methods pass to the first and the second app (what their strategies start with). */
fun PairMethod.plannedFlags(custom: CustomPair = CustomPair.DEFAULT): Pair<Int, Int> =
    when (this) {
        PairMethod.SEQUENTIAL, PairMethod.START_ACTIVITIES_ARRAY ->
            PairFlags.NEW_TASK to PairFlags.ADJACENT_NEW_MULTIPLE_TASK
        PairMethod.TRAMPOLINE_ADJACENT_SECOND -> PairFlags.NEW_TASK to PairFlags.ADJACENT_NEW_TASK
        PairMethod.TRAMPOLINE_ADJACENT_FIRST -> PairFlags.ADJACENT_NEW_TASK to PairFlags.NEW_TASK
        PairMethod.BOTH_ADJACENT -> PairFlags.ADJACENT_NEW_TASK to PairFlags.ADJACENT_NEW_MULTIPLE_TASK
        PairMethod.CUSTOM -> custom.first.flags to custom.second.flags
    }

/**
 * The switches of one app in the Custom strategy. `NEW_TASK` is always on (a start from the launcher's own context
 * needs it for another app's activity), so it is not a switch; [flags] is the whole int.
 */
data class CustomAppFlags(
    val adjacent: Boolean = false,
    val multipleTask: Boolean = false,
    val clearTask: Boolean = false,
    val noAnimation: Boolean = false,
) {
    val flags: Int
        get() =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                (if (adjacent) Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT else 0) or
                (if (multipleTask) Intent.FLAG_ACTIVITY_MULTIPLE_TASK else 0) or
                (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0) or
                (if (noAnimation) Intent.FLAG_ACTIVITY_NO_ANIMATION else 0)

    val description: String get() = PairFlags.describe(flags)
}

/** One switch of [CustomAppFlags], for the page and the tests. */
enum class CustomSwitch(
    val label: String,
) {
    ADJACENT("LAUNCH_ADJACENT"),
    MULTIPLE_TASK("MULTIPLE_TASK"),
    CLEAR_TASK("CLEAR_TASK"),
    NO_ANIMATION("NO_ANIMATION"),
}

fun CustomAppFlags.isOn(switch: CustomSwitch): Boolean =
    when (switch) {
        CustomSwitch.ADJACENT -> adjacent
        CustomSwitch.MULTIPLE_TASK -> multipleTask
        CustomSwitch.CLEAR_TASK -> clearTask
        CustomSwitch.NO_ANIMATION -> noAnimation
    }

fun CustomAppFlags.with(
    switch: CustomSwitch,
    on: Boolean,
): CustomAppFlags =
    when (switch) {
        CustomSwitch.ADJACENT -> copy(adjacent = on)
        CustomSwitch.MULTIPLE_TASK -> copy(multipleTask = on)
        CustomSwitch.CLEAR_TASK -> copy(clearTask = on)
        CustomSwitch.NO_ANIMATION -> copy(noAnimation = on)
    }

/**
 * The Custom strategy's whole recipe: switches per app and whether both go out in one `startActivities` call (then the
 * delay is ignored). [starts] is the list of `startActivity` calls it stands for, in order.
 */
data class CustomPair(
    val first: CustomAppFlags = CustomAppFlags(),
    val second: CustomAppFlags = CustomAppFlags(),
    val oneCall: Boolean = false,
) {
    fun starts(
        a: PairTarget,
        b: PairTarget,
    ): List<PairStart> = listOf(PairStart(a, first.flags), PairStart(b, second.flags))

    companion object {
        /** What the page starts with: the same recipe as the "Both adjacent" method. */
        val DEFAULT =
            CustomPair(CustomAppFlags(adjacent = true), CustomAppFlags(adjacent = true, multipleTask = true))
    }
}

/** One-tap recipes for the Custom switches; each only fills them in. */
enum class PairPreset(
    val pair: CustomPair,
) {
    FRESH_BOTH(
        CustomPair(
            CustomAppFlags(adjacent = true, multipleTask = true),
            CustomAppFlags(adjacent = true, multipleTask = true),
        ),
    ),
    REUSE_BOTH(CustomPair(CustomAppFlags(adjacent = true), CustomAppFlags(adjacent = true))),
    RESET_BOTH(
        CustomPair(
            CustomAppFlags(adjacent = true, clearTask = true),
            CustomAppFlags(adjacent = true, clearTask = true),
        ),
    ),
    REUSE_FIRST_FRESH_SECOND(
        CustomPair(CustomAppFlags(adjacent = true), CustomAppFlags(adjacent = true, multipleTask = true)),
    ),
    FRESH_FIRST_REUSE_SECOND(
        CustomPair(CustomAppFlags(adjacent = true, multipleTask = true), CustomAppFlags(adjacent = true)),
    ),
    ONE_CALL_RESET_BOTH(
        CustomPair(
            CustomAppFlags(clearTask = true),
            CustomAppFlags(adjacent = true, clearTask = true),
            oneCall = true,
        ),
    ),
}

/** Starts the pair the way a [CustomPair] says: one call, or two starts with the delay between them. */
class CustomStrategy(
    private val custom: CustomPair,
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : PairStrategy {
    override val method = PairMethod.CUSTOM

    override suspend fun launch(
        first: PairTarget,
        second: PairTarget,
        starter: PairStarter,
        delayMillis: Long,
    ): List<StartOutcome> {
        val (a, b) = custom.starts(first, second)
        if (custom.oneCall) {
            return listOf(
                attempt("startActivities [${a.describe("first")}, ${b.describe("second")}]") {
                    starter.startAll(listOf(a, b))
                },
            )
        }
        val outcomes = mutableListOf(attempt(a.describe("first")) { starter.start(a) })
        if (delayMillis > 0) pause(delayMillis)
        outcomes += attempt(b.describe("second after $delayMillis ms")) { starter.start(b) }
        return outcomes
    }
}
