package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluation

/** Whether a lens can be drawn yet, summarising the states of the sources it reads. */
enum class LensAvailability {
    LOADING,
    READY,
    PERMISSION_REQUIRED,

    /** Every source the lens reads was turned off by the user (Settings > Sources). */
    OFF,
    UNAVAILABLE,
}

/**
 * What a container draws for one lens: [result] is present exactly when [availability] is
 * [LensAvailability.READY] (an empty result is still ready). Transient; never persisted.
 */
data class LensOutput(
    val availability: LensAvailability,
    val result: LensResult? = null,
) {
    init {
        require((availability == LensAvailability.READY) == (result != null)) {
            "A result is present exactly when the output is ready."
        }
    }

    companion object {
        val Loading = LensOutput(LensAvailability.LOADING)
        val PermissionRequired = LensOutput(LensAvailability.PERMISSION_REQUIRED)
        val Off = LensOutput(LensAvailability.OFF)
        val Unavailable = LensOutput(LensAvailability.UNAVAILABLE)

        fun ready(result: LensResult): LensOutput = LensOutput(LensAvailability.READY, result)

        /**
         * One availability for a lens reading several sources. Any ready source makes the lens ready (the
         * items that exist are better than none, and a container cannot say which source is missing);
         * otherwise still loading beats a missing permission beats a source the user turned off beats unavailable.
         */
        fun availabilityOf(states: Collection<SourceState>): LensAvailability =
            when {
                states.isEmpty() || states.any { it is SourceState.Ready } -> LensAvailability.READY
                states.any { it is SourceState.Loading } -> LensAvailability.LOADING
                states.any { it is SourceState.PermissionRequired } -> LensAvailability.PERMISSION_REQUIRED
                states.any { it is SourceState.Off } -> LensAvailability.OFF
                else -> LensAvailability.UNAVAILABLE
            }
    }
}

/** Receives a lens's outputs. May be called from any thread, never after the observation is cancelled. */
fun interface LensOutputListener {
    fun onOutput(output: LensOutput)
}

/**
 * The seam between containers and the source/lens pipeline. A container asks for a [Lens] and is told
 * its [LensOutput] now (usually [LensOutput.Loading] first) and again whenever it changes. Implementations
 * share one subscription per source however many lenses read it, evaluate off the main thread, and stop
 * all work when the returned handle is cancelled.
 */
interface LensResultProvider {
    fun observe(
        lens: Lens,
        listener: LensOutputListener,
    ): LensEvaluation
}

/** A provider with fixed answers, for previews, screenshot tests and container tests. Never subscribes. */
class StaticLensResultProvider(private val answer: (Lens) -> LensOutput) : LensResultProvider {
    constructor(output: LensOutput) : this({ output })

    override fun observe(
        lens: Lens,
        listener: LensOutputListener,
    ): LensEvaluation {
        listener.onOutput(answer(lens))
        return LensEvaluation { }
    }
}
