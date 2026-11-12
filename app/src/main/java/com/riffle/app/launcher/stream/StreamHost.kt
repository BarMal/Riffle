package com.riffle.app.launcher.stream

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.expressions.LocalItemHider
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamHideItems
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * What the shell needs to show the Stream: the content provider (one shared subscription per source, nothing read until
 * a section is observed), the layouts, the one action gateway and the image loader. Built once per process by the
 * activity. Public only so the shell can take it; everything it holds is internal.
 */
@Suppress("LongParameterList")
class StreamHost internal constructor(
    internal val provider: StreamContentProvider,
    internal val layouts: CachedStreamLayoutRepository,
    internal val actions: StreamActionGateway,
    internal val imageLoader: ExpressionImageLoader,
    internal val formats: StreamFormats = SystemStreamFormats(),
    /** The existing explicit access flow for a source (notification access, calendar, usage access). */
    internal val requestAccess: (SourceId) -> Unit = {},
    /** Turns the sources of a section back on: only ever called from the section's own "Turn on" button. */
    internal val enableSources: (List<SourceId>) -> Unit = {},
    /** The posture the Stream is shown on, which the provider reads the options of; the UI keeps it current. */
    internal val posture: StreamPostureHolder = StreamPostureHolder(),
)

/** The device class the Stream is drawn for, readable from the provider's threads. */
internal class StreamPostureHolder {
    @Volatile var deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE
}

/**
 * The stateful Stream: reads the layout of [deviceClass], observes each enabled section (results come off the main
 * thread and are posted back to it), runs actions off the main thread and shows a snackbar when one fails.
 */
@Composable
internal fun StreamDestination(
    host: StreamHost,
    deviceClass: HomeLayoutDeviceClass,
    installedApps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    reducedMotion: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(host) { host.layouts.initialize() }
    SideEffect { host.posture.deviceClass = deviceClass }
    val layouts by host.layouts.layouts.collectAsState()
    val layout = layouts.forPosture(deviceClass)
    val enabledSections = layout.enabledSections.map { it.section }
    val results = rememberStreamSectionResults(host.provider, enabledSections)
    val scope = rememberCoroutineScope()
    val failures =
        remember(
            host,
        ) { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    val hider = LocalItemHider.current
    val callbacks =
        StreamCallbacks(
            onAction = { content, action ->
                scope.launch(Dispatchers.Default) {
                    val outcome =
                        runCatching {
                            host.actions.perform(
                                content,
                                action,
                            )
                        }.getOrDefault(StreamActionResult.Failed)
                    if (outcome == StreamActionResult.Failed || outcome == StreamActionResult.Unavailable) {
                        failures.tryEmit(
                            Unit,
                        )
                    }
                }
            },
            onLayoutAction = { action: StreamLayoutAction -> host.layouts.update(deviceClass, action) },
            onRequestAccess = { sources ->
                sources.firstOrNull()?.let { host.requestAccess(StreamSourceIds.userSourceOf(it)) }
            },
            onEnableSection = {
                    section ->
                host.enableSources(section.itemSources.map(StreamSourceIds::userSourceOf).distinct())
            },
            onHide =
                hider?.let { itemHider ->
                    {
                            content,
                            groupKey,
                            kind,
                        ->
                        StreamHideItems.itemFor(content, groupKey)?.let { itemHider.hide(it, kind) }
                    }
                },
        )
    val env =
        StreamEnvironment(
            imageLoader = host.imageLoader,
            appIconLoader = appIconLoader,
            installedApps = installedApps,
            reducedMotion = reducedMotion,
            formats = host.formats,
            nowEpochMillis = host.formats.now(),
        )
    StreamSurface(
        layout = layout,
        results = results,
        env = env,
        callbacks = callbacks,
        modifier = modifier,
        contentPadding = contentPadding,
        failures = failures,
    )
}

/**
 * The latest result of each of [sections], observed while the Stream is composed. A section's subscription lives only
 * as long as it is shown; callbacks arrive on the provider's threads and are posted to the main thread.
 */
@Composable
internal fun rememberStreamSectionResults(
    provider: StreamContentProvider,
    sections: List<StreamSection>,
): Map<StreamSection, StreamSectionResult> {
    val results = remember(provider) { mutableStateMapOf<StreamSection, StreamSectionResult>() }
    sections.forEach { section ->
        key(section) {
            DisposableEffect(provider, section) {
                val main = Handler(Looper.getMainLooper())
                val subscription =
                    provider.observe(section) { result -> main.post { results[section] = result } }
                onDispose { subscription.cancel() }
            }
        }
    }
    return results
}
