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

    /** `isInPictureInPictureMode`; false when it cannot be read. */
    fun isInPictureInPicture(): Boolean = false

    /** `smallestScreenWidthDp` and `screenWidthDp` of the holder's configuration, as text. */
    fun configText(): String = "unknown"

    /** Facts about the holder itself (its ActivityInfo, the freeform feature, the HOME role), one line each. */
    fun holderFacts(): List<String> = emptyList()

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

    data class TopResumedChanged(
        val isTop: Boolean,
    ) : HolderEvent

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
 * (the log entry the page created). Lines are `+N ms` from construction, which happens in `onCreate`. Until the
 * activity is destroyed it also looks at the window every [TICK_MS] ms (one ticker, stopped on destroy) and writes
 * every change of multi-window mode, bounds, configuration and picture-in-picture mode once; a drop out of multi-window
 * mode is written as `split lost after N ms in split` with what the holder was doing.
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
    private var lastConfig: String? = null
    private var lastPip: Boolean? = null
    private var pendingLabel: String? = null
    private var splitSince: Long? = null
    private var destroyed = false
    private val ticker =
        object : Runnable {
            override fun run() {
                if (destroyed) return
                look(null, tick = true)
                host.post(TICK_MS, this)
            }
        }

    init {
        val v = request.variant
        note(
            "holder ${v.id} '${v.title}': first [${PairFlags.describe(v.firstFlags)}], " +
                (if (v.startsSecond) "second [${PairFlags.describe(v.secondFlags)}]" else "no second start") +
                ", finish ${v.finishDelayMillis} ms after the ${if (v.startsSecond) "second" else "first"} start",
        )
        host.holderFacts().forEach { note("$FACT_PREFIX$it") }
    }

    /** Reports one callback of the holder activity. */
    fun on(event: HolderEvent) {
        when (event) {
            HolderEvent.Created -> {
                observe("onCreate", null, null)
                host.post(TICK_MS, ticker)
            }
            HolderEvent.Started -> observe("onStart", null, null)
            HolderEvent.Resumed -> {
                observe("onResume", null, null)
                handle(machine.onResume())
                observe("poll on onResume", null, "poll on onResume", announce = false)
            }
            HolderEvent.Paused -> {
                observe("onPause", null, null)
                handle(machine.onLeft("onPause"))
            }
            HolderEvent.Stopped -> {
                observe("onStop", null, null)
                handle(machine.onLeft("onStop"))
            }
            is HolderEvent.TopResumedChanged -> observe("onTopResumedActivityChanged(${event.isTop})", null, null)
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
                host.cancel(ticker)
                destroyed = true
                publish()
            }
        }
    }

    // Writes the callback, then any change since the last look; [signal] also gives the reading to the machine, which
    // is what the split detection runs on.
    private fun observe(
        name: String,
        multiWindow: Boolean?,
        signal: String?,
        announce: Boolean = true,
    ) {
        if (announce) note(name)
        look(multiWindow, label = name.takeUnless { announce })
        if (signal != null) handle(machine.onSplitSignal(lastMulti == true, signal)) else publish()
    }

    // The ticker's look: only changes are written, and a changed multi-window mode also goes to the machine.
    private fun look(
        multiWindow: Boolean?,
        tick: Boolean = false,
        label: String? = if (tick) "poll $TICK_MS ms" else null,
    ) {
        pendingLabel = label
        val before = lines.size
        val was = lastMulti
        val multi = multiWindow ?: host.isInMultiWindowMode()
        noteChanges(multi, was)
        lastMulti = multi
        pendingLabel = null
        if (tick && multi != was) {
            handle(machine.onSplitSignal(multi, "poll $TICK_MS ms"))
        } else if (lines.size != before) {
            publish()
        }
    }

    private fun noteChanges(
        multi: Boolean,
        was: Boolean?,
    ) {
        if (multi != was) {
            change(if (was == null) "isInMultiWindowMode $multi (first look)" else "isInMultiWindowMode $was -> $multi")
            if (multi) splitSince = clock()
            if (!multi && was == true) noteSplitLost()
        }
        val bounds = host.boundsText()
        if (bounds != lastBounds) change("window bounds ${lastBounds ?: "unknown"} -> $bounds")
        lastBounds = bounds
        val config = host.configText()
        if (config != lastConfig) change("config ${lastConfig?.let { "$it -> " } ?: ""}$config")
        lastConfig = config
        val pip = host.isInPictureInPicture()
        if (pip != lastPip) change("isInPictureInPictureMode ${lastPip?.let { "$it -> " } ?: ""}$pip")
        lastPip = pip
    }

    // A change found by a look that was not itself a callback: the line naming the look comes first, once.
    private fun change(text: String) {
        pendingLabel?.let { note(it) }
        pendingLabel = null
        note(text)
    }

    private fun noteSplitLost() {
        change(splitLostText((clock() - (splitSince ?: clock())).coerceAtLeast(0), machine.status))
        splitSince = null
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
        if (firstStarted) {
            observe("poll after the first start", null, "poll after the first start", announce = false)
        }
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

    companion object {
        /** How often the holder looks at its own window while it is alive. */
        const val TICK_MS = 250L

        /** Marks the lines about the holder itself, which a compact copy prints once in its header. */
        const val FACT_PREFIX = "fact: "

        private const val MAX_LINES = 200

        /** The line written when the holder drops out of multi-window mode; [doing] is [PairHolderMachine.status]. */
        fun splitLostText(
            inSplitMillis: Long,
            doing: String,
        ): String = "split lost after $inSplitMillis ms in split ($doing)"
    }
}
