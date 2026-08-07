package com.riffle.app.launcher.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun interface ActiveNotificationChangeSource {
    fun observeActiveNotifications(onChanged: () -> Unit): () -> Unit
}

fun interface NotificationListenerConnectionChangeSource {
    fun observeConnection(onChanged: () -> Unit): () -> Unit
}

class ActiveNotificationRefreshCoordinator(
    private val notificationChangeSource: ActiveNotificationChangeSource,
    private val connectionChangeSource: NotificationListenerConnectionChangeSource =
        NotificationListenerConnectionChangeSource(RiffleNotificationListenerConnection::observeConnection),
    private val dispatchOnMainThread: (() -> Unit) -> Unit,
    private val refreshNotifications: () -> Unit,
    private val refreshPlatformStatuses: () -> Unit,
    private val isListenerConnected: () -> Boolean = RiffleNotificationListenerConnection::isConnected,
    private val requestListenerRebind: () -> Unit = {},
) : DefaultLifecycleObserver {
    private var removeNotificationObserver: (() -> Unit)? = null
    private var removeConnectionObserver: (() -> Unit)? = null
    private val isStarted = AtomicBoolean(false)
    private val refreshPending = AtomicBoolean(false)
    private val observerGeneration = AtomicLong(0)

    fun start() {
        if (removeNotificationObserver != null) return
        isStarted.set(true)
        observerGeneration.incrementAndGet()
        removeNotificationObserver =
            notificationChangeSource.observeActiveNotifications {
                dispatchRefresh()
            }
        removeConnectionObserver = connectionChangeSource.observeConnection(::dispatchRefresh)
    }

    fun stop() {
        isStarted.set(false)
        refreshPending.set(false)
        observerGeneration.incrementAndGet()
        removeNotificationObserver?.invoke()
        removeNotificationObserver = null
        removeConnectionObserver?.invoke()
        removeConnectionObserver = null
    }

    override fun onStart(owner: LifecycleOwner) {
        // The platform can silently drop the listener binding without ever calling
        // onListenerDisconnected (observed on OEM power management). Home is foregrounded often
        // enough that this doubles as a cheap health check, not just a lifecycle formality.
        if (!isListenerConnected()) requestListenerRebind()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stop()
    }

    /** Coalesce listener bursts; the queued refresh reads the newest persisted snapshot. */
    private fun dispatchRefresh() {
        if (!isStarted.get() || !refreshPending.compareAndSet(false, true)) return
        val generation = observerGeneration.get()
        dispatchOnMainThread {
            refreshPending.set(false)
            if (!isStarted.get() || observerGeneration.get() != generation) return@dispatchOnMainThread
            refreshPlatformStatuses()
            refreshNotifications()
        }
    }
}
