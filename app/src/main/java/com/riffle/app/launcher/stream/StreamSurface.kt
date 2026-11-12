@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.StreamFocus
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamPageNavigation
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPagesPerView
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamSpine
import com.riffle.core.domain.launcher.stream.StreamStage
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.StreamWideView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal const val STREAM_SURFACE_TEST_TAG = "stream-surface"
internal const val STREAM_PAGER_TEST_TAG = "stream-pager"

private const val CLOCK_TICK_MILLIS = 30_000L

/** A request to bring a page to the front, which the pager or the Stage acts on once; [id] tells repeats apart. */
private class PageJump(val key: String, val id: Long)

/**
 * The Stream (ADR 0003, Views): the actions centre as a horizontal pager of pages, each page one vertically scrolling
 * stack or list of one section's content (see [StreamPagePlanner]), with the spine above it as the page indicator. On
 * an expanded posture ([wideLayout]) the layout's [StreamWideView] chooses between that pager (two pages side by side
 * when the width allows) and the Stage view, a rail of live stages beside the selected stage's stack.
 *
 * Stateless apart from which page is showing: layout edits and every user action go out through [callbacks], and
 * [contentPadding] keeps it clear of the system bars and the Dock under it. "Show as" redraws at once and offers Undo.
 * The current page is kept by key, so content arriving never jumps the pager; [initialPageKey] and [onPageChanged]
 * carry the last page across a close and reopen.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
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
    /** An expanded posture: the Stage view and two pages side by side are available. */
    wideLayout: Boolean = false,
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
    val onMenuAction: (StreamMenuAction) -> Unit = { action ->
        val before = latestLayout
        callbacks.onLayoutAction(action.toLayoutAction())
        if (action is StreamMenuAction.SetWideView && action.view != before.wideView) {
            offerUndo(
                context.getString(R.string.stream_shown_as_stream, context.getString(action.view.titleRes())),
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

    val pages = remember(layout, results) { StreamPagePlanner.plan(layout, results) }
    val state = rememberStreamPageState(pages, results, initialPageKey, focusRequest, onFocusHandled, env.reducedMotion)
    LaunchedEffect(state.settledKey, state.restoring) {
        val key = state.settledKey
        if (key != null && !state.restoring) onPageChanged(key)
    }
    val enabled = layout.enabledSections
    val actionsFor: (StreamPage) -> StreamPageActions = { page ->
        val index = enabled.indexOfFirst { it.section == page.section }
        StreamPageActions(index > 0, index in 0 until enabled.lastIndex) { view -> onShowAs(page.section, view) }
    }
    val menu =
        StreamMenuModel(
            wideView = layout.wideView.takeIf { wideLayout },
            hiddenSections = layout.sections.filter { !it.enabled }.map { it.section },
        )
    Box(modifier = modifier.fillMaxSize().testTag(STREAM_SURFACE_TEST_TAG)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            val widthDp = maxWidth.value.toInt()
            when {
                pages.isEmpty() ->
                    StreamAllHidden(menu.hiddenSections) { section ->
                        surfaceCallbacks.onLayoutAction(StreamLayoutAction.SetEnabled(section, true))
                    }
                wideLayout && layout.wideView == StreamWideView.STAGE ->
                    StreamStageMode(
                        pages = pages,
                        state = state,
                        env = liveEnv,
                        callbacks = surfaceCallbacks,
                        actionsFor = actionsFor,
                        menu = menu,
                        onMenuAction = onMenuAction,
                    )
                else ->
                    StreamPagesMode(
                        pages = pages,
                        state = state,
                        env = liveEnv,
                        callbacks = surfaceCallbacks,
                        actionsFor = actionsFor,
                        menu = menu,
                        onMenuAction = onMenuAction,
                        perView = StreamPagesPerView.forWidth(widthDp, wideLayout),
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
    var recentKeys: List<String> by mutableStateOf(emptyList())
    private var lastJumpId = 0L

    val restoring: Boolean get() = restoreKey != null

    val animate: Boolean get() = !reducedMotion()

    /** The user (or a landing) brought [key] to the front. */
    fun select(
        key: String,
        jump: Boolean,
    ) {
        if (key == currentKey && !jump) return
        recentKeys = StreamStage.recentAfterSelecting(recentKeys, currentKey, key)
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
                val key = landing ?: initialPageKey?.takeIf { it in keys } ?: keys.firstOrNull()
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

/** A request can only be placed once the section it names has delivered: its page is a loading page until then. */
private fun StreamFocusRequest.isWaiting(pages: List<StreamPage>): Boolean {
    val key = StreamFocus.resolvePage(target, pages)
    val page = pages.firstOrNull { it.key == key }
    return (page?.body as? StreamPageBody.State)?.result == StreamSectionResult.Loading
}

/** The pager of pages with the spine above it. Two pages share the width when [perView] is 2. */
@Composable
@Suppress("LongParameterList", "LongMethod")
private fun StreamPagesMode(
    pages: List<StreamPage>,
    state: StreamPageState,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    actionsFor: (StreamPage) -> StreamPageActions,
    menu: StreamMenuModel,
    onMenuAction: (StreamMenuAction) -> Unit,
    perView: Int,
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
    val previousPage = stringResource(R.string.stream_previous_page)
    val nextPage = stringResource(R.string.stream_next_page)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        StreamSpineBar(
            pages = pages,
            currentIndex = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
            focusIndex =
                StreamSpine.focusIndex(
                    pagerState.currentPage,
                    pagerState.currentPageOffsetFraction,
                    pages.size,
                ),
            env = env,
            menu = menu,
            onSelect = goTo,
            onMenuAction = onMenuAction,
            modifier = Modifier.padding(horizontal = RiffleSpacing.l),
        )
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
            pageSize = if (perView > 1) splitPageSize(perView) else PageSize.Fill,
            pageSpacing = RiffleSpacing.s,
            beyondViewportPageCount = 0,
            key = { index -> latestPages.getOrNull(index)?.key ?: index },
        ) { index ->
            val page = latestPages.getOrNull(index)
            if (page != null) {
                val actions = actionsFor(page)
                StreamPageView(page, env, callbacks, actions.canMoveUp, actions.canMoveDown, actions.onShowAs)
            }
        }
    }
}

/** Each of [count] pages takes an equal share of the width. */
private fun splitPageSize(count: Int): PageSize =
    object : PageSize {
        override fun Density.calculateMainAxisPageSize(
            availableSpace: Int,
            pageSpacing: Int,
        ): Int = (availableSpace - pageSpacing * (count - 1)) / count
    }

/** The Stage view with its slim top bar (the title and the Stream's own menu). */
@Composable
private fun StreamStageMode(
    pages: List<StreamPage>,
    state: StreamPageState,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    actionsFor: (StreamPage) -> StreamPageActions,
    menu: StreamMenuModel,
    onMenuAction: (StreamMenuAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        StreamStageBar(menu, onMenuAction, Modifier.padding(horizontal = RiffleSpacing.l))
        StreamStageView(
            pages = pages,
            selectedKey = state.currentKey,
            recentKeys = state.recentKeys,
            previousKeys = state.previousKeys,
            env = env,
            callbacks = callbacks,
            actionsFor = actionsFor,
            onSelect = { key -> state.select(key, jump = false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreamStageBar(
    menu: StreamMenuModel,
    onMenuAction: (StreamMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = StreamMinTarget)
                        .combinedClickable(
                            onLongClickLabel = stringResource(R.string.stream_header_long_press_label),
                            onLongClick = { menuOpen = true },
                            onClick = {},
                        )
                        .padding(start = RiffleSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stream_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!menu.isEmpty) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(StreamMinTarget).testTag(STREAM_OPTIONS_TEST_TAG),
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription =
                                stringResource(
                                    R.string.stream_section_options,
                                    stringResource(R.string.stream_title),
                                ),
                        )
                    }
                }
            }
        }
        StreamMenu(menu, menuOpen && !menu.isEmpty, { menuOpen = false }, onMenuAction)
    }
}

/** Every section is hidden: say so, and offer each one back. */
@Composable
private fun StreamAllHidden(
    hidden: List<StreamSection>,
    onShow: (StreamSection) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = RiffleSpacing.l),
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
