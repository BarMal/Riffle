package com.riffle.app.launcher.stream

import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.stream.todoist.TasksSettingsController
import com.riffle.core.domain.launcher.stream.NextAlarmProvider
import com.riffle.core.domain.launcher.stream.NotConnectedTaskProvider
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.TaskActions
import com.riffle.core.domain.launcher.stream.TaskProvider
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import java.util.concurrent.CopyOnWriteArrayList

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
