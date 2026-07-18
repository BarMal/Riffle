package com.riffle.app.launcher

import android.content.Intent

internal class DefaultHomeRoleRequestHandler(
    private val createRequestIntent: () -> Intent?,
    private val onRequestStarted: () -> Unit,
    private val launchRequest: (Intent) -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
    private val showUnavailable: () -> Unit,
    private val logLaunchFailure: (Throwable) -> Unit,
) {
    fun request() {
        val intent = createRequestIntent()
        if (intent == null) {
            showUnavailable()
            return
        }

        onRequestStarted()
        runCatching { launchRequest(intent) }
            .onFailure { failure ->
                logLaunchFailure(failure)
                refreshPlatformStatuses()
                showUnavailable()
            }
    }
}
