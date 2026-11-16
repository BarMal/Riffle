package com.riffle.app.launcher.diagnostics.pair

/**
 * The one-tap scenarios of round 2: each is a whole recipe (a [PairMethod] and the [CustomPair] it uses), so the page
 * can launch it with nothing but the two apps. [logName] is what the log entry and the copied text call it.
 */
enum class PairScenario(
    val logName: String,
    val method: PairMethod,
    val custom: CustomPair,
) {
    RESET_BOTH("Reset both", PairMethod.CUSTOM, PairPreset.RESET_BOTH.pair),
    ONE_CALL_RESET_BOTH("One call, reset both", PairMethod.CUSTOM, PairPreset.ONE_CALL_RESET_BOTH.pair),
    FRESH_BOTH("Fresh both", PairMethod.CUSTOM, PairPreset.FRESH_BOTH.pair),
    REUSE_BOTH("Reuse both", PairMethod.CUSTOM, PairPreset.REUSE_BOTH.pair),
    REUSE_FIRST_FRESH_SECOND(
        "Reuse first, fresh second",
        PairMethod.CUSTOM,
        PairPreset.REUSE_FIRST_FRESH_SECOND.pair,
    ),
    FRESH_FIRST_REUSE_SECOND("Fresh first, reuse second", PairMethod.CUSTOM, PairPreset.FRESH_FIRST_REUSE_SECOND.pair),

    /** Round 1's "One startActivities call", as it was: first NEW_TASK, second ADJACENT|NEW_TASK|MULTIPLE_TASK. */
    ONE_CALL(
        "One call",
        PairMethod.START_ACTIVITIES_ARRAY,
        CustomPair(
            CustomAppFlags(),
            CustomAppFlags(adjacent = true, multipleTask = true),
            oneCall = true,
        ),
    ),
    ;

    companion object {
        /** The scenario a log entry was started from, by the name the entry carries; null for other launches. */
        fun fromLogName(name: String?): PairScenario? = entries.firstOrNull { it.logName == name }
    }
}
