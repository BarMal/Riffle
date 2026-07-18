package com.riffle.app.launcher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultHomeRoleRequestHandlerTest {
    @Test
    fun unavailableRequestShowsRecoveryWithoutStartingSetup() {
        val calls = mutableListOf<String>()

        handler(
            createRequestIntent = { null },
            calls = calls,
        ).request()

        assertEquals(listOf("unavailable"), calls)
    }

    @Test
    fun launchFailureRefreshesStatusAndShowsRecovery() {
        val calls = mutableListOf<String>()

        handler(
            createRequestIntent = { Intent("request-home-role") },
            calls = calls,
            launchRequest = { throw IllegalStateException("unavailable") },
        ).request()

        assertEquals(listOf("started", "launch", "failure", "refresh", "unavailable"), calls)
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

    private fun handler(
        createRequestIntent: () -> Intent?,
        calls: MutableList<String>,
        launchRequest: (Intent) -> Unit = {},
    ): DefaultHomeRoleRequestHandler =
        DefaultHomeRoleRequestHandler(
            createRequestIntent = createRequestIntent,
            onRequestStarted = { calls += "started" },
            launchRequest = { intent ->
                calls += "launch"
                launchRequest(intent)
            },
            refreshPlatformStatuses = { calls += "refresh" },
            showUnavailable = { calls += "unavailable" },
            logLaunchFailure = { calls += "failure" },
        )
}
