package com.riffle.app.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveNotificationSnapshotTest {
    @Test
    fun preservesStoredNotificationsWhenPlatformSnapshotThrows() {
        val snapshot =
            activeNotificationSnapshotOrNull<Int, String>(
                activeNotifications = { throw SecurityException("Listener is not connected") },
                mapper = Int::toString,
            )

        assertNull(snapshot)
    }

    @Test
    fun preservesStoredNotificationsWhenOnePlatformNotificationCannotBeMapped() {
        val snapshot =
            activeNotificationSnapshotOrNull(
                activeNotifications = { arrayOf(1, 2) },
                mapper = { value ->
                    if (value == 2) error("Malformed notification")
                    value.toString()
                },
            )

        assertNull(snapshot)
    }

    @Test
    fun mapsCompletePlatformSnapshot() {
        val snapshot =
            activeNotificationSnapshotOrNull(
                activeNotifications = { arrayOf(1, 2) },
                mapper = Int::toString,
            )

        assertEquals(listOf("1", "2"), snapshot)
    }

    @Test
    fun ignoresPersistenceFailureAfterReadingPlatformSnapshot() {
        var savedNotifications: List<String>? = null

        saveActiveNotificationSnapshot(
            activeNotifications = { arrayOf(1, 2) },
            mapper = Int::toString,
            saveNotifications = { notifications ->
                savedNotifications = notifications
                error("Data store is unavailable")
            },
        )

        assertEquals(listOf("1", "2"), savedNotifications)
    }

    @Test
    fun ignoresListenerSetupFailureBeforeReadingPlatformSnapshot() {
        var setupStarted = false

        ignoreNotificationListenerFailure {
            setupStarted = true
            error("Notification storage is unavailable")
        }

        assertEquals(true, setupStarted)
    }
}
