package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Device facts worth knowing when judging a result; all read through public APIs. */
data class PairFacts(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    /** Whether the window that launched the pair was in multi-window mode (null: not known). */
    val inMultiWindow: Boolean?,
    val windowDp: String,
    val widthClass: String,
    /** The layout class Riffle uses for this window (folded/unfolded as Riffle sees it). */
    val posture: String,
    val features: List<String>,
    val targets: List<String>,
)

fun interface PairFactsSource {
    fun collect(
        first: PairTarget,
        second: PairTarget,
    ): PairFacts
}

/** Material window width classes: compact below 600 dp, medium below 840 dp, else expanded. */
fun widthClassFor(widthDp: Int): String =
    when {
        widthDp < 600 -> "compact"
        widthDp < 840 -> "medium"
        else -> "expanded"
    }

data class PairLogEntry(
    val id: Long,
    val timeMillis: Long,
    val method: PairMethod,
    val delayMillis: Long,
    val first: String,
    val second: String,
    val outcomes: List<StartOutcome>,
    val facts: PairFacts,
)

/**
 * Runs a pair launch and keeps the result log in memory, newest first. A trampoline reports its own two starts later
 * through [onTrampolineReport], which adds them to the most recent entry that used a trampoline.
 */
class AppPairExperiment(
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val entries = MutableStateFlow<List<PairLogEntry>>(emptyList())
    private var nextId = 1L
    private var waitingForTrampoline: Long? = null

    val log: StateFlow<List<PairLogEntry>> = entries

    suspend fun launch(
        method: PairMethod,
        first: PairTarget,
        second: PairTarget,
        delayMillis: Long,
        starter: PairStarter,
        factsSource: PairFactsSource,
    ) {
        val id = nextId++
        val strategy = pairStrategyFor(method, pause)
        val collected = factsSource.collect(first, second)
        if (method == PairMethod.TRAMPOLINE_ADJACENT_FIRST || method == PairMethod.TRAMPOLINE_ADJACENT_SECOND) {
            waitingForTrampoline = id
        }
        // The entry is shown at once so a slow sequential launch is visible; its outcomes arrive as they are made.
        entries.update {
            listOf(
                PairLogEntry(
                    id,
                    clock(),
                    method,
                    delayMillis,
                    first.packageName,
                    second.packageName,
                    emptyList(),
                    collected,
                ),
            ) + it
        }
        val outcomes = strategy.launch(first, second, starter, delayMillis)
        replace(id) { it.copy(outcomes = it.outcomes + outcomes) }
    }

    /** Called by the trampoline activity with the outcomes of its own starts. */
    fun onTrampolineReport(outcomes: List<StartOutcome>) {
        val id = waitingForTrampoline ?: return
        waitingForTrampoline = null
        replace(id) { it.copy(outcomes = it.outcomes + outcomes) }
    }

    private fun replace(
        id: Long,
        change: (PairLogEntry) -> PairLogEntry,
    ) = entries.update { list -> list.map { if (it.id == id) change(it) else it } }

    companion object {
        /** The one in-memory log of the app process, shared by the page and the trampoline activity. */
        val shared: AppPairExperiment by lazy { AppPairExperiment() }
    }
}

/** The log as plain text, newest first. Package names and device facts only. */
fun List<PairLogEntry>.toPlainText(zone: ZoneId = ZoneId.systemDefault()): String =
    buildString {
        appendLine("App pair experiment log (newest first)")
        if (this@toPlainText.isEmpty()) appendLine("no launches yet")
        this@toPlainText.forEach { entry ->
            appendLine()
            append(entry.toPlainText(zone))
        }
        appendLine()
        appendLine("ok means the call returned without an exception; the system can still refuse silently.")
    }

/** One entry as plain text: when, which method, the two packages, each call's outcome and the device facts. */
fun PairLogEntry.toPlainText(zone: ZoneId = ZoneId.systemDefault()): String {
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(zone)
    val e = this
    return buildString {
        appendLine("${clock.format(Instant.ofEpochMilli(e.timeMillis))}  ${e.method.title}  delay ${e.delayMillis} ms")
        appendLine("  first:  ${e.first}")
        appendLine("  second: ${e.second}")
        if (e.outcomes.isEmpty()) appendLine("  (no start made yet)")
        e.outcomes.forEach { o ->
            appendLine("  ${if (o.ok) "ok" else "FAILED"}: ${o.step}${o.error?.let { " -> $it" } ?: ""}")
        }
        val f = e.facts
        appendLine("  device: Android SDK ${f.sdkInt}, ${f.manufacturer} ${f.model}")
        appendLine(
            "  window: ${f.windowDp} dp (${f.widthClass}), multi-window at launch: " +
                (f.inMultiWindow?.toString() ?: "unknown") + ", layout: ${f.posture}",
        )
        f.features.forEach { appendLine("  feature: $it") }
        f.targets.forEach { appendLine("  target: $it") }
    }
}
