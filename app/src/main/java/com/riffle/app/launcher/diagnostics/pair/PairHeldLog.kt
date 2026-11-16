package com.riffle.app.launcher.diagnostics.pair

// How a held-trampoline entry reads in the copied log (see AppPairExperiment.kt for the rest of the entry).

internal fun HeldVariant.timingText(): String {
    val second =
        secondAfterMillis?.let { "second start $it ms after the first" }
            ?: "second start ${PairHolderMachine.SPLIT_SETTLE_MS} ms after the split is seen"
    return "holder ${PairHolderMachine.SETTLE_MS} ms settle, $second, " +
        "no-split timeout ${PairHolderMachine.SPLIT_TIMEOUT_MS} ms, finish $finishDelayMillis ms after the second"
}

internal fun StringBuilder.appendHolder(trace: HolderTrace?) {
    appendLine("  holder trace (the holder activity's own window; offsets from its onCreate):")
    trace?.lines?.forEach { appendLine("    +${it.offsetMillis} ms ${it.text}") }
    if (trace == null || trace.lines.isEmpty()) appendLine("    nothing reported (the holder never ran)")
    appendLine("    final state: ${trace?.finalState ?: "not reached"}")
    if (trace?.open == true) appendLine("    (holder still alive)")
}
