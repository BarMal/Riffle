package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCountBadgeTest {
    @Test
    fun usesRawCountAtOrBelowBadgeLimit() {
        assertEquals("1", 1.notificationBadgeLabel())
        assertEquals("99", 99.notificationBadgeLabel())
    }

    @Test
    fun capsCountsAboveBadgeLimit() {
        assertEquals("99+", 100.notificationBadgeLabel())
        assertEquals("99+", 250.notificationBadgeLabel())
    }
}
