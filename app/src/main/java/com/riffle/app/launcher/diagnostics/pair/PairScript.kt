package com.riffle.app.launcher.diagnostics.pair

/** One step of the scripted run: a name for the toast and log, the recipe, and whether both apps are already open. */
data class PairScriptStep(
    val name: String,
    val method: PairMethod,
    val custom: CustomPair,
    /** The [PairScenario] this step repeats; null for the warm-up, which is not a result. */
    val scenario: PairScenario?,
    /** Whether both apps are expected to be running already (true from the first real step on). */
    val bothOpen: Boolean,
    /** How long the step stays on screen before the launcher tries to come back. */
    val dwellMillis: Long = PairScript.DWELL_MS,
    /** The held-trampoline variant this step runs (round 3); null for the scenario script's steps. */
    val held: HeldVariant? = null,
    /** True when the step cannot say whether the apps were already open (the held script's first step). */
    val openUnknown: Boolean = false,
) {
    /** The name the log entry carries: the scenario's own, so the page's per-scenario labels still find it. */
    val logName: String get() = held?.title ?: scenario?.logName ?: name

    /** The toast that labels this step on the recording. */
    fun toastText(
        number: Int,
        total: Int,
    ): String = if (held != null) "Held $number of $total: ${held.short}" else "Step $number of $total: $name"
}

/** Which script a run is: the nine-step scenario script or the six-step held-trampoline one. */
enum class PairScriptKind(
    val label: String,
) {
    SCENARIOS("nine-step scenario script"),
    HELD("held-trampoline script (six steps)"),
}

/**
 * The hard-coded script behind "Run the whole script": the warm-up and then the scenarios, non-duplicating recipes
 * first (forced new tasks leave duplicates in Recents that would pollute later steps), and a closing repeat of
 * "Reset both" to see whether a reset still behaves with duplicate tasks around.
 */
object PairScript {
    /** The launcher must have been resumed this long before a step fires. */
    const val SETTLE_MS = 1_000L

    /** How long a fired step stays on screen before the launcher tries to come back. */
    const val DWELL_MS = 4_000L

    /** How long after the return attempt the launcher may take to be resumed before the log says it was not. */
    const val RETURN_WAIT_MS = 20_000L

    /** A resume this soon after a return attempt that raised nothing counts as the automatic return. */
    const val AUTO_RETURN_MS = 2_000L

    /** The delay between the two starts of a two-call step; the same as the page's default. */
    const val DELAY_MS = 600L

    /** Plain `NEW_TASK` starts of both apps, one after the other: nothing adjacent, so both simply open and run. */
    val WARM_UP_RECIPE = CustomPair(CustomAppFlags(), CustomAppFlags())

    val steps: List<PairScriptStep> =
        listOf(
            PairScriptStep("Warm-up: open both", PairMethod.CUSTOM, WARM_UP_RECIPE, null, bothOpen = false),
            scenarioStep(PairScenario.REUSE_BOTH),
            scenarioStep(PairScenario.RESET_BOTH),
            scenarioStep(PairScenario.ONE_CALL_RESET_BOTH),
            scenarioStep(PairScenario.ONE_CALL, "One call (round 1)"),
            scenarioStep(PairScenario.REUSE_FIRST_FRESH_SECOND, "Reuse first, fresh second (round 1's Both adjacent)"),
            scenarioStep(PairScenario.FRESH_FIRST_REUSE_SECOND),
            scenarioStep(PairScenario.FRESH_BOTH),
            scenarioStep(PairScenario.RESET_BOTH, "Reset both, again"),
        )

    /** How long a held step stays on screen: the holder needs about 2 s of its own before the apps are in place. */
    const val HELD_DWELL_MS = 6_000L

    /** The held-trampoline script: the six variants in order, no warm-up, H1 on whatever state the apps are in. */
    val heldSteps: List<PairScriptStep> =
        HeldVariants.all.mapIndexed { index, variant ->
            PairScriptStep(
                variant.title,
                PairMethod.CUSTOM,
                CustomPair.DEFAULT,
                null,
                bothOpen = index > 0,
                dwellMillis = HELD_DWELL_MS,
                held = variant,
                openUnknown = index == 0,
            )
        }

    private fun scenarioStep(
        scenario: PairScenario,
        name: String = scenario.logName,
    ) = PairScriptStep(name, scenario.method, scenario.custom, scenario, bothOpen = true)
}
