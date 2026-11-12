package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.SourceSubscription

/**
 * A signal that the context lens evaluation reads has changed. [observe] returns a handle that detaches the
 * listener; [onChange] may be called from any thread and must be cheap.
 */
fun interface ContextChanges {
    fun observe(onChange: () -> Unit): SourceSubscription
}
