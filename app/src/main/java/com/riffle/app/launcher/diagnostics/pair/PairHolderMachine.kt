package com.riffle.app.launcher.diagnostics.pair

/** What the machine asks the holder activity to do. Each effect is emitted at most once per run. */
sealed interface HolderEffect {
    data class StartFirst(
        val flags: Int,
    ) : HolderEffect

    data class StartSecond(
        val flags: Int,
    ) : HolderEffect

    /** `finishAndRemoveTask()` with no animation. */
    data object Finish : HolderEffect

    /** A line for the log. */
    data class Note(
        val text: String,
    ) : HolderEffect
}

enum class HolderPhase {
    Created,
    Settling,
    WaitingForSplit,
    SplitSettling,
    Finishing,
    Done,
}

/**
 * The holder's state machine, with no Android in it: the activity feeds it lifecycle and window events and performs
 * the effects it returns; time comes from [clock] and the machine says when it next wants an [onTick] ([wakeAt]).
 * Created, then the first resume and [SETTLE_MS] later the first start, then the wait for `isInMultiWindowMode`,
 * [SPLIT_SETTLE_MS] after it the second start (or, for a variant with [HeldVariant.secondAfterMillis], that long after
 * the first), then [HeldVariant.finishDelayMillis] later the finish. No split within [SPLIT_TIMEOUT_MS] of the first
 * start is noted and handled by [HeldVariant.onTimeout]. All calls are expected on the main thread.
 */
class PairHolderMachine(
    private val variant: HeldVariant,
    private val clock: () -> Long,
) {
    var phase = HolderPhase.Created
        private set

    /** Why the run ended, once [phase] is [HolderPhase.Done]. */
    var finalState: String? = null
        private set

    private var resumedAt = 0L
    private var firstAt = 0L
    private var splitAt = 0L
    private var lastStartAt = 0L
    private var secondStarted = false
    private var multiWindow: Boolean? = null

    /** The first resume starts the settle time; later resumes change nothing. */
    fun onResume(): List<HolderEffect> =
        collect {
            if (phase == HolderPhase.Created) {
                resumedAt = clock()
                phase = HolderPhase.Settling
                it += HolderEffect.Note("state Settling: first start due in $SETTLE_MS ms")
            }
        }

    /** The holder was paused or stopped ([source] says which); before the first start that ends the run. */
    fun onLeft(source: String): List<HolderEffect> =
        collect {
            if (phase == HolderPhase.Created || phase == HolderPhase.Settling) {
                it += HolderEffect.Note("$source before the first start could run")
                finish(it, "$source before the first start")
            }
        }

    /** The activity is being destroyed: whatever was pending is dropped and nothing else is started. */
    fun onDestroy(): List<HolderEffect> =
        collect {
            if (phase != HolderPhase.Done) {
                val was = phase
                it += HolderEffect.Note("destroyed in state $was")
                phase = HolderPhase.Done
                finalState = "destroyed in state $was"
            }
        }

    /** A reading of `isInMultiWindowMode` from [source] (a callback or a poll). */
    fun onSplitSignal(
        inMultiWindow: Boolean,
        source: String,
    ): List<HolderEffect> =
        collect {
            val before = multiWindow
            multiWindow = inMultiWindow
            if (inMultiWindow && phase == HolderPhase.WaitingForSplit && variant.secondAfterMillis == null) {
                splitAt = clock()
                phase = HolderPhase.SplitSettling
                val was = before ?: "unknown"
                val settle = variant.splitSettleMillis
                it += HolderEffect.Note("split observed via $source (was $was); second start in $settle ms")
            }
        }

    /** The time the machine next wants [onTick] called at, in [clock] terms; null when nothing is pending. */
    val wakeAt: Long?
        get() =
            when (phase) {
                HolderPhase.Settling -> resumedAt + SETTLE_MS
                HolderPhase.WaitingForSplit -> firstAt + (variant.secondAfterMillis ?: SPLIT_TIMEOUT_MS)
                HolderPhase.SplitSettling -> splitAt + variant.splitSettleMillis
                HolderPhase.Finishing -> lastStartAt + variant.finishDelayMillis
                HolderPhase.Created, HolderPhase.Done -> null
            }

    /** What the holder is doing now, for the `split lost` line: waiting, how long ago the last start was, finishing. */
    val status: String
        get() {
            val ago = clock() - lastStartAt
            return when (phase) {
                HolderPhase.Created, HolderPhase.Settling -> "waiting, nothing started yet"
                HolderPhase.WaitingForSplit -> "waiting for the split, first started ${clock() - firstAt} ms ago"
                HolderPhase.SplitSettling -> "waiting to start the second app"
                HolderPhase.Finishing ->
                    if (secondStarted) "second started $ago ms ago" else "first started $ago ms ago, no second start"
                HolderPhase.Done -> "finishing"
            }
        }

    /** Moves on every step whose time has come. */
    fun onTick(): List<HolderEffect> = collect { }

    private fun collect(change: (MutableList<HolderEffect>) -> Unit): List<HolderEffect> {
        val out = mutableListOf<HolderEffect>()
        change(out)
        while (step(out)) Unit
        return out
    }

    // One step for the phase whose time has come; false when nothing is due (Created and Done are never due).
    private fun step(out: MutableList<HolderEffect>): Boolean {
        val due = wakeAt
        val ready = due != null && clock() >= due
        if (ready) {
            when (phase) {
                HolderPhase.Settling -> {
                    firstAt = clock()
                    phase = HolderPhase.WaitingForSplit
                    out += HolderEffect.StartFirst(variant.firstFlags)
                    if (variant.startsSecond) {
                        out += HolderEffect.Note("state WaitingForSplit: first started")
                    } else {
                        lastStartAt = firstAt
                        phase = HolderPhase.Finishing
                        val ms = variant.finishDelayMillis
                        out += HolderEffect.Note("state Finishing: first started, no second start; finish in $ms ms")
                    }
                }
                HolderPhase.WaitingForSplit -> waitedLongEnough(out)
                HolderPhase.SplitSettling -> startSecond(out, "split settled")
                HolderPhase.Finishing -> finish(out, "finished after the second start")
                HolderPhase.Created, HolderPhase.Done -> Unit
            }
        }
        return ready
    }

    private fun waitedLongEnough(out: MutableList<HolderEffect>) {
        if (variant.secondAfterMillis != null) {
            startSecond(out, "${variant.secondAfterMillis} ms after the first start")
            return
        }
        out += HolderEffect.Note("no split after first start ($SPLIT_TIMEOUT_MS ms)")
        if (variant.onTimeout == TimeoutAction.START_SECOND) {
            startSecond(out, "no split after first start; starting the second anyway")
        } else {
            finish(out, "no split after first start; stopped")
        }
    }

    private fun startSecond(
        out: MutableList<HolderEffect>,
        why: String,
    ) {
        lastStartAt = clock()
        secondStarted = true
        phase = HolderPhase.Finishing
        out += HolderEffect.StartSecond(variant.secondFlags)
        out += HolderEffect.Note("state Finishing: second started ($why); finish in ${variant.finishDelayMillis} ms")
    }

    private fun finish(
        out: MutableList<HolderEffect>,
        reason: String,
    ) {
        phase = HolderPhase.Done
        finalState = reason
        out += HolderEffect.Finish
        out += HolderEffect.Note("state Done: $reason")
    }

    companion object {
        const val SETTLE_MS = 300L
        const val SPLIT_SETTLE_MS = 400L
        const val SPLIT_TIMEOUT_MS = 3_000L
    }
}
