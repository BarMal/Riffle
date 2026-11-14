@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.riffle.app.R
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dockFan
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.dock.DockPageOrder
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.stream.OpenCardEvent
import com.riffle.core.domain.launcher.stream.StreamFocus
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamPageNavigation
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.ref
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal const val STREAM_SURFACE_TEST_TAG = "stream-surface"
internal const val STREAM_PAGER_TEST_TAG = "stream-pager"

private const val CLOCK_TICK_MILLIS = 30_000L

/** A request to bring a page to the front, which the pager acts on once; [id] tells repeats apart. */
private class PageJump(val key: String, val id: Long)

/**
 * The Stream (ADR 0003, Views, Amendment 3): the actions centre as a horizontal pager of pages, each page one
 * vertically
 * scrolling stack or list of one section's content (see [StreamPagePlanner]), in the Dock's order ([DockPageOrder]).
 * There is no spine or page header: the Dock's contextual mode is the page indicator ([dockLink] tells it which page is
 * open and lists the pages), and a long-press of a tile there asks for the page options. Folded it fills the screen;
 * unfolded the same surface stands in a column attached to the Dock (the caller sizes it), so there is one Stream.
 *
 * Stateless apart from which page is showing: layout edits and every user action go out through [callbacks], and
 * [contentPadding] keeps it clear of the system bars and the Dock under it. "Show as" redraws at once and offers Undo.
 * The current page is kept by key, so content arriving never jumps the pager; [initialPageKey] and [onPageChanged]
 * carry the last page across a close and reopen.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
internal fun StreamSurface(
    layout: StreamLayout,
    results: Map<StreamSection, StreamSectionResult>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    failures: Flow<Unit> = emptyFlow(),
    /** Where to land, when the Dock opened the Stream on a live app or on now playing; handled once it has landed. */
    focusRequest: StreamFocusRequest? = null,
    onFocusHandled: (Long) -> Unit = {},
    /** The page the Stream was last left on (this session), or null. */
    initialPageKey: String? = null,
    onPageChanged: (String) -> Unit = {},
    /** How many live apps the Dock shows, which the order of the pages follows. */
    liveAppSlots: Int = DEFAULT_DOCK_LIVE_APP_SLOTS,
    /**
     * Where the Dock's contextual mode learns the pages and the open one, and asks for a page option; null in tests.
     */
    dockLink: StreamDockLink? = null,
    /** The shortcuts pages of the Dock's pinned apps (see [StreamShortcutPages]), which are no part of the layout. */
    shortcutPages: List<StreamPage> = emptyList(),
    /** The pinned apps' keys in the Dock's order, which the order of their pages follows. */
    pinnedGroupKeys: List<String> = emptyList(),
    /**
     * Whether an open card's actions sit in the Dock's row (the setting, on a Dock that can hold one) rather than on
     * the card. False wherever there is no Dock to hold them.
     */
    actionsInDock: Boolean = false,
) {
    val openCards = dockLink?.openCards ?: remember { OpenCardHolder() }
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
    val offerUndo: (String, StreamLayout) -> Unit = { message, before ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val outcome = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
            if (outcome == SnackbarResult.ActionPerformed) callbacks.onLayoutAction(StreamLayoutAction.Restore(before))
        }
    }
    val onShowAs: (StreamSection, StreamView) -> Unit = { section, view ->
        val before = latestLayout
        if (before.viewOf(section) != view) {
            callbacks.onLayoutAction(StreamLayoutAction.SetView(section, view))
            offerUndo(
                context.getString(
                    R.string.stream_shown_as,
                    context.getString(section.titleRes()),
                    context.getString(view.titleRes()),
                ),
                before,
            )
        }
    }
    // Every callback reaches every page: only the layout edit is wrapped (for its Undo), the rest are forwarded whole.
    val surfaceCallbacks =
        callbacks.copy(
            onLayoutAction = { action ->
                val before = latestLayout
                callbacks.onLayoutAction(action)
                if (action is StreamLayoutAction.SetEnabled && !action.enabled) {
                    offerUndo(
                        context.getString(R.string.stream_section_hidden, context.getString(action.section.titleRes())),
                        before,
                    )
                }
            },
        )

    val dockEntries =
        remember(results, liveAppSlots, pinnedGroupKeys) {
            results.dockLiveEntries(liveAppSlots, pinnedGroupKeys.toSet())
        }
    val pages =
        remember(layout, results, dockEntries, shortcutPages, pinnedGroupKeys) {
            DockPageOrder.order(
                pages = StreamShortcutPages.merge(StreamPagePlanner.plan(layout, results), shortcutPages),
                liveGroupKeys = DockPageOrder.liveGroupKeys(dockEntries),
                nowPlaying = dockEntries.any { entry -> entry is DockDynamicSideEntry.NowPlaying },
                pinnedGroupKeys = pinnedGroupKeys,
            )
        }
    val state = rememberStreamPageState(pages, results, initialPageKey, focusRequest, onFocusHandled, env.reducedMotion)
    // A card open on a page closes when the Stream comes to another one (a tap on a tile, the pager's own actions).
    LaunchedEffect(state.currentKey) { openCards.dispatch(OpenCardEvent.PageChanged(state.currentKey)) }
    LaunchedEffect(state.settledKey, state.restoring) {
        val key = state.settledKey
        if (key != null && !state.restoring) onPageChanged(key)
    }
    if (dockLink != null) {
        DockLinkEffects(dockLink, pages, surfaceCallbacks, onShowAs)
    }
    val hiddenSections = layout.sections.filter { !it.enabled }.map { it.section }
    CompositionLocalProvider(LocalOpenCards provides openCards, LocalActionsInDock provides actionsInDock) {
        Box(modifier = modifier.fillMaxSize().testTag(STREAM_SURFACE_TEST_TAG)) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (pages.isEmpty()) {
                    StreamAllHidden(hiddenSections) { section ->
                        surfaceCallbacks.onLayoutAction(StreamLayoutAction.SetEnabled(section, true))
                    }
                } else {
                    StreamPagesMode(
                        pages = pages,
                        state = state,
                        env = liveEnv,
                        callbacks = surfaceCallbacks,
                        dockLink = dockLink,
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
}

/**
 * Which page is current, by key, across everything that changes under it (ADR 0003, Views): swipes and taps, pages
 * arriving or going, a landing the Dock asked for, and the page remembered from last time (kept until the sections it
 * lives in have delivered, so an app's page is found once its notifications arrive).
 */
@Stable
private class StreamPageState(
    private val reducedMotion: () -> Boolean,
) {
    var currentKey: String? by mutableStateOf(null)
    var previousKeys: List<String> by mutableStateOf(emptyList())
    var settledKey: String? by mutableStateOf(null)
    var restoreKey: String? by mutableStateOf(null)
    var jump: PageJump? by mutableStateOf(null)
    private var lastJumpId = 0L

    val restoring: Boolean get() = restoreKey != null

    val animate: Boolean get() = !reducedMotion()

    /** The user (or a landing) brought [key] to the front. */
    fun select(
        key: String,
        jump: Boolean,
    ) {
        if (key == currentKey && !jump) return
        currentKey = key
        settledKey = key
        restoreKey = null
        if (jump) this.jump = PageJump(key, ++lastJumpId)
    }
}

@Composable
@Suppress("LongParameterList")
private fun rememberStreamPageState(
    pages: List<StreamPage>,
    results: Map<StreamSection, StreamSectionResult>,
    initialPageKey: String?,
    request: StreamFocusRequest?,
    onHandled: (Long) -> Unit,
    reducedMotion: Boolean,
): StreamPageState {
    val keys = pages.map { it.key }
    val latestReduced by rememberUpdatedState(reducedMotion)
    val state =
        remember {
            StreamPageState { latestReduced }.also { initial ->
                // Land straight where a request or the last visit says, so the first frame is already there.
                val landing =
                    request?.takeUnless { it.isWaiting(pages) }?.let { StreamFocus.resolvePage(it.target, pages) }
                val key = landing ?: initialPageKey?.takeIf { it in keys } ?: defaultPageKey(pages)
                initial.currentKey = key
                initial.settledKey = key
                initial.previousKeys = keys
                initial.restoreKey = initialPageKey?.takeIf { landing == null && it !in keys }
            }
        }
    // The current page stays current by key; one that has gone hands over to its nearest neighbour.
    val current = StreamPageNavigation.currentAfter(state.previousKeys, state.currentKey, keys)
    SideEffect {
        if (state.currentKey != current) state.currentKey = current
        if (state.previousKeys != keys) state.previousKeys = keys
    }
    // The remembered page of last time turns up once its section has delivered.
    LaunchedEffect(keys, results) {
        val restore = state.restoreKey ?: return@LaunchedEffect
        if (restore in keys) {
            state.select(restore, jump = true)
        } else if (pages.none { (it.body as? StreamPageBody.State)?.result == StreamSectionResult.Loading }) {
            state.restoreKey = null
        }
    }
    LaunchedEffect(request, pages) {
        if (request == null || request.isWaiting(pages)) return@LaunchedEffect
        StreamFocus.resolvePage(request.target, pages)?.let { state.select(it, jump = true) }
        onHandled(request.id)
    }
    return state
}

/**
 * The page the Stream opens on when it has no landing and no page to return to: the first page that is not a pinned
 * app's shortcuts (those come first, as the pinned icons do in the Dock, but the Stream is not an app launcher), else
 * the first.
 */
private fun defaultPageKey(pages: List<StreamPage>): String? =
    (pages.firstOrNull { it.section != StreamSection.SHORTCUTS } ?: pages.firstOrNull())?.key

/** A request can only be placed once the section it names has delivered: its page is a loading page until then. */
private fun StreamFocusRequest.isWaiting(pages: List<StreamPage>): Boolean {
    val key = StreamFocus.resolvePage(target, pages)
    val page = pages.firstOrNull { it.key == key }

    fun StreamPage.isLoading() = (body as? StreamPageBody.State)?.result == StreamSectionResult.Loading
    // A pinned app's page is its live page once the notifications have arrived: until then its shortcuts page is only
    // a placeholder that the live page replaces, so the request waits rather than land on it.
    val liveStillLoading =
        target is StreamFocusTarget.AppShortcuts && page?.section == StreamSection.SHORTCUTS &&
            pages.any { it.section == StreamSection.NOTIFICATIONS && it.groupKey == null && it.isLoading() }
    return page?.isLoading() == true || liveStillLoading
}

/** The pager of pages: one at a time, swiped sideways. */
@Composable
@Suppress("LongParameterList", "LongMethod")
private fun StreamPagesMode(
    pages: List<StreamPage>,
    state: StreamPageState,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    dockLink: StreamDockLink?,
) {
    val latestPages by rememberUpdatedState(pages)
    val keys = pages.map { it.key }
    val pagerState =
        rememberPagerState(initialPage = StreamPageNavigation.indexOf(keys, state.currentKey)) { latestPages.size }
    val scope = rememberCoroutineScope()
    val goTo: (Int) -> Unit = { index ->
        scope.launch {
            val target = index.coerceIn(0, (latestPages.size - 1).coerceAtLeast(0))
            if (state.animate) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    // A landing, the remembered page or a structural change: the pager follows the current key.
    LaunchedEffect(state.jump) {
        val jump = state.jump ?: return@LaunchedEffect
        val index = latestPages.indexOfFirst { it.key == jump.key }
        if (index >= 0 && index != pagerState.currentPage) {
            if (state.animate) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index)
        }
    }
    LaunchedEffect(state.currentKey, keys) {
        val index = keys.indexOf(state.currentKey)
        if (index >= 0 && index != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(index)
        }
    }
    // The page the pager came to rest on is the current one (swipes, spine taps and the actions below all end here).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.filter { !it }.collect {
            latestPages.getOrNull(pagerState.currentPage)?.key?.let { key -> state.select(key, jump = false) }
        }
    }
    // The page in front is the Dock's open icon: it follows the pager, so a swipe moves the ring when it passes
    // halfway.
    LaunchedEffect(pagerState, dockLink) {
        snapshotFlow { latestPages.getOrNull(pagerState.currentPage)?.key }
            .collect { key -> dockLink?.publishCurrent(key) }
    }
    val previousPage = stringResource(R.string.stream_previous_page)
    val nextPage = stringResource(R.string.stream_next_page)
    HorizontalPager(
        state = pagerState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = RiffleSpacing.s)
                .testTag(STREAM_PAGER_TEST_TAG)
                .semantics {
                    // The one custom-actions list of the pager: every swipe has an action.
                    customActions =
                        listOf(
                            CustomAccessibilityAction(previousPage) {
                                goTo(pagerState.currentPage - 1)
                                pagerState.currentPage > 0
                            },
                            CustomAccessibilityAction(nextPage) {
                                goTo(pagerState.currentPage + 1)
                                pagerState.currentPage < latestPages.lastIndex
                            },
                        )
                },
        // A swipe is not for a page while a card is open (the open card is its own scene): a tile or Back leaves it.
        userScrollEnabled = !LocalOpenCards.current.state.isOpen,
        pageSpacing = RiffleSpacing.s,
        beyondViewportPageCount = 0,
        key = { index -> latestPages.getOrNull(index)?.key ?: index },
    ) { index ->
        val page = latestPages.getOrNull(index)
        if (page != null) StreamPageView(page, env, callbacks)
    }
}

/** Every section is hidden: say so, and offer each one back. */
@Composable
private fun StreamAllHidden(
    hidden: List<StreamSection>,
    onShow: (StreamSection) -> Unit,
) {
    Column(
        modifier = Modifier.dockFan(LocalDockFan.current).fillMaxSize().padding(horizontal = RiffleSpacing.l),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        StreamStateMessage(stringResource(R.string.stream_all_hidden))
        hidden.forEach { section ->
            StreamTextButton(
                text = stringResource(R.string.stream_show_hidden_section, stringResource(section.titleRes())),
                onClick = { onShow(section) },
            )
        }
    }
}

/** The environment with its clock refreshed every half minute (countdowns), unless the formats are fixed. */
@Composable
internal fun StreamEnvironment.withLiveClock(): StreamEnvironment {
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

/**
 * Keeps the Dock's contextual mode in step with the Stream: lists the pages in front of it (cleared when the Stream
 * leaves) and applies the page options its tiles ask for through the Stream's own handlers, so each keeps its Undo.
 */
@Composable
private fun DockLinkEffects(
    dockLink: StreamDockLink,
    pages: List<StreamPage>,
    callbacks: StreamCallbacks,
    onShowAs: (StreamSection, StreamView) -> Unit,
) {
    val refs = pages.map(StreamPage::ref)
    SideEffect { dockLink.publishPages(refs) }
    DisposableEffect(dockLink) { onDispose { dockLink.clear() } }
    val latestCallbacks by rememberUpdatedState(callbacks)
    val latestShowAs by rememberUpdatedState(onShowAs)
    val request = dockLink.pendingOption
    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        when (val option = pending.option) {
            is StreamPageOption.ShowAs -> latestShowAs(option.section, option.view)
            is StreamPageOption.Move ->
                latestCallbacks.onLayoutAction(StreamLayoutAction.Move(option.section, option.delta))
            is StreamPageOption.Hide ->
                latestCallbacks.onLayoutAction(StreamLayoutAction.SetEnabled(option.section, false))
            is StreamPageOption.Show ->
                latestCallbacks.onLayoutAction(StreamLayoutAction.SetEnabled(option.section, true))
        }
        dockLink.optionHandled(pending.id)
    }
}
