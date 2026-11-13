package com.riffle.app.launcher.stream

import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.stream.todoist.TasksSettingsController
import com.riffle.core.domain.launcher.stream.NextAlarmProvider
import com.riffle.core.domain.launcher.stream.NotConnectedTaskProvider
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.stream.TaskActions
import com.riffle.core.domain.launcher.stream.TaskProvider
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.settings.AliasedSourceEnablement
import com.riffle.core.domain.launcher.workspace.settings.SourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.DelayedTaskScheduler
import com.riffle.core.domain.launcher.workspace.sources.EnablementSourceRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The platform side of the Stream, ready for P4 to hand to `StreamContentProvider`:
 * `StreamContentProvider(registry = platform.registry, alarms = platform.alarms, ...)`, and
 * `platform.actions.perform(content, action)` for every user action.
 *
 * [registry] already applies the user's source on/off choices (the Stream's agenda follows the calendar). Build one
 * per process and share it: it owns the one media-session listener and the one alarm receiver, both attached only
 * while a section is observed.
 */
internal class StreamPlatform(
    val registry: SourceRegistry,
    val alarms: NextAlarmProvider,
    val actions: StreamActionGateway,
    /** The Tasks section's source; [NotConnectedTaskProvider] where there is none. */
    val tasks: TaskProvider = NotConnectedTaskProvider,
    val taskActions: TaskActions? = null,
    val taskSettings: TasksSettingsController? = null,
)

/** A change signal the app raises itself (RSS read state has no platform callback). */
internal class ManualChangeSource : SourceChangeSource {
    private val observers = CopyOnWriteArrayList<() -> Unit>()

    override fun observe(onChanged: () -> Unit): () -> Unit {
        observers += onChanged
        return { observers -= onChanged }
    }

    fun changed() = observers.forEach { it() }
}

/**
 * The registry the Stream reads: [sources] with the user's source on/off choices applied, the agenda following the
 * calendar. The one place the Stream's enablement wiring lives, so the app and its end-to-end test share it.
 */
internal fun enabledStreamRegistry(
    sources: SourceRegistry,
    enablement: SourceEnablement,
): SourceRegistry =
    EnablementSourceRegistry(
        sources,
        AliasedSourceEnablement(enablement, mapOf(StreamSourceIds.AGENDA to SourceIds.CALENDAR)),
    )

/** The one daemon timer behind the Stream's Loading deadline (`StreamLoadingRule`); tasks are a few cheap callbacks. */
internal fun streamLoadingScheduler(
    executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "riffle-stream-timer").apply { isDaemon = true }
        },
): DelayedTaskScheduler =
    DelayedTaskScheduler { delayMillis, task ->
        val handle = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS)
        SourceSubscription { handle.cancel(false) }
    }
