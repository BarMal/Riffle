package com.riffle.app.launcher

import android.content.Intent

internal data class HomeRoleRequestStateCallbacks(
    val canStartRequest: () -> Boolean,
    val onRequestStarted: () -> Unit,
    val onRequestLaunchFailed: () -> Unit,
)

internal class DefaultHomeRoleRequestHandler(
    private val requestStateCallbacks: HomeRoleRequestStateCallbacks,
    private val createRequestIntent: () -> Intent?,
    private val launchRequest: (Intent) -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
    private val showUnavailable: () -> Unit,
    private val logLaunchFailure: (Throwable) -> Unit,
) {
    fun request() {
        if (requestStateCallbacks.canStartRequest()) {
            createRequestIntentOrRecover()?.let { intent ->
                requestStateCallbacks.onRequestStarted()
                launchRequestOrRecover(intent)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun createRequestIntentOrRecover(): Intent? =
        try {
            createRequestIntent() ?: run {
                recoverUnavailableRequest()
                null
            }
        } catch (failure: Exception) {
            logLaunchFailure(failure)
            recoverUnavailableRequest()
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun launchRequestOrRecover(intent: Intent) {
        try {
            launchRequest(intent)
        } catch (failure: Exception) {
            logLaunchFailure(failure)
            recoverUnavailableRequest()
        }
    }

    private fun recoverUnavailableRequest() {
        requestStateCallbacks.onRequestLaunchFailed()
        refreshPlatformStatuses()
        showUnavailable()
    }
}
