package com.riffle.app.launcher.diagnostics.pair

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The compact copy of a scripted run: one header block (device, window, features, targets, holder facts), then one
// short entry per step with only the lines that differ between steps. The full log is AppPairExperiment.kt's.

/**
 * One run's entries in step order (oldest first), after one header block: device, window, features, the app pair, the
 * holder's own facts and the start time. [runId] picks the run; by default the newest one started (of [kind], when
 * given, else of any kind). Package names and device facts only.
 */
fun List<PairLogEntry>.toScriptPlainText(
    runId: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    kind: PairScriptKind? = null,
): String {
    val scripted = filter { it.script != null && (kind == null || it.script.kind == kind) }
    val run = runId ?: scripted.maxByOrNull { it.script?.runId ?: 0 }?.script?.runId
    val entries = scripted.filter { it.script?.runId == run }.sortedBy { it.script?.step }
    return buildString {
        appendLine("App pair script log (oldest first)")
        val head = entries.firstOrNull()
        if (head == null) {
            appendLine("no script entries yet")
            return@buildString
        }
        appendHeader(head, entries, run ?: head.timeMillis, zone)
        entries.forEach {
            appendLine()
            append(it.toCompactText(zone))
        }
        appendLine()
        appendLine("ok means the call returned without an exception; the system can still refuse silently.")
    }
}

private fun StringBuilder.appendHeader(
    head: PairLogEntry,
    entries: List<PairLogEntry>,
    run: Long,
    zone: ZoneId,
) {
    val f = head.facts
    appendLine("script: ${head.script?.kind?.label}")
    val clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(zone)
    appendLine("device: Android SDK ${f.sdkInt}, ${f.manufacturer} ${f.model}")
    appendLine(
        "window: ${f.windowDp} dp (${f.widthClass}), layout: ${f.posture}, " +
            "multi-window at the first launch: ${f.inMultiWindow ?: "unknown"}",
    )
    if (f.features.isNotEmpty()) appendLine("features: ${f.features.joinToString(", ")}")
    f.targets.forEach { appendLine("target: $it") }
    appendLine("app pair: first ${head.first}, second ${head.second}")
    entries
        .flatMap { e -> e.holder?.lines.orEmpty() }
        .map { it.text }
        .filter { it.startsWith(PairHolderSession.FACT_PREFIX) }
        .distinct()
        .forEach { appendLine("holder ${it.removePrefix(PairHolderSession.FACT_PREFIX)}") }
    if (entries.any { it.held != null }) {
        appendLine(
            "holder timing: ${PairHolderMachine.SETTLE_MS} ms settle before the first start, " +
                "${PairHolderMachine.SPLIT_TIMEOUT_MS} ms no-split timeout",
        )
    }
    appendLine("started: ${clock.format(Instant.ofEpochMilli(run))}")
    appendLine("steps logged: ${entries.size} of ${head.script?.total}")
}

/**
 * One step without what the header says or what never changes in a script: the first line, one `script:` line, one
 * flags line, one return line (when there is anything to say), the launcher's trace and the holder's trace. The
 * owner's verdict and the Recents duplicates are printed only when set.
 */
internal fun PairLogEntry.toCompactText(zone: ZoneId): String =
    buildString {
        val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(zone)
        val (title, fullTiming) = titleAndTiming()
        val timing = held?.compactTimingText() ?: fullTiming
        val name = scenario?.let { "$it ($title)" } ?: title
        appendLine("${clock.format(Instant.ofEpochMilli(timeMillis))}  $name  $timing")
        script?.let { appendLine(it.stepLine()) }
        appendLine(flagsLine())
        script?.returnLine()?.let { appendLine(it) }
        val watching = if (traceOpen) " (still watching)" else ""
        appendLine("  launcher lifecycle trace$watching${if (trace.isEmpty()) ": nothing recorded" else ":"}")
        trace.forEach { appendLine("    +${it.offsetMillis} ms ${it.text}") }
        if (held != null) appendHolderCompact(holder)
        verdict?.let { appendLine("  owner's verdict: ${it.text}") }
        if (duplicatesInRecents) appendLine("  duplicates in Recents: yes")
    }

private fun PairScriptInfo.stepLine(): String =
    "  script: step $step of $total; already open: " +
        if (openUnknown) "unknown" else "first=$firstOpen second=$secondOpen"

private fun PairScriptInfo.returnLine(): String? {
    val parts =
        listOfNotNull(
            returnAttempt?.let { "return attempt: $it" },
            returned?.let { "returned to launcher: $it" },
            notes.takeIf { it.isNotEmpty() }?.let { "note: ${it.joinToString(", ")}" },
        )
    return if (parts.isEmpty()) null else "  " + parts.joinToString("; ")
}

private fun PairLogEntry.flagsLine(): String {
    val (firstFlags, secondFlags) = held?.let { it.firstFlags to it.secondFlags } ?: method.plannedFlags(custom)
    val second = if (held?.startsSecond == false) "none" else PairFlags.describe(secondFlags)
    val calls =
        when {
            outcomes.isEmpty() -> "no start made yet"
            outcomes.all { it.ok } -> "ok"
            else -> outcomes.filterNot { it.ok }.joinToString("; ") { "FAILED: ${it.step} -> ${it.error}" }
        }
    return "  flags first: ${PairFlags.describe(firstFlags)}; second: $second; calls: $calls"
}

private fun StringBuilder.appendHolderCompact(trace: HolderTrace?) {
    if (trace == null || trace.lines.isEmpty()) {
        appendLine("  holder trace: nothing reported (the holder never ran)")
        return
    }
    val alive = if (trace.open) " (still alive)" else ""
    appendLine("  holder trace, final state: ${trace.finalState ?: "not reached"}$alive:")
    val kept = trace.lines.filterNot { line -> DROPPED_IN_COMPACT.any { it.containsMatchIn(line.text) } }
    // Lines written in the same millisecond share one line, so a burst of callbacks reads as one moment.
    val groups = mutableListOf<Pair<Long, MutableList<String>>>()
    kept.forEach {
        val text = it.text.compactHolderText()
        val last = groups.lastOrNull()
        if (last != null && last.first == it.offsetMillis) {
            last.second += text
        } else {
            groups += it.offsetMillis to mutableListOf(text)
        }
    }
    groups.forEach { (offset, texts) -> appendLine("    +$offset ms ${texts.joinToString(" | ")}") }
}

// Lines a compact copy leaves out: the holder's own facts (printed once in the header), its recipe line (the entry's
// flags line says the same) and the two state notes that only repeat the start lines that follow them.
private val DROPPED_IN_COMPACT =
    listOf(
        Regex("^${PairHolderSession.FACT_PREFIX}"),
        Regex("^holder [HP]\\d "),
        Regex("^state (Settling|WaitingForSplit):"),
    )

// Package names are in the header; the px size repeats the dp size; the two config numbers are shortened.
private fun String.compactHolderText(): String =
    replace(Regex("^start (first|second) \\S+ "), "start $1 ")
        .replace(Regex("(\\]\\[\\d+,\\d+\\]) \\d+x\\d+ px \\((\\d+x\\d+ dp)\\)"), "$1 $2")
        .replace(Regex("smallestScreenWidthDp=(\\d+) screenWidthDp=(\\d+)"), "sw=$1 w=$2")
