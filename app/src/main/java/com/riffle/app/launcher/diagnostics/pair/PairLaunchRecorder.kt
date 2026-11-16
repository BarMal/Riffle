package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One thing the launcher itself saw after a launch, [offsetMillis] after it. */
data class PairObservation(
    val offsetMillis: Long,
    val text: String,
)

/** What the launcher's window looks like: multi-window mode (null: not known) and its bounds as text. */
data class PairWindowState(
    val multiWindow: Boolean? = null,
    val bounds: String? = null,
) {
    val description: String get() = "multi-window=${multiWindow ?: "unknown"}, bounds=${bounds ?: "unknown"}"
}

/** The launcher activity's side: it reports each lifecycle callback and window change here. */
interface PairActivitySignals {
    fun onSignal(
        name: String,
        state: PairWindowState,
    )
}

/** The experiment's side: asks for a time-boxed watch of the launcher and gets the observations back. */
fun interface PairLaunchWatcher {
    /** Starts watching from [launchMillis]. [onTrace] gets the whole trace so far, and `open == false` at the end. */
    fun begin(
        launchMillis: Long,
        onTrace: (events: List<PairObservation>, open: Boolean) -> Unit,
    )

    companion object {
        /** Watches nothing; the trace stays empty. */
        val None = PairLaunchWatcher { _, _ -> }
    }
}

/**
 * Records what the launcher's own activity observes for [windowMillis] after a launch: lifecycle callbacks with their
 * offsets, `isInMultiWindowMode` transitions and window bounds changes. It sees only the launcher's window; what the
 * other two apps do is not observable from here. The only timer is one coroutine delay in [scope] (the app's own scope,
 * so it outlives the launcher being stopped); a new [begin] or the end of the window cancels it. All calls are expected
 * on the main thread.
 */
class PairLaunchRecorder(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : PairLaunchWatcher,
    PairActivitySignals {
    private class Session(
        val launchMillis: Long,
        val onTrace: (List<PairObservation>, Boolean) -> Unit,
        val events: MutableList<PairObservation> = mutableListOf(),
        var timer: Job? = null,
    )

    private var session: Session? = null
    private var last = PairWindowState()

    override fun begin(
        launchMillis: Long,
        onTrace: (List<PairObservation>, Boolean) -> Unit,
    ) {
        end("superseded by a newer launch")
        val s = Session(launchMillis, onTrace)
        session = s
        add(s, 0, "launcher window at launch: ${last.description}")
        publish(s, open = true)
        s.timer =
            scope.launch {
                pause(windowMillis)
                if (session === s) end("window over")
            }
    }

    override fun onSignal(
        name: String,
        state: PairWindowState,
    ) {
        val before = last
        last = state
        val s = session ?: return
        val offset = clock() - s.launchMillis
        add(s, offset, name)
        if (state.multiWindow != before.multiWindow && !name.startsWith("onMultiWindowModeChanged")) {
            add(
                s,
                offset,
                "isInMultiWindowMode ${before.multiWindow ?: "unknown"} -> ${state.multiWindow ?: "unknown"}",
            )
        }
        if (state.bounds != before.bounds) {
            add(s, offset, "window bounds ${before.bounds ?: "unknown"} -> ${state.bounds ?: "unknown"}")
        }
        publish(s, open = true)
    }

    private fun end(reason: String) {
        val s = session ?: return
        session = null
        s.timer?.cancel()
        add(
            s,
            (clock() - s.launchMillis).coerceAtLeast(0),
            "observation ended ($reason); launcher window: ${last.description}",
        )
        publish(s, open = false)
    }

    private fun add(
        s: Session,
        offset: Long,
        text: String,
    ) {
        if (s.events.size < MAX_EVENTS) s.events += PairObservation(offset, text)
    }

    private fun publish(
        s: Session,
        open: Boolean,
    ) = s.onTrace(s.events.toList(), open)

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 3_000L
        private const val MAX_EVENTS = 60
    }
}
