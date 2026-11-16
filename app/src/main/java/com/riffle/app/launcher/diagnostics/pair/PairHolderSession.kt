package com.riffle.app.launcher.diagnostics.pair

/** What the holder wrote about itself: every line with its offset from `onCreate`, and how the run ended. */
data class HolderTrace(
    val lines: List<PairObservation>,
    /** Why the holder ended (see [PairHolderMachine.finalState]); null while it is still running. */
    val finalState: String?,
    /** False once the holder activity was destroyed: nothing more will be added. */
    val open: Boolean,
)

/** The holder activity's side of a [PairHolderSession]: the window, the two starts, finishing and a timer. */
interface HolderHost {
    fun isInMultiWindowMode(): Boolean

    /** The holder's window bounds as text, px and dp. */
    fun boundsText(): String

    /** Starts [target]'s main activity with [flags] from the holder; throws what `startActivity` throws. */
    fun start(
        target: PairTarget,
        flags: Int,
    )

    /** `finishAndRemoveTask()` without an animation. */
    fun finishHolder()

    /** Runs [task] on the main thread after [delayMillis]; [cancel] removes it. */
    fun post(
        delayMillis: Long,
        task: Runnable,
    )

    fun cancel(task: Runnable)
}

/** The callbacks of the holder activity the session is told about. */
sealed interface HolderEvent {
    data object Created : HolderEvent

    data object Started : HolderEvent

    data object Resumed : HolderEvent

    data object Paused : HolderEvent

    data object Stopped : HolderEvent

    data object Destroyed : HolderEvent

    data object ConfigurationChanged : HolderEvent

    data class FocusChanged(
        val hasFocus: Boolean,
    ) : HolderEvent

    data class MultiWindowChanged(
        val inMultiWindow: Boolean,
    ) : HolderEvent
}

/**
 * Glue between the holder activity and [PairHolderMachine]: the activity reports its callbacks here; the session feeds
 * the machine, performs its effects through [host], and appends every transition to the trace it hands to [report]
 * (the log entry the page created). Lines are `+N ms` from construction, which happens in `onCreate`.
 */
class PairHolderSession(
    private val request: HolderRequest,
    private val host: HolderHost,
    private val clock: () -> Long,
    private val report: (HolderTrace) -> Unit,
) {
    private val machine = PairHolderMachine(request.variant, clock)
    private val createdAt = clock()
    private val lines = mutableListOf<PairObservation>()
    private val tick = Runnable { handle(machine.onTick()) }
    private var lastMulti: Boolean? = null
    private var lastBounds: String? = null
    private var destroyed = false

    init {
        val v = request.variant
        note(
            "holder ${v.id} '${v.title}': first ${request.first.packageName} [${PairFlags.describe(v.firstFlags)}], " +
                "second ${request.second.packageName} [${PairFlags.describe(v.secondFlags)}], " +
                "finish ${v.finishDelayMillis} ms after the second start",
        )
    }

    /** Reports one callback of the holder activity. */
    fun on(event: HolderEvent) {
        when (event) {
            HolderEvent.Created -> observe("onCreate", null, null)
            HolderEvent.Started -> observe("onStart", null, null)
            HolderEvent.Resumed -> {
                observe("onResume", null, null)
                handle(machine.onResume())
                observe("poll on onResume", null, "poll on onResume")
            }
            HolderEvent.Paused -> {
                observe("onPause", null, null)
                handle(machine.onLeft("onPause"))
            }
            HolderEvent.Stopped -> {
                observe("onStop", null, null)
                handle(machine.onLeft("onStop"))
            }
            is HolderEvent.FocusChanged ->
                observe("onWindowFocusChanged(${event.hasFocus})", null, "poll on window focus")
            HolderEvent.ConfigurationChanged -> observe("onConfigurationChanged", null, "onConfigurationChanged")
            is HolderEvent.MultiWindowChanged -> {
                val name = "onMultiWindowModeChanged"
                observe("$name(${event.inMultiWindow})", event.inMultiWindow, name)
            }
            HolderEvent.Destroyed -> {
                observe("onDestroy", null, null)
                handle(machine.onDestroy())
                host.cancel(tick)
                destroyed = true
                publish()
            }
        }
    }

    // Writes the callback, then any change of multi-window mode or bounds since the last look; [signal] also gives the
    // reading to the machine, which is what the split detection runs on.
    private fun observe(
        name: String,
        multiWindow: Boolean?,
        signal: String?,
    ) {
        val multi = multiWindow ?: host.isInMultiWindowMode()
        val bounds = host.boundsText()
        note(name)
        val was = lastMulti
        if (multi != was) {
            note(if (was == null) "isInMultiWindowMode $multi (first look)" else "isInMultiWindowMode $was -> $multi")
        }
        if (bounds != lastBounds) note("window bounds ${lastBounds ?: "unknown"} -> $bounds")
        lastMulti = multi
        lastBounds = bounds
        if (signal != null) handle(machine.onSplitSignal(multi, signal)) else publish()
    }

    private fun handle(effects: List<HolderEffect>) {
        var firstStarted = false
        effects.forEach {
            when (it) {
                is HolderEffect.Note -> note(it.text)
                is HolderEffect.StartFirst -> {
                    start("first", request.first, it.flags)
                    firstStarted = true
                }
                is HolderEffect.StartSecond -> start("second", request.second, it.flags)
                HolderEffect.Finish -> host.finishHolder()
            }
        }
        host.cancel(tick)
        machine.wakeAt?.let { host.post((it - clock()).coerceAtLeast(0), tick) }
        publish()
        if (firstStarted) observe("poll after the first start", null, "poll after the first start")
    }

    private fun start(
        role: String,
        target: PairTarget,
        flags: Int,
    ) {
        val label = "start $role ${target.packageName} [${PairFlags.describe(flags)}]"
        val outcome = attempt(label) { host.start(target, flags) }
        note(if (outcome.ok) "$label: ok" else "$label: FAILED -> ${outcome.error}")
    }

    private fun note(text: String) {
        if (lines.size < MAX_LINES) lines += PairObservation((clock() - createdAt).coerceAtLeast(0), text)
    }

    private fun publish() = report(HolderTrace(lines.toList(), machine.finalState, open = !destroyed))

    private companion object {
        const val MAX_LINES = 150
    }
}
