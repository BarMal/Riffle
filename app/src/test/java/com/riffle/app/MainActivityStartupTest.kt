package com.riffle.app

import com.riffle.core.domain.launcher.HomeRoleStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MainActivityStartupTest {
    @Test
    fun platformStatusReadUsesFallbackWhenPlatformCallFails() {
        assertEquals(
            HomeRoleStatus.UNKNOWN,
            startupPlatformValue(fallback = HomeRoleStatus.UNKNOWN) {
                error("Role service is unavailable")
            },
        )
    }

    @Test
    fun optionalPlatformEventIsIgnoredWhenPlatformCallFails() {
        assertNull(
            startupPlatformValueOrNull<String> {
                error("Window metrics are unavailable")
            },
        )
    }

    @Test
    fun platformStatusReadPropagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            startupPlatformValue(fallback = HomeRoleStatus.UNKNOWN) {
                throw CancellationException("Startup was cancelled")
            }
        }
        assertThrows(CancellationException::class.java) {
            startupPlatformValueOrNull<String> {
                throw CancellationException("Startup was cancelled")
            }
        }
    }

    @Test
    fun platformEventFlowIsIgnoredWhenDeviceClassFlowCreationFails() =
        runBlocking {
            assertEquals(
                emptyList<String>(),
                startupPlatformFlow<String> {
                    error("Window event flow is unavailable")
                }.toList(),
            )
        }

    @Test
    fun platformEventFlowIsIgnoredWhenEventEmissionFails() =
        runBlocking {
            assertEquals(
                emptyList<String>(),
                startupPlatformFlow {
                    flow<String> { error("Window metrics are unavailable") }
                }.toList(),
            )
        }

    @Test
    fun platformEventFlowIsIgnoredWhenMappedEventReadFails() =
        runBlocking {
            assertEquals(
                emptyList<String>(),
                startupPlatformFlow {
                    flowOf("event").map<String, String> {
                        error("Window metrics are unavailable")
                    }
                }.toList(),
            )
        }
}
