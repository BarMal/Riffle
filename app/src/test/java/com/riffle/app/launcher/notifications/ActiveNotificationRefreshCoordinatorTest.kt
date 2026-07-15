package com.riffle.app.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationRefreshCoordinatorTest {
    @Test
    fun dispatchesNotificationRefreshOnMainThreadWhenNotificationsChange() {
        val source = FakeNotificationChangeSource()
        val connectionSource = FakeConnectionChangeSource()
        val dispatchedActions = mutableListOf<() -> Unit>()
        var refreshCount = 0
        var platformStatusRefreshCount = 0
        val refreshOrder = mutableListOf<String>()
        val coordinator =
            ActiveNotificationRefreshCoordinator(
                notificationChangeSource = source,
                connectionChangeSource = connectionSource,
                dispatchOnMainThread = { action -> dispatchedActions += action },
                refreshNotifications = {
                    refreshCount += 1
                    refreshOrder += "notifications"
                },
                refreshPlatformStatuses = {
                    platformStatusRefreshCount += 1
                    refreshOrder += "platform statuses"
                },
            )

        coordinator.start()
        source.emitChanged()

        assertEquals(0, refreshCount)
        assertEquals(1, dispatchedActions.size)

        dispatchedActions.single().invoke()

        assertEquals(1, refreshCount)
        assertEquals(1, platformStatusRefreshCount)
        assertEquals(listOf("platform statuses", "notifications"), refreshOrder)

        connectionSource.emitChanged()
        dispatchedActions[1].invoke()

        assertEquals(2, refreshCount)
        assertEquals(2, platformStatusRefreshCount)

        coordinator.stop()
        connectionSource.emitChanged()
        assertEquals(2, dispatchedActions.size)
    }

    private class FakeNotificationChangeSource : ActiveNotificationChangeSource {
        private var onChanged: (() -> Unit)? = null

        override fun observeActiveNotifications(onChanged: () -> Unit): () -> Unit {
            this.onChanged = onChanged
            return { this.onChanged = null }
        }

        fun emitChanged() {
            onChanged?.invoke()
        }
    }

    private class FakeConnectionChangeSource : NotificationListenerConnectionChangeSource {
        private var onChanged: (() -> Unit)? = null

        override fun observeConnection(onChanged: () -> Unit): () -> Unit {
            this.onChanged = onChanged
            return { this.onChanged = null }
        }

        fun emitChanged() {
            onChanged?.invoke()
        }
    }
}
