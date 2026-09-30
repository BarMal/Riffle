package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Handle to an in-flight evaluation. Cancelling suppresses delivery; it does not interrupt the work. */
fun interface LensEvaluation {
    fun cancel()
}

/**
 * Runs a [LensEvaluator] on a caller-supplied [Executor] (a background pool in the app, a direct executor
 * in tests) so lens evaluation stays off the main thread without the domain depending on coroutines.
 *
 * Callbacks run on [executor]'s thread; hop to the main thread in the caller if needed. The inputs are
 * snapshotted (`toList`) before hand-off so later mutation by the caller cannot race the evaluation.
 */
class AsyncLensEvaluator(
    private val executor: Executor,
    private val evaluator: LensEvaluator = DefaultLensEvaluator,
) {
    fun evaluate(
        lens: Lens,
        items: List<Item>,
        context: LensEvaluationContext,
        onError: (Throwable) -> Unit = {},
        onResult: (LensResult) -> Unit,
    ): LensEvaluation {
        val cancelled = AtomicBoolean(false)
        val snapshot = items.toList()
        executor.execute {
            runCatching { evaluator.evaluate(lens, snapshot, context) }
                .onSuccess { if (!cancelled.get()) onResult(it) }
                .onFailure { if (!cancelled.get()) onError(it) }
        }
        return LensEvaluation { cancelled.set(true) }
    }
}
