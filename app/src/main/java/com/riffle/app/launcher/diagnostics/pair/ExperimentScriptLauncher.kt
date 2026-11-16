package com.riffle.app.launcher.diagnostics.pair

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A [PairScriptLauncher] over the experiment: each step is an ordinary [AppPairExperiment.launch], so it writes the
 * normal log entry. The providers are read at every step, because the activity whose context starts things can be
 * recreated while a run is under way.
 */
class ExperimentScriptLauncher(
    private val experiment: AppPairExperiment,
    private val starter: () -> PairStarter,
    private val facts: () -> PairFactsSource,
    private val watcher: PairLaunchWatcher,
    private val resolver: (String) -> PairTarget?,
    private val home: () -> Unit,
) : PairScriptLauncher {
    override fun resolve(packageName: String): PairTarget? = resolver(packageName)

    override suspend fun fire(
        step: PairScriptStep,
        info: PairScriptInfo,
        first: PairTarget,
        second: PairTarget,
    ): Long =
        experiment.launch(
            step.method,
            first,
            second,
            PairScript.DELAY_MS,
            starter(),
            facts(),
            PairLaunchOptions(step.custom, info.firstOpen, info.secondOpen, watcher, step.logName, info),
        )

    override fun returnToLauncher() = home()
}

/**
 * This run's entries in step order (oldest first), after one header block: device, window, the app pair and the start
 * time. [runId] picks the run; by default the newest one in the log. Package names and device facts only.
 */
fun List<PairLogEntry>.toScriptPlainText(
    runId: Long? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val scripted = filter { it.script != null }
    val run = runId ?: scripted.maxByOrNull { it.script?.runId ?: 0 }?.script?.runId
    val entries = scripted.filter { it.script?.runId == run }.sortedBy { it.script?.step }
    return buildString {
        appendLine("App pair script log (oldest first)")
        val head = entries.firstOrNull()
        if (head == null) {
            appendLine("no script entries yet")
            return@buildString
        }
        val clock = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(zone)
        appendLine("device: Android SDK ${head.facts.sdkInt}, ${head.facts.manufacturer} ${head.facts.model}")
        appendLine("window: ${head.facts.windowDp} dp (${head.facts.widthClass}), layout: ${head.facts.posture}")
        appendLine("app pair: first ${head.first}, second ${head.second}")
        appendLine("started: ${clock.format(Instant.ofEpochMilli(run ?: head.timeMillis))}")
        appendLine("steps logged: ${entries.size} of ${head.script?.total}")
        entries.forEach {
            appendLine()
            append(it.toPlainText(zone))
        }
        appendLine()
        appendLine("ok means the call returned without an exception; the system can still refuse silently.")
    }
}
