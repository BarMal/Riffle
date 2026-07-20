package com.riffle.app.launcher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultHomeRoleRequestHandlerTest {
    @Test
    fun unavailableRequestClearsPendingRecoveryStateAndRefreshesStatus() {
        val calls = mutableListOf<String>()

        handler(
            createRequestIntent = { null },
            calls = calls,
        ).request()

        assertEquals(listOf("cleared", "refresh", "unavailable"), calls)
    }

    @Test
    fun requestIntentCreationFailureRefreshesStatusAndShowsRecovery() {
        val calls = mutableListOf<String>()

        handler(
            createRequestIntent = { throw IllegalStateException("unavailable") },
            calls = calls,
        ).request()

        assertEquals(listOf("failure", "cleared", "refresh", "unavailable"), calls)
    }

    @Test
    fun launchFailureRefreshesStatusAndShowsRecovery() {
        val calls = mutableListOf<String>()

        handler(
            createRequestIntent = { Intent("request-home-role") },
            calls = calls,
            launchRequest = { throw IllegalStateException("unavailable") },
        ).request()

        assertEquals(listOf("started", "launch", "failure", "cleared", "refresh", "unavailable"), calls)
    }

    @Test
    fun fatalLaunchErrorIsNotHandledAsUnavailable() {
        val calls = mutableListOf<String>()
        val fatalError = AssertionError("fatal")

        val thrown =
            assertThrows(AssertionError::class.java) {
                handler(
                    createRequestIntent = { Intent("request-home-role") },
                    calls = calls,
                    launchRequest = { throw fatalError },
                )
                    .request()
            }

        assertSame(fatalError, thrown)
        assertEquals(listOf("started", "launch"), calls)
    }

    @Test
    fun resolvableRequestStartsSetupAndLaunchesIntent() {
        val calls = mutableListOf<String>()
        val intent = Intent("request-home-role")

        handler(
            createRequestIntent = { intent },
            calls = calls,
        ).request()

        assertEquals(listOf("started", "launch"), calls)
    }

    @Test
    fun repeatedRequestWhilePendingLaunchesOnlyOnce() {
        val calls = mutableListOf<String>()
        var requestPending = false
        val handler =
            handler(
                createRequestIntent = {
                    calls += "create"
                    Intent("request-home-role")
                },
                calls = calls,
                canStartRequest = { !requestPending },
                onRequestStarted = {
                    calls += "started"
                    requestPending = true
                },
            )

        handler.request()
        handler.request()

        assertEquals(listOf("create", "started", "launch"), calls)
    }

    private fun handler(
        createRequestIntent: () -> Intent?,
        calls: MutableList<String>,
        launchRequest: (Intent) -> Unit = {},
        canStartRequest: () -> Boolean = { true },
        onRequestStarted: () -> Unit = { calls += "started" },
    ): DefaultHomeRoleRequestHandler =
        DefaultHomeRoleRequestHandler(
            requestStateCallbacks =
                HomeRoleRequestStateCallbacks(
                    canStartRequest = canStartRequest,
                    onRequestStarted = onRequestStarted,
                    onRequestLaunchFailed = { calls += "cleared" },
                ),
            createRequestIntent = createRequestIntent,
            launchRequest = { intent ->
                calls += "launch"
                launchRequest(intent)
            },
            refreshPlatformStatuses = { calls += "refresh" },
            showUnavailable = { calls += "unavailable" },
            logLaunchFailure = { calls += "failure" },
        )
}
