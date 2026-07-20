package com.riffle.app.launcher

import android.content.Intent

internal class DefaultHomeRoleRequestHandler(
    private val createRequestIntent: () -> Intent?,
    private val onRequestStarted: () -> Unit,
    private val onRequestLaunchFailed: () -> Unit,
    private val launchRequest: (Intent) -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
    private val showUnavailable: () -> Unit,
    private val logLaunchFailure: (Throwable) -> Unit,
) {
    @Suppress("TooGenericExceptionCaught")
    fun request() {
        val intent = createRequestIntent()
        if (intent == null) {
            showUnavailable()
            return
        }

        onRequestStarted()
        try {
            launchRequest(intent)
        } catch (failure: Exception) {
            logLaunchFailure(failure)
            onRequestLaunchFailed()
            refreshPlatformStatuses()
            showUnavailable()
        }
    }
}
