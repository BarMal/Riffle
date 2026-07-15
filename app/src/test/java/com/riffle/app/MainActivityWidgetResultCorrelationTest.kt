package com.riffle.app

import com.riffle.core.domain.launcher.home.HostedWidgetId
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityWidgetResultCorrelationTest {
    @Test
    fun resultWithoutHostedIdUsesReconstructedDurableTransactionId() {
        assertEquals(
            HostedWidgetId(42),
            resolveHostedWidgetResultId(
                returnedHostedWidgetId = null,
                expectedHostedWidgetId = HostedWidgetId(42),
            ),
        )
    }

    @Test
    fun explicitHostedIdIsPreservedForStaleResultRejection() {
        assertEquals(
            HostedWidgetId(41),
            resolveHostedWidgetResultId(
                returnedHostedWidgetId = HostedWidgetId(41),
                expectedHostedWidgetId = HostedWidgetId(42),
            ),
        )
    }
}
