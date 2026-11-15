package com.riffle.app.launcher.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.library.LibraryState
import com.riffle.app.launcher.library.toGridResult
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamStackState
import com.riffle.app.launcher.stream.SystemStreamFormats
import com.riffle.app.launcher.stream.rememberStreamSectionResults
import com.riffle.core.domain.launcher.search.SearchStack
import com.riffle.core.domain.launcher.search.SearchStackCard
import com.riffle.core.domain.launcher.search.SearchStackNotification
import com.riffle.core.domain.launcher.search.SearchStackNotifications
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What the Dock's search draws and does for the card stack: its cards, state, environment and actions. */
internal class SearchStackWiring(
    val cards: List<SearchStackCard>,
    val stackState: StreamStackState,
    val env: StreamEnvironment,
    val actions: SearchStackActions,
    /** Enter and the IME's search action: opens the front result (the stack's front card, else the top hit). */
    val openFront: () -> Unit,
)

/**
 * The stack's cards for the query (the best match first, "Search web" last), its position (a new query puts the best
 * match in front again), the environment the Stream's cards are drawn in, and what a tap does: a result opens through
 * the shell, an app's quick action through the Stream's one action gateway (the shell's own shortcut launch when the
 * Stream is off) and then search closes ([motion]). Nothing here is read unless [asStack].
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun rememberSearchStackWiring(
    state: LibraryState,
    context: AppListContext,
    reducedMotion: Boolean,
    stream: StreamHost?,
    motion: DockSearchMotionState,
    asStack: Boolean,
): SearchStackWiring {
    val stackState = remember(state.query) { StreamStackState() }
    val notifications = rememberSearchStackNotifications(stream, context, enabled = asStack && state.isSearching)
    val cards =
        remember(asStack, state.query, state.hits, context.appShortcutsByApp, notifications) {
            if (asStack) {
                SearchStack.plan(state.query, state.hits, context.appShortcutsByApp, notifications)
            } else {
                emptyList()
            }
        }
    val env =
        remember(stream, state.installedApps, context.appIconLoader, reducedMotion, state.cardSettings) {
            val formats = stream?.formats ?: SystemStreamFormats()
            StreamEnvironment(
                imageLoader = stream?.imageLoader ?: NoExpressionImageLoader,
                appIconLoader = context.appIconLoader,
                installedApps = state.installedApps,
                reducedMotion = reducedMotion,
                formats = formats,
                nowEpochMillis = formats.now(),
                cardSettings = state.cardSettings,
            )
        }
    val scope = rememberCoroutineScope()
    val latestContext by rememberUpdatedState(context)
    val actions =
        remember(stream, scope, motion) {
            SearchStackActions(
                open = { card -> latestContext.onAction(card.openAction()) },
                launchQuickAction = { card, action ->
                    if (stream != null) {
                        // The one action gateway, as the Stream's own chips: the shortcut's content, its profile too.
                        scope.launch(Dispatchers.Default) {
                            runCatching { stream.actions.perform(action.shortcut, StreamAction.Open) }
                        }
                        motion.close()
                    } else {
                        latestContext.appShortcutsByApp[card.app.identity].orEmpty()
                            .firstOrNull { it.id.value == action.id }
                            ?.let {
                                    shortcut ->
                                latestContext.onAction(LauncherShellAction.LaunchAppShortcut(shortcut))
                            }
                    }
                },
            )
        }
    val latestCards by rememberUpdatedState(cards)
    val latestStack by rememberUpdatedState(stackState)
    val latestHits by rememberUpdatedState(state.hits)
    val openFront: () -> Unit = {
        if (asStack) {
            val front = latestStack.frontIndex(latestCards.size)?.let { latestCards[it] }
            if (front != null) actions.open(front)
        } else {
            latestHits.firstOrNull()?.toGridResult()?.action?.let(latestContext.onAction)
        }
    }
    return remember(cards, stackState, env, actions) { SearchStackWiring(cards, stackState, env, actions, openFront) }
}

/**
 * The newest notification of each app for the search stack. The count is the launcher's; the line comes from the
 * Stream's notifications section when the Stream is on (it is read only while the stack is shown, and is already
 * redacted by the Privacy content level, the hide rules and the quiet profiles).
 */
@Composable
private fun rememberSearchStackNotifications(
    stream: StreamHost?,
    context: AppListContext,
    enabled: Boolean,
): Map<String, SearchStackNotification> {
    val results =
        if (stream != null) {
            rememberStreamSectionResults(
                stream.provider,
                if (enabled) listOf(StreamSection.NOTIFICATIONS) else emptyList(),
            )
        } else {
            emptyMap()
        }
    val ready = results[StreamSection.NOTIFICATIONS] as? StreamSectionResult.Ready
    return remember(context.notificationGroupsByApp, ready) {
        SearchStackNotifications.from(context.notificationGroupsByApp, ready?.groups.orEmpty())
    }
}
