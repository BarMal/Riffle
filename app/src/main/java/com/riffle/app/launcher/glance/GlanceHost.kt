package com.riffle.app.launcher.glance

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceState
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult

/**
 * What the glance widgets on Home need from the shell: the Stream's one content provider and action gateway (a
 * glance widget never builds a second pipeline), the apps and icons their cards are lit from, and the way to open the
 * Stream on a section. Provided around Home by `HomeDestination`; absent in previews, where a glance widget draws its
 * loading state and nothing else.
 */
internal class GlanceHost(
    val stream: StreamHost,
    val installedApps: List<InstalledApp>,
    val appIconLoader: AppIconLoader,
    val reducedMotion: Boolean,
    val openStream: (StreamFocusTarget) -> Unit,
) {
    fun environment(): StreamEnvironment =
        StreamEnvironment(
            imageLoader = stream.imageLoader,
            appIconLoader = appIconLoader,
            installedApps = installedApps,
            reducedMotion = reducedMotion,
            formats = stream.formats,
            nowEpochMillis = stream.formats.now(),
        )
}

internal val LocalGlanceHost = staticCompositionLocalOf<GlanceHost?> { null }

/**
 * What [kind] draws now. The section is observed only while this widget is composed (its Home page is showing) and
 * the activity is started: the subscription is cancelled when either ends, and the upstream source is dropped with
 * the last observer. Results arrive off the main thread and are posted to it.
 */
@Composable
internal fun rememberGlanceState(
    host: GlanceHost?,
    kind: GlanceWidgetKind,
): GlanceState {
    val started = rememberIsStarted()
    var result by remember(host, kind.section) { mutableStateOf<StreamSectionResult?>(null) }
    if (host != null && started) {
        DisposableEffect(host.stream.provider, kind.section) {
            val main = Handler(Looper.getMainLooper())
            val subscription = host.stream.provider.observe(kind.section) { value -> main.post { result = value } }
            onDispose { subscription.cancel() }
        }
    }
    return remember(kind, result) { GlanceWidgets.stateFor(kind, result) }
}

@Composable
private fun rememberIsStarted(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return started
}

/** The Stream section a glance widget's body opens. */
internal fun GlanceWidgetKind.focusTarget(): StreamFocusTarget =
    if (section == StreamSection.NOW) StreamFocusTarget.Now else StreamFocusTarget.Section(section)
