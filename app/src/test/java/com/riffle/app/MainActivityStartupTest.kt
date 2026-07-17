package com.riffle.app

import com.riffle.core.domain.launcher.HomeRoleStatus
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun platformEventFlowIsIgnoredWhenMappedEventReadFails() =
        runBlocking {
            assertEquals(
                emptyList<String>(),
                startupPlatformFlow {
                    flow<String> { error("Window metrics are unavailable") }
                }.toList(),
            )
        }
}
