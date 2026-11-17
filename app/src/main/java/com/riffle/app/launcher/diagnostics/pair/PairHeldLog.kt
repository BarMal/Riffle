package com.riffle.app.launcher.diagnostics.pair

// How a held-trampoline entry reads in the copied log (see AppPairExperiment.kt for the rest of the entry).

internal fun HeldVariant.timingText(): String {
    val second =
        when {
            !startsSecond -> "no second start"
            secondAfterMillis != null -> "second start $secondAfterMillis ms after the first"
            else -> "second start $splitSettleMillis ms after the split is seen"
        }
    val last = if (startsSecond) "second" else "first"
    return "holder ${PairHolderMachine.SETTLE_MS} ms settle, $second, " +
        "no-split timeout ${PairHolderMachine.SPLIT_TIMEOUT_MS} ms, finish $finishDelayMillis ms after the $last"
}

/** The timing without the parts every variant shares (those are in a compact copy's header, once). */
internal fun HeldVariant.compactTimingText(): String {
    val second =
        when {
            !startsSecond -> "no second start"
            secondAfterMillis != null -> "second $secondAfterMillis ms after the first"
            else -> "second $splitSettleMillis ms after the split"
        }
    return "$second, finish $finishDelayMillis ms after the ${if (startsSecond) "second" else "first"}"
}

internal fun StringBuilder.appendHolder(trace: HolderTrace?) {
    appendLine("  holder trace (the holder activity's own window; offsets from its onCreate):")
    trace?.lines?.forEach { appendLine("    +${it.offsetMillis} ms ${it.text}") }
    if (trace == null || trace.lines.isEmpty()) appendLine("    nothing reported (the holder never ran)")
    appendLine("    final state: ${trace?.finalState ?: "not reached"}")
    if (trace?.open == true) appendLine("    (holder still alive)")
}
