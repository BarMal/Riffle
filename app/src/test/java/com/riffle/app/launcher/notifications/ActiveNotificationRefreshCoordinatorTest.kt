package com.riffle.app.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationRefreshCoordinatorTest {
    @Test
    fun dispatchesNotificationRefreshOnMainThreadWhenNotificationsChange() {
        val source = FakeNotificationChangeSource()
        val dispatchedActions = mutableListOf<() -> Unit>()
        var refreshCount = 0
        val coordinator =
            ActiveNotificationRefreshCoordinator(
                notificationChangeSource = source,
                dispatchOnMainThread = { action -> dispatchedActions += action },
                refreshNotifications = { refreshCount += 1 },
            )

        coordinator.start()
        source.emitChanged()

        assertEquals(0, refreshCount)
        assertEquals(1, dispatchedActions.size)

        dispatchedActions.single().invoke()

        assertEquals(1, refreshCount)
    }

    private class FakeNotificationChangeSource : ActiveNotificationChangeSource {
        private var onChanged: (() -> Unit)? = null

        override fun observeActiveNotifications(onChanged: () -> Unit) {
            this.onChanged = onChanged
        }

        fun emitChanged() {
            onChanged?.invoke()
        }
    }
}
