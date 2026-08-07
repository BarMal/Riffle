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

    @Test
    fun coalescesBurstCallbacksAndDropsQueuedWorkAfterStop() {
        val source = FakeNotificationChangeSource()
        val queuedActions = mutableListOf<() -> Unit>()
        var refreshes = 0
        val coordinator =
            ActiveNotificationRefreshCoordinator(
                notificationChangeSource = source,
                connectionChangeSource = FakeConnectionChangeSource(),
                dispatchOnMainThread = { action -> queuedActions += action },
                refreshNotifications = { refreshes += 1 },
                refreshPlatformStatuses = {},
            )

        coordinator.start()
        source.emitChanged()
        source.emitChanged()

        assertEquals(1, queuedActions.size)
        queuedActions.single().invoke()
        assertEquals(1, refreshes)

        source.emitChanged()
        coordinator.stop()
        queuedActions.last().invoke()

        assertEquals(1, refreshes)
    }

    @Test
    fun requestsRebindOnStartWhenListenerNotConnected() {
        var rebindRequests = 0
        val coordinator =
            ActiveNotificationRefreshCoordinator(
                notificationChangeSource = FakeNotificationChangeSource(),
                connectionChangeSource = FakeConnectionChangeSource(),
                dispatchOnMainThread = { action -> action() },
                refreshNotifications = {},
                refreshPlatformStatuses = {},
                isListenerConnected = { false },
                requestListenerRebind = { rebindRequests += 1 },
            )

        coordinator.onStart(FakeLifecycleOwner)

        assertEquals(1, rebindRequests)
    }

    @Test
    fun doesNotRequestRebindOnStartWhenListenerAlreadyConnected() {
        var rebindRequests = 0
        val coordinator =
            ActiveNotificationRefreshCoordinator(
                notificationChangeSource = FakeNotificationChangeSource(),
                connectionChangeSource = FakeConnectionChangeSource(),
                dispatchOnMainThread = { action -> action() },
                refreshNotifications = {},
                refreshPlatformStatuses = {},
                isListenerConnected = { true },
                requestListenerRebind = { rebindRequests += 1 },
            )

        coordinator.onStart(FakeLifecycleOwner)

        assertEquals(0, rebindRequests)
    }

    private object FakeLifecycleOwner : androidx.lifecycle.LifecycleOwner {
        override val lifecycle: androidx.lifecycle.Lifecycle
            get() = throw UnsupportedOperationException("not used by the coordinator under test")
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
