package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal const val STREAM_SURFACE_TEST_TAG = "stream-surface"
internal const val STREAM_LIST_TEST_TAG = "stream-list"

private const val CLOCK_TICK_MILLIS = 30_000L

/**
 * The Stream (ADR 0003): the actions centre. Draws the sections [layout] turns on, in its order, each as a Stack of
 * cards or a List of rows, from the [results] the content provider delivered. Stateless: layout edits and every user
 * action go out through [callbacks], and [contentPadding] keeps it clear of the system bars and the Dock under it.
 * Text-heavy content stops at 640 dp. "Show as" redraws at once and offers Undo.
 */
@Composable
@Suppress("LongMethod")
internal fun StreamSurface(
    layout: StreamLayout,
    results: Map<StreamSection, StreamSectionResult>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    failures: Flow<Unit> = emptyFlow(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val latestLayout by rememberUpdatedState(layout)
    val liveEnv = env.withLiveClock()
    val failedMessage = stringResource(R.string.stream_action_failed)
    LaunchedEffect(failures) {
        failures.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(failedMessage, duration = SnackbarDuration.Short)
        }
    }
    val undo = stringResource(R.string.stream_undo)
    val onShowAs: (StreamSection, StreamView) -> Unit = { section, view ->
        val before = latestLayout
        if (before.viewOf(section) != view) {
            callbacks.onLayoutAction(StreamLayoutAction.SetView(section, view))
            val message =
                context.getString(
                    R.string.stream_shown_as,
                    context.getString(section.titleRes()),
                    context.getString(view.titleRes()),
                )
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val outcome = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
                if (outcome == SnackbarResult.ActionPerformed) {
                    callbacks.onLayoutAction(
                        StreamLayoutAction.Restore(before),
                    )
                }
            }
        }
    }
    val onLayoutAction: (StreamLayoutAction) -> Unit = { action ->
        val before = latestLayout
        callbacks.onLayoutAction(action)
        if (action is StreamLayoutAction.SetEnabled && !action.enabled) {
            val message =
                context.getString(
                    R.string.stream_section_hidden,
                    context.getString(action.section.titleRes()),
                )
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val outcome = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
                if (outcome == SnackbarResult.ActionPerformed) {
                    callbacks.onLayoutAction(
                        StreamLayoutAction.Restore(before),
                    )
                }
            }
        }
    }
    val surfaceCallbacks =
        StreamCallbacks(
            onAction = callbacks.onAction,
            onLayoutAction = onLayoutAction,
            onRequestAccess = callbacks.onRequestAccess,
            onEnableSection = callbacks.onEnableSection,
            onHide = callbacks.onHide,
        )
    Box(modifier = modifier.fillMaxSize().testTag(STREAM_SURFACE_TEST_TAG), contentAlignment = Alignment.TopCenter) {
        val enabled = layout.enabledSections
        LazyColumn(
            modifier = Modifier.widthIn(max = StreamMaxReadableWidth).fillMaxSize().testTag(STREAM_LIST_TEST_TAG),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
        ) {
            item(key = "title") {
                Row(modifier = Modifier.padding(horizontal = RiffleSpacing.l)) {
                    GlassSurface(
                        weight = GlassWeight.Thin,
                        shape = RiffleShapes.full,
                        tint = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.stream_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier =
                                Modifier
                                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.xs)
                                    .semantics { heading() },
                        )
                    }
                }
            }
            items(enabled, key = { it.section.storedId }) { entry ->
                val index = enabled.indexOf(entry)
                StreamSectionBlock(
                    section = entry.section,
                    view = entry.view,
                    result = results[entry.section] ?: StreamSectionResult.Loading,
                    env = liveEnv,
                    callbacks = surfaceCallbacks,
                    canMoveUp = index > 0,
                    canMoveDown = index < enabled.lastIndex,
                    onShowAs = { view -> onShowAs(entry.section, view) },
                    modifier = Modifier.padding(horizontal = RiffleSpacing.l),
                )
            }
            val hidden = layout.sections.filter { !it.enabled }
            items(hidden, key = { "hidden-${it.section.storedId}" }) { entry ->
                StreamTextButton(
                    text =
                        stringResource(
                            R.string.stream_show_hidden_section,
                            stringResource(entry.section.titleRes()),
                        ),
                    onClick = {
                        surfaceCallbacks.onLayoutAction(
                            StreamLayoutAction.SetEnabled(entry.section, true),
                        )
                    },
                    modifier = Modifier.padding(horizontal = RiffleSpacing.l),
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(contentPadding)
                    .padding(horizontal = RiffleSpacing.l),
        )
    }
}

/** The environment with its clock refreshed every half minute (countdowns), unless the formats are fixed. */
@Composable
private fun StreamEnvironment.withLiveClock(): StreamEnvironment {
    var now by remember(formats) { mutableLongStateOf(nowEpochMillis) }
    if (formats.live) {
        LaunchedEffect(formats) {
            while (true) {
                delay(CLOCK_TICK_MILLIS)
                now = formats.now()
            }
        }
    }
    return if (formats.live) copy(nowEpochMillis = now) else this
}
