package com.riffle.app.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreActiveNotificationRepositoryTest {
    @Test
    fun advancesTheSnapshotRevisionForEveryListenerSave() {
        assertEquals(0L, nextActiveNotificationSnapshotRevision(null))
        assertEquals(1L, nextActiveNotificationSnapshotRevision(0L))
        assertEquals(2L, nextActiveNotificationSnapshotRevision(1L))
    }

    @Test
    fun wrapsTheSnapshotRevisionAfterItsMaximumValue() {
        assertEquals(0L, nextActiveNotificationSnapshotRevision(Long.MAX_VALUE))
    }
}
