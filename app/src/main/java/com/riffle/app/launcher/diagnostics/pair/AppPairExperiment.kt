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

/** What a launch carries besides the method: the Custom recipe, the owner's "already open" ticks and the watcher. */
data class PairLaunchOptions(
    val custom: CustomPair = CustomPair.DEFAULT,
    val firstWasOpen: Boolean = false,
    val secondWasOpen: Boolean = false,
    val watcher: PairLaunchWatcher = PairLaunchWatcher.None,
    /** The [PairScenario.logName] this launch is, when it came from a scenario button. */
    val scenario: String? = null,
    /** The scripted run's facts about this launch; null for a launch the owner made by hand. */
    val script: PairScriptInfo? = null,
    /** The held-trampoline recipe this launch is; null for every other launch. */
    val held: HeldVariant? = null,
)

/** The owner's after-the-fact judgement of what the screen showed. */
enum class PairVerdict(
    val text: String,
) {
    SPLIT("Split"),
    NO_SPLIT("No split"),
    SPLIT_UNEVEN("Split, uneven"),
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
    /** The Custom recipe used; only meaningful when [method] is [PairMethod.CUSTOM]. */
    val custom: CustomPair = CustomPair.DEFAULT,
    /** Ticked by the owner before launching: nothing in the public API can tell whether an app was running. */
    val firstWasOpen: Boolean = false,
    val secondWasOpen: Boolean = false,
    /** What the launcher's own window saw after the launch, and whether it is still watching. */
    val trace: List<PairObservation> = emptyList(),
    val traceOpen: Boolean = false,
    val verdict: PairVerdict? = null,
    val duplicatesInRecents: Boolean = false,
    /** The [PairScenario.logName] when a scenario button started this launch. */
    val scenario: String? = null,
    /** The scripted run's facts about this launch; null for a launch the owner made by hand. */
    val script: PairScriptInfo? = null,
    /** The held-trampoline recipe, for an entry the holder activity reports into (its [method] is Custom). */
    val held: HeldVariant? = null,
    /** What the holder activity wrote about itself; null until it reports. */
    val holder: HolderTrace? = null,
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
    private var waitingForHolder: Long? = null

    val log: StateFlow<List<PairLogEntry>> = entries

    /** Runs the launch and returns the id of the log entry it wrote. */
    suspend fun launch(
        method: PairMethod,
        first: PairTarget,
        second: PairTarget,
        delayMillis: Long,
        starter: PairStarter,
        factsSource: PairFactsSource,
        options: PairLaunchOptions = PairLaunchOptions(),
    ): Long {
        val id = nextId++
        val strategy = options.held?.let { HeldStrategy(it) } ?: pairStrategyFor(method, options.custom, pause)
        val collected = factsSource.collect(first, second)
        if (method == PairMethod.TRAMPOLINE_ADJACENT_FIRST || method == PairMethod.TRAMPOLINE_ADJACENT_SECOND) {
            waitingForTrampoline = id
        }
        if (options.held != null) waitingForHolder = id
        val launchedAt = clock()
        // The entry is shown at once so a slow sequential launch is visible; its outcomes arrive as they are made.
        entries.update {
            listOf(
                PairLogEntry(
                    id,
                    launchedAt,
                    method,
                    delayMillis,
                    first.packageName,
                    second.packageName,
                    emptyList(),
                    collected,
                    custom = options.custom,
                    firstWasOpen = options.firstWasOpen,
                    secondWasOpen = options.secondWasOpen,
                    scenario = options.scenario,
                    script = options.script,
                    held = options.held,
                ),
            ) + it
        }
        // Watching starts before the first start so the launcher's own reaction to it is in the trace.
        options.watcher.begin(launchedAt) { events, open -> replace(id) { it.copy(trace = events, traceOpen = open) } }
        val outcomes = strategy.launch(first, second, starter, delayMillis)
        replace(id) { it.copy(outcomes = it.outcomes + outcomes) }
        return id
    }

    /** Called by the trampoline activity with the outcomes of its own starts. */
    fun onTrampolineReport(outcomes: List<StartOutcome>) {
        val id = waitingForTrampoline ?: return
        waitingForTrampoline = null
        replace(id) { it.copy(outcomes = it.outcomes + outcomes) }
    }

    /** Called by the holder activity with everything it has written so far; the last report has `open == false`. */
    fun onHolderReport(trace: HolderTrace) {
        val id = waitingForHolder ?: return
        if (!trace.open) waitingForHolder = null
        replace(id) { it.copy(holder = trace) }
    }

    /** The owner's verdict for entry [id]; choosing the same verdict again clears it. */
    fun setVerdict(
        id: Long,
        verdict: PairVerdict,
    ) = replace(id) { it.copy(verdict = if (it.verdict == verdict) null else verdict) }

    /** Changes the script facts of entry [id], when it has any (the runner adds what happened after the launch). */
    fun updateScript(
        id: Long,
        change: (PairScriptInfo) -> PairScriptInfo,
    ) = replace(id) { entry -> entry.script?.let { entry.copy(script = change(it)) } ?: entry }

    fun setDuplicates(
        id: Long,
        duplicates: Boolean,
    ) = replace(id) { it.copy(duplicatesInRecents = duplicates) }

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

/** One entry as plain text: when, which method, the packages, flags, calls, the trace, the verdict and the device. */
fun PairLogEntry.toPlainText(zone: ZoneId = ZoneId.systemDefault()): String =
    buildString {
        appendRecipe(this@toPlainText, zone)
        script?.let { appendScript(it) }
        if (held != null) appendHolder(holder)
        appendTraceAndVerdict(this@toPlainText)
        appendDevice(facts)
    }

private fun StringBuilder.appendRecipe(
    e: PairLogEntry,
    zone: ZoneId,
) {
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT).withZone(zone)
    val (title, timing) = e.titleAndTiming()
    val (firstFlags, secondFlags) = e.held?.let { it.firstFlags to it.secondFlags } ?: e.method.plannedFlags(e.custom)
    val name = e.scenario?.let { "$it ($title)" } ?: title
    appendLine("${clock.format(Instant.ofEpochMilli(e.timeMillis))}  $name  $timing")
    appendLine("  first:  ${e.first}")
    appendLine("  second: ${e.second}")
    appendLine("  flags first:  ${PairFlags.describe(firstFlags)}")
    appendLine("  flags second: ${PairFlags.describe(secondFlags)}")
    appendLine(e.alreadyOpenLine())
    if (e.outcomes.isEmpty()) appendLine("  (no start made yet)")
    e.outcomes.forEach { o ->
        appendLine("  ${if (o.ok) "ok" else "FAILED"}: ${o.step}${o.error?.let { " -> $it" } ?: ""}")
    }
}

/** The method's title and the timing the entry's first line prints: a held entry's own, else the strategy's. */
private fun PairLogEntry.titleAndTiming(): Pair<String, String> {
    val variant = held
    if (variant != null) return "Held trampoline" to variant.timingText()
    val oneCall = method == PairMethod.START_ACTIVITIES_ARRAY || (method == PairMethod.CUSTOM && custom.oneCall)
    return method.title to if (oneCall) "one startActivities call, delay ignored" else "delay $delayMillis ms"
}

private fun PairLogEntry.alreadyOpenLine(): String =
    if (script?.openUnknown == true) {
        "  already open (ticked by the owner): not asked, see the script line"
    } else {
        "  already open (ticked by the owner): first ${firstWasOpen.yesNo()}, second ${secondWasOpen.yesNo()}"
    }

private fun StringBuilder.appendScript(info: PairScriptInfo) {
    appendLine("  script: step ${info.step} of ${info.total}")
    appendLine(
        if (info.openUnknown) {
            "  already open: unknown"
        } else {
            "  already open: first=${info.firstOpen} second=${info.secondOpen}"
        },
    )
    info.returnAttempt?.let { appendLine("  return attempt: $it") }
    info.returned?.let { appendLine("  returned to launcher: $it") }
    info.notes.forEach { appendLine("  note: $it") }
}

private fun StringBuilder.appendTraceAndVerdict(e: PairLogEntry) {
    appendLine("  launcher lifecycle trace (the launcher's own window only):")
    e.trace.forEach { appendLine("    +${it.offsetMillis} ms ${it.text}") }
    if (e.trace.isEmpty()) appendLine("    nothing recorded")
    if (e.traceOpen) appendLine("    (still watching)")
    appendLine("  owner's verdict: ${e.verdict?.text ?: "not noted yet"}")
    appendLine("  duplicates in Recents: ${if (e.duplicatesInRecents) "yes" else "no (not ticked)"}")
}

private fun StringBuilder.appendDevice(f: PairFacts) {
    appendLine("  device: Android SDK ${f.sdkInt}, ${f.manufacturer} ${f.model}")
    appendLine(
        "  window: ${f.windowDp} dp (${f.widthClass}), multi-window at launch: " +
            (f.inMultiWindow?.toString() ?: "unknown") + ", layout: ${f.posture}",
    )
    f.features.forEach { appendLine("  feature: $it") }
    f.targets.forEach { appendLine("  target: $it") }
}

private fun Boolean.yesNo() = if (this) "yes" else "no"
