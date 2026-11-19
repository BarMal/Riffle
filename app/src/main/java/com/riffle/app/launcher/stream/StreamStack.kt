@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.riffle.app.R
import com.riffle.app.launcher.CountRecomposition
import com.riffle.app.launcher.DockFanSource
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dockFan
import com.riffle.app.launcher.reportsCardRect
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.OpenCardBlend
import com.riffle.core.domain.launcher.stream.OpenCardEvent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.SlimTabs
import com.riffle.core.domain.launcher.stream.StackCardRef
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackFollow
import com.riffle.core.domain.launcher.stream.StackGeometry
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.SwipeDismiss
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val StackMaxCardWidth = StreamMaxReadableWidth

internal fun StreamContent.iconHandle(): ItemImageHandle? =
    when (this) {
        is NotificationContent -> icon
        is MediaContent -> icon
        is RecentAppContent -> icon
        is AppShortcutContent -> icon
        is AppPairContent -> first?.icon
        else -> null
    }

/**
 * The collapsed height each card type is drawn at in a stack, at most. Calm's card is 168 dp tall
 * ([StreamCardStackSpec]); an event and an article without a lead image take that, and the ones that carry more
 * (transport and the scrubber, a lead image) are taller. A notification is as tall as what it shows, up to that
 * ([rememberBrowseHeights]); each card of a stack is drawn at its own height.
 */
internal fun StreamContent.cardHeight(): Dp =
    when (this) {
        is NotificationContent -> StreamCardHeights.notification
        is MediaContent ->
            when {
                source -> StreamCardHeights.mediaSource
                queueItem != null -> StreamCardHeights.mediaQueue
                else -> StreamCardHeights.media
            }
        is EventContent -> StreamCardHeights.event
        is ArticleContent -> if (image != null) StreamCardHeights.article else StreamCardHeights.articleWithoutImage
        is TaskContent -> StreamCardHeights.task
        is RecentAppContent -> StreamCardHeights.app
        is AppShortcutContent -> StreamCardHeights.app
        is AppPairContent -> StreamCardHeights.pair
    }

/** One line saying what a card is about, for the strip of it that peeks out below the card in front. */
@Composable
internal fun StreamContent.peekText(): String =
    when (this) {
        is NotificationContent -> headline ?: hiddenText()
        is MediaContent -> mediaPeekText()
        is EventContent -> eventTitle(this)
        is ArticleContent -> title ?: hiddenText()
        is TaskContent -> title ?: hiddenText()
        is RecentAppContent -> label ?: hiddenText()
        is AppShortcutContent -> shortcutTitle(this)
        is AppPairContent -> pairPeekText(this)
    }

@Composable
private fun MediaContent.mediaPeekText(): String =
    if (source) appLabel ?: hiddenText() else title ?: stringResource(R.string.stream_media_unknown_title)

/**
 * The card for any content, so a Stack draws without knowing what is in it. [onOpen] opens it in place when a tap on it
 * should (a conversation, the player, an event); null leaves the tap to act on it, as in a widget.
 */
@Composable
@Suppress("LongParameterList")
internal fun StreamCard(
    content: StreamContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    /** Said before an event's time: the day, in a pile that spans days. */
    caption: String? = null,
    onOpen: (() -> Unit)? = null,
) {
    when (content) {
        is NotificationContent -> NotificationCard(content, env, callbacks, groupKey, modifier, onOpen)
        is MediaContent -> MediaCard(content, env, callbacks, groupKey, modifier, onOpen)
        is EventContent -> EventCard(content, env, callbacks, groupKey, modifier, caption, onOpen)
        is ArticleContent -> ArticleCard(content, env, callbacks, groupKey, modifier)
        is TaskContent -> TasksCard(listOf(content), env, callbacks, modifier)
        is RecentAppContent -> RecentAppCard(content, env, callbacks, groupKey, modifier, caption)
        is AppShortcutContent -> AppShortcutCard(content, env, callbacks, modifier)
        is AppPairContent -> PairCard(content, env, callbacks, modifier)
    }
}

/**
 * One page's stack of cards (ADR 0003, Amendment 1, "Browse, then open"; Amendment 3, "Motion"): anchored to the Dock.
 * The front card sits just above the Dock, nearest the thumb, and older cards peek above it, each one the real card
 * clipped to its top strip ([FocusedStack]). Tapping the front card of a kind that opens in place (a conversation, the
 * player, an event) opens it: it grows in place from its browse size into its full content ([OpenCardFrame]) and the
 * cards behind stay as peeking strips above it. A tap on it again, a tap beside it, a press of the Dock and Back close
 * it ([OpenCardHolder], which the Dock reads to draw the card's actions). A lone card is a stack of one, drawn at the
 * same place. Not gesture-only: Next and Previous card as accessibility actions and as buttons, a tap on a peeking
 * card, and the keyboard.
 */
@Composable
@Suppress("LongParameterList")
internal fun StreamStack(
    entries: List<StackEntry>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    stackKey: String,
    modifier: Modifier = Modifier,
) {
    // A card the owner dismissed is out of the stack at once, so the next card comes forward and no hole is left while
    // the app takes the notification away. If the app ignores the cancel (a messaging app re-posts it) the card comes
    // back, once, after SwipeDismiss.RETURN_AFTER_MILLIS: it does not hide, return and hide again.
    val dismissed = remember(stackKey) { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    // Only a swipe takes the card out at once (the Dismiss button and the accessibility action leave it to the app).
    val hook: (String) -> Unit =
        remember(stackKey) {
            { id ->
                if (id !in dismissed) {
                    dismissed += id
                    scope.launch {
                        delay(SwipeDismiss.RETURN_AFTER_MILLIS)
                        dismissed -= id
                    }
                }
            }
        }
    val visible = entries.filter { it.id !in dismissed }
    // The width a card is drawn at decides how tall its text is, and so how tall the card is: known before anything
    // of the stack is composed.
    if (visible.isNotEmpty()) {
        CompositionLocalProvider(LocalSwipeDismissed provides hook) {
            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val cardWidth = maxWidth.coerceAtMost(StackMaxCardWidth)
                val fitted = rememberFittedStack(visible, cardWidth, maxHeight, LocalStreamPageInsets.current)
                // The cards read the sizes with the line budget the stack needed: what they draw is what was measured.
                CompositionLocalProvider(LocalStreamCardSizes provides fitted.sizes) {
                    StreamStackContent(
                        entries = visible,
                        env = env,
                        callbacks = callbacks,
                        groupKey = groupKey,
                        stackKey = stackKey,
                        cardWidth = cardWidth,
                        areaHeight = maxHeight,
                        fitted = fitted,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
private fun StreamStackContent(
    entries: List<StackEntry>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    stackKey: String,
    cardWidth: Dp,
    areaHeight: Dp,
    fitted: FittedStack,
) {
    val cards = LocalOpenCards.current
    val pageInsets = LocalStreamPageInsets.current
    // Each card is as tall as what it shows (a notification's text, its buttons); the stack's geometry is built from
    // those heights, so the strips, the drag and the open card all work from the height the front card really has.
    val heights = fitted.heights
    // The sizes of the posture: the folded phone's three strips of 56 dp, or the unfolded column's up to six of 64 dp;
    // how many peek is what fits (StackFit: never above the column's top).
    val sizes = LocalStreamCardSizes.current
    val peeks = fitted.peeks
    val geometry =
        remember(heights, sizes, peeks) {
            StackGeometry(
                heightsDp = heights.map { it.value },
                pitchDp = sizes.pitchDp,
                visiblePeeks = peeks,
                scaleStep = sizes.scaleStep,
            )
        }
    val openState = cards.state
    val presences = remember(stackKey) { mutableMapOf<String, OpenCardPresence>() }
    val stackState = remember(stackKey) { StreamStackState() }
    // Whether the stack, with its strips, stops short of the Stream's top-end corner, where the view button stands.
    val buttonRoom = LocalStreamViewButtonRoom.current
    val strips = minOf(peeks, entries.size - 1).coerceAtLeast(0)
    val reachDp = geometry.cardHeightDp + strips * sizes.pitchDp + pageInsets.stackBottom.value
    val cornerClear = reachDp + StreamMinTarget.value <= areaHeight.value
    if (buttonRoom != null) {
        SideEffect { buttonRoom.report(stackKey, cornerClear) }
        DisposableEffect(buttonRoom, stackKey) { onDispose { buttonRoom.report(stackKey, clear = true) } }
    }
    val ids = remember(entries) { entries.map { it.id }.toSet() }
    // A card that goes away closes; a card open on another page is that page's.
    LaunchedEffect(stackKey, ids) { cards.dispatch(OpenCardEvent.CardsChanged(stackKey, ids)) }
    val openedHere = openState.isOpen && openState.pageKey == stackKey
    val openEntry = entries.firstOrNull { it.id == openState.cardId && openedHere && it.opensInPlace }
    // One motion for the Stream's open card (the holder's): the stack reads it only where it draws.
    val motion = cards.motion
    val frameGeometry = remember(stackKey) { OpenCardFrameGeometry() }
    val mover = rememberStackMover(stackState, entries.size, env.reducedMotion)
    val latestEntries by rememberUpdatedState(entries)
    val latestOpenedHere by rememberUpdatedState(openedHere)
    // The layer is drawn while a card is open here and while it closes. Whether it is still there is read through a
    // derived value, so the stack recomposes when the card appears and when it is gone, never once per frame between.
    // Only the stack the card is open in (or was, while it closes) has a layer: the motion is the Stream's, so another
    // page's stack, composed beside this one while pages are swiped, must not take part in it (it published an empty
    // layer that replaced the open card's, leaving tabs and no card).
    var closingHere by remember(stackKey) { mutableStateOf(false) }
    SideEffect { if (openedHere && !closingHere) closingHere = true }
    LaunchedEffect(stackKey) {
        snapshotFlow { !latestOpenedHere && motion.progress <= 0f }.collect { if (it) closingHere = false }
    }
    val layerActive =
        remember(stackKey) { derivedStateOf { latestOpenedHere || (closingHere && motion.progress > 0f) } }
    // The cards the layer draws: those of the open card's kind at the two positions the stack lies between. The set
    // changes only when the stack crosses a card, so a drag recomposes nothing between.
    val latestReduced by rememberUpdatedState(env.reducedMotion)
    val overlayIndices =
        remember(stackKey) {
            derivedStateOf { openableIndicesAround(stackState.position, latestEntries, latestReduced) }
        }
    BackHandler(enabled = openEntry != null) { cards.dispatch(OpenCardEvent.CloseByBack) }
    val actionsOnCard = !LocalActionsInDock.current
    // The Dock's row: this card's content, its controls and how to act on them. Published while the card is open.
    val openContent = (openEntry as? ContentEntry)?.content
    val dockActions =
        remember(openContent, callbacks) {
            openContent?.takeIf { !actionsOnCard }?.let {
                OpenCardActions(
                    content = it,
                    actions = StreamCardActions.forContent(it),
                    callbacks = callbacks,
                )
            }
        }
    SideEffect { cards.publish(stackKey, dockActions) }
    DisposableEffect(cards, stackKey) { onDispose { cards.publish(stackKey, null) } }
    // A stack that comes to rest on another card with a card open (a drag, Next or Previous card, a tap on a strip):
    // that card is open in place, or, with nothing to open, the stack is at rest.
    LaunchedEffect(stackKey) {
        snapshotFlow { stackState.settled }.drop(1).collect { index ->
            val entry = latestEntries.getOrNull(index)
            if (entry != null && latestOpenedHere) {
                cards.dispatch(OpenCardEvent.Settled(stackKey, entry.id, entry.opensInPlace))
            }
        }
    }

    val open: (StackEntry) -> Unit = { entry ->
        if (entry.opensInPlace) {
            cards.dispatch(OpenCardEvent.Open(stackKey, entry.id))
        } else {
            entry.openContent?.let { callbacks.onAction(it, StreamAction.Open) }
        }
    }
    val close = { cards.dispatch(OpenCardEvent.CloseByTap) }
    // The slots give way to the open card (hidden, the stack moved to its frame) only once the layer that draws it is
    // there: a frame with the slot hidden and no card drawn is a blink, and a stack moved to a frame that has not been
    // laid out is a stack at the top of the screen.
    val layerNow = cards.layer
    val layerIds = layerNow?.cards?.map { it.id }.orEmpty()
    val drawnByLayer =
        layerActive.value && OpenCardCover.drawsHere(layerNow != null && layerNow.stack === stackState, layerIds)
    val covers: (Int, Float) -> Boolean = { index, r ->
        layerActive.value && OpenCardBlend.openness(r) > 0f && index in overlayIndices.value
    }
    // While a card is open its neighbours are slim tabs: the strips' pitch eases from the stack's to the tabs' on the
    // open card's own progress, and the cards' faces swap for the tabs' on the same number. Both are read in layout
    // and draw.
    val stripPitchDp: () -> Float =
        remember(stackKey, sizes) {
            { if (layerActive.value) SlimTabs.pitchDp(sizes.pitchDp, motion.progress) else sizes.pitchDp }
        }
    val tabMorph: () -> Float =
        remember(stackKey) { { if (layerActive.value) motion.progress.coerceIn(0f, 1f) else 0f } }
    // A tap on a neighbour's tab (or its "open" action): that card opens in place; a card with nothing to open closes.
    val openNeighbour: (Int) -> Unit =
        remember(mover, cards) {
            { index ->
                if (latestEntries.getOrNull(index)?.opensInPlace == true) {
                    mover.goTo(index)
                } else {
                    cards.dispatch(OpenCardEvent.CloseByTap)
                }
            }
        }
    Box(modifier = Modifier.fillMaxSize()) {
        if (entries.size == 1) {
            val only = entries.single()
            // A lone card is its own front card: dismissible if it is a notification that can be, else it resists.
            val swipe =
                rememberSwipeDismissState(
                    only.swipeableContent(),
                    callbacks,
                    env.reducedMotion,
                    resists = true,
                )
            val dismissLabel = stringResource(R.string.stream_notification_dismiss)
            LoneCard(
                streamStackCardTag(only.id),
                resistLabel = only.resistLabel(isFront = true),
                hidden = drawnByLayer,
                swipe = swipe,
                canDismiss = only.swipeableContent() != null,
                dismissLabel = dismissLabel,
                onDismiss = { only.swipeableContent()?.let { callbacks.onAction(it, StreamAction.Dismiss) } },
            ) {
                StackEntryCard(
                    entry = only,
                    env = env,
                    callbacks = callbacks,
                    groupKey = groupKey,
                    onOpen = { open(only) },
                    modifier = Modifier.fillMaxWidth().widthIn(max = StackMaxCardWidth).height(heights.first()),
                )
            }
        } else {
            FocusedStack(
                items = entries,
                env = env,
                callbacks = callbacks,
                groupKey = groupKey,
                state = stackState,
                heights = heights,
                geometry = geometry,
                cardWidth = cardWidth,
                areaHeight = areaHeight,
                openActive = drawnByLayer,
                layerCardIds = layerIds,
                layerAlpha = { id -> presences[id]?.alpha ?: 0f },
                mover = mover,
                covers = covers,
                frameGeometry = frameGeometry,
                onOpen = open,
                stripPitchDp = stripPitchDp,
                tabMorph = tabMorph,
                onTab = openNeighbour,
            )
        }
    }
    val placement = env.cardSettings.openCardPlacement
    val density = LocalDensity.current.density
    val bodies = remember(stackKey) { OpenCardBodies() }
    val cue = remember(stackKey) { HandoffCueState() }
    val footer = LocalStackFooter.current
    val handoff =
        remember(mover, density, cue) {
            OpenCardHandoffConnection(
                mover,
                density,
                bodyScroll = {
                    latestEntries.getOrNull(StackFling.nearest(stackState.position, latestEntries.size))
                        ?.let { entry -> bodies.scrollFor(entry.id) }
                },
                cue = cue,
                hasNeighbour = { older ->
                    val front = StackFling.nearest(stackState.position, latestEntries.size)
                    if (older) front + 1 < latestEntries.size else front > 0
                },
            )
        }
    // The cards in front of the open one show as strips under it: the nearest few below the position.
    // The strips' number is the fit's, which changes with the room: read through the latest value, not the first.
    val latestPeeks by rememberUpdatedState(peeks)
    val belowIndices =
        remember(stackKey) {
            derivedStateOf {
                if (latestReduced) {
                    emptyList()
                } else {
                    val low = floor(stackState.position).toInt().coerceAtMost(latestEntries.size - 1)
                    ((low - latestPeeks).coerceAtLeast(0) until low.coerceAtLeast(0)).toList()
                }
            }
        }
    val dragsStack = entries.size > 1
    val dragModifier =
        remember(mover, handoff, dragsStack) {
            if (dragsStack) {
                // A vertical drag of the layer outside the card's scrolling body moves the stack; one that starts in
                // the body scrolls it, and only what it cannot take, past a threshold, is handed on (the nested
                // connection, inside the scrollable so that it sees what the body leaves first).
                Modifier
                    .scrollable(
                        state = mover.scrollable,
                        orientation = Orientation.Vertical,
                        flingBehavior = mover.flingBehavior,
                    )
                    .nestedScroll(handoff)
            } else {
                Modifier
            }
        }
    val previous = stringResource(R.string.stream_previous_card)
    val next = stringResource(R.string.stream_next_card)
    // The open card is not drawn here: it is handed to the surface, which draws it over the whole of its area, outside
    // the pager and the column, so nothing around it can clip it (see OpenCardLayerHost). While it closes the cards
    // last drawn stay (the content may be gone by then).
    val retained = remember(stackKey) { RetainedLayerCards() }
    val layerCards =
        overlayIndices.value.mapNotNull { index ->
            val entry = entries.getOrNull(index) as? ContentEntry
            entry?.let { index to it.content }
        }
    val drawnCards = if (openedHere) layerCards else retained.cards
    SideEffect { if (openedHere) retained.cards = layerCards }
    val layer =
        remember(
            drawnCards, layerActive.value, env, callbacks, groupKey, actionsOnCard, geometry,
            placement, dragModifier, entries.size, previous, next, sizes, footer, peeks, belowIndices.value, pageInsets,
        ) {
            if (!layerActive.value || drawnCards.isEmpty()) {
                null
            } else {
                OpenCardLayer(
                    geometry = frameGeometry,
                    placement = placement,
                    stackGeometry = geometry,
                    pitchDp = sizes.pitchDp,
                    bottomInset = pageInsets.stackBottom,
                    stack = stackState,
                    count = entries.size,
                    reducedMotion = env.reducedMotion,
                    dragModifier = dragModifier,
                    onClose = close,
                    onTabTap = openNeighbour,
                    restFooter = footer,
                    peeks = peeks,
                    cue = cue,
                    belowStrips =
                        belowIndices.value.mapNotNull { index ->
                            val entry = entries.getOrNull(index) ?: return@mapNotNull null
                            OpenLayerStrip(index, entry.id) { stripModifier ->
                                BelowStrip(
                                    entry = entry,
                                    env = env,
                                    onOpen = { mover.goTo(index) },
                                    modifier = stripModifier,
                                )
                            }
                        },
                    cards =
                        drawnCards.map { (index, content) ->
                            val presence = presences.getOrPut(content.id.value) { OpenCardPresence() }
                            OpenLayerCard(index, content.id.value, presence = presence, content = { cardModifier ->
                                val scroll = rememberScrollState()
                                DisposableEffect(content.id, scroll) {
                                    bodies.register(content.id.value, scroll)
                                    onDispose { bodies.unregister(content.id.value, scroll) }
                                }
                                CountRecomposition("open-card:${content.id.value}")
                                OpenStreamCard(
                                    content = content,
                                    env = env,
                                    callbacks = callbacks,
                                    groupKey = groupKey,
                                    actionsOnCard = actionsOnCard,
                                    onClose = close,
                                    bodyScroll = scroll,
                                    cardSteps =
                                        if (entries.size > 1) {
                                            CardSteps(previous, next) { older -> mover.step(older) }
                                        } else {
                                            null
                                        },
                                    presence = presence,
                                    cue = cue.takeIf { entries.size > 1 },
                                    olderTitle = entries.getOrNull(index + 1)?.peekText(env),
                                    newerTitle = entries.getOrNull(index - 1)?.peekText(env),
                                    modifier =
                                        cardModifier.fillMaxWidth().reportsCardRect()
                                            .openCardAlphaSemantics(presence).testTag(STREAM_OPEN_CARD_TEST_TAG),
                                )
                            })
                        },
                )
            }
        }
    SideEffect { cards.publishLayer(stackKey, layer) }
    DisposableEffect(cards, stackKey) { onDispose { cards.publishLayer(stackKey, null) } }
}

/**
 * A card in front of the open one, drawn as a slim tab under it ([SlimTab]). One node to assistive technology, a button
 * "<title>, open", that brings the card up when activated. A tap on it is found by the layer's scrim (the tab is
 * slimmer than a touch target, so its target is wider than it: [SlimTabs.hit]).
 */
@Composable
private fun BelowStrip(
    entry: StackEntry,
    env: StreamEnvironment,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    CountRecomposition("open-strip:${entry.id}")
    val label = stringResource(R.string.stream_open_tab, entry.peekText(env))
    SlimTab(
        entry = entry,
        env = env,
        modifier =
            modifier.clearAndSetSemantics {
                testTag = STREAM_OPEN_STRIP_TEST_TAG
                role = Role.Button
                contentDescription = label
                onClick(label = null) {
                    onOpen()
                    true
                }
            },
    )
}

/** The test tag of a strip of a card in front of the open one. */
internal const val STREAM_OPEN_STRIP_TEST_TAG = "stream-open-strip"

/** The indices of the cards around [position] (the two it lies between) that open in place, in order. */
private fun openableIndicesAround(
    position: Float,
    entries: List<StackEntry>,
    reducedMotion: Boolean,
): List<Int> {
    if (entries.isEmpty()) return emptyList()
    // Under reduced motion one card is drawn at a time: the nearest to the front, swapped as the stack crosses a card.
    val low = if (reducedMotion) StackFling.nearest(position, entries.size) else floor(position).toInt()
    val high = if (reducedMotion) low else ceil(position).toInt()
    return (low..high).filter { entries.getOrNull(it)?.opensInPlace == true && entries[it] is ContentEntry }
}

/** The cards the layer drew last while a card was open, so that it can draw them while the card closes. */
private class RetainedLayerCards {
    var cards: List<Pair<Int, StreamContent>> = emptyList()
}

/** The scroll states of the open cards' bodies, by card id: the hand-off asks whether the front one scrolls. */
internal class OpenCardBodies {
    private val states = mutableMapOf<String, ScrollState>()

    fun register(
        id: String,
        state: ScrollState,
    ) {
        states[id] = state
    }

    fun unregister(
        id: String,
        state: ScrollState,
    ) {
        if (states[id] === state) states.remove(id)
    }

    fun scrollFor(id: String): ScrollState? = states[id]
}

/** The tag of the open card (a test hook). */
internal const val STREAM_OPEN_CARD_TEST_TAG = "stream-open-card"

/**
 * A lone card, where the front card of a stack is: just above the Dock (it scrolls only if it is taller than the page).
 * [hidden] while it is open: the open card is drawn over its place.
 */
@Composable
@Suppress("LongParameterList")
private fun LoneCard(
    tag: String,
    /** The words the card says when it resists a sideways drag; null for a card that can be dismissed. */
    resistLabel: String?,
    hidden: Boolean,
    swipe: SwipeDismissState?,
    canDismiss: Boolean,
    dismissLabel: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val quickActions = LocalFrontCardQuickActions.current
    val stackBottom = LocalStreamPageInsets.current.stackBottom
    Box(
        modifier =
            Modifier.fillMaxSize().then(
                if (hidden) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {} else Modifier,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .reportsCardRect()
                    .dockFan(LocalDockFan.current)
                    .swipeCue(swipe, resistLabel)
                    .swipeLayer(swipe)
                    .swipeToDismiss(swipe)
                    .semantics {
                        testTag = tag
                        swipeSemantics(swipe, resistLabel)
                        val dismiss =
                            if (canDismiss) {
                                listOf(
                                    CustomAccessibilityAction(dismissLabel) {
                                        onDismiss()
                                        true
                                    },
                                )
                            } else {
                                emptyList()
                            }
                        if (dismiss.isNotEmpty() || quickActions.isNotEmpty()) customActions = dismiss + quickActions
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = stackBottom),
        ) { content() }
    }
}

/**
 * Everything a stack leaves under the front card, in the order of the slots: used for the content description of a
 * peeking card, so assistive technology reads what the strip hides (the body, not only the line that shows).
 */
@Composable
@Suppress("CyclomaticComplexMethod") // One line per kind of content.
internal fun StreamContent.spokenText(env: StreamEnvironment): String =
    when (this) {
        is NotificationContent ->
            listOfNotNull(
                secondaryLabel,
                headline ?: hiddenText(),
                text?.takeIf { it.isNotBlank() },
                env.formats.age(postedAtEpochMillis),
            )
        is MediaContent ->
            when {
                source -> mediaSourceSpoken(env)
                queueItem != null -> listOf(queueSpoken())
                else -> listOfNotNull(appLabel, peekText(), artist)
            }
        is EventContent ->
            listOfNotNull(
                eventSpanText(this, env),
                eventTitle(this),
                (location ?: calendarName)?.takeIf { it.isNotBlank() },
                eventCountdownText(this, env.nowEpochMillis),
            )
        is ArticleContent ->
            listOfNotNull(feedName, title ?: hiddenText(), summary?.takeIf { it.isNotBlank() }, author)
        is TaskContent -> listOfNotNull(title ?: hiddenText(), listName)
        is RecentAppContent -> listOfNotNull(label ?: hiddenText(), lastUsedEpochMillis?.let(env.formats::age))
        is AppShortcutContent -> listOfNotNull(shortcutTitle(this), longLabel, appLabel.takeIf { !opensApp })
        is AppPairContent -> listOf(pairSpokenText(this))
    }.joinToString(". ")

/**
 * The stack of two or more cards. [StackLayout] places each card from the stack's one continuous position; the
 * platform's `scrollable` (ADR 0002) turns the vertical drag into that position and hands the release to the stack's
 * own fling (`StreamStackState.release`: coast to the card the velocity would reach, settle on it with a magnetic
 * spring), so what the platform owns is drag detection, touch slop, velocity tracking and the choice of axis, and the
 * stack owns only what the position means.
 *
 * Direction is decided from the first movement by the platform's nested scrolling, as for the page's own horizontal
 * pager: the pager's `scrollable` is horizontal and this one is vertical, and each claims a drag only along its own
 * axis, so a swipe that leads sideways changes the page and one that leads up or down moves the stack. A finger that
 * lands on a running coast catches it (a `scrollable` whose scroll is in progress starts dragging at once and cancels
 * the coast). What needed custom handling: the position (a float, rubber-banded past both ends, which the platform's
 * scroll value has no notion of), the release (a spring with a magnet in place of the platform's decay) and the tap on
 * a peeking card, which brings it to the front.
 *
 * Dragging down brings older cards toward the front and the front card sinks toward the Dock, fading. The accessibility
 * actions "Next card" (older, above) and "Previous card" are on the stack itself, so nothing depends on the drag.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
private fun FocusedStack(
    items: List<StackEntry>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    state: StreamStackState,
    /** How tall each card is drawn, and the stack's layout built from those heights. */
    heights: List<Dp>,
    geometry: StackGeometry,
    cardWidth: Dp,
    areaHeight: Dp,
    /** A card is open over the front card: the stack makes room for it and takes no input. */
    openActive: Boolean,
    /** The ids of the cards the open card's layer draws for this stack, and how opaque it draws one (read in draw). */
    layerCardIds: List<String>,
    layerAlpha: (cardId: String) -> Float,
    mover: StackMover,
    /** Whether the open card's layer draws the card at [index] ([r] places from the front) in the slot's place. */
    covers: (index: Int, r: Float) -> Boolean,
    frameGeometry: OpenCardFrameGeometry,
    onOpen: (StackEntry) -> Unit,
    /** The distance between the strips now: the stack's pitch, or, with a card open, easing to the slim tabs' pitch. */
    stripPitchDp: () -> Float,
    /** How far the strips have become slim tabs, 0 to 1, read in draw. */
    tabMorph: () -> Float,
    /** The tab of the card at an index was activated (with a card open). */
    onTab: (Int) -> Unit,
) {
    val count = items.size
    val dockFan = LocalDockFan.current
    // A media stack's queue moves up as the track advances: the person stays on the same card (by its id), or on the
    // front when that card became the one playing. Before the clamp, in the frame the cards change.
    val followed = remember(state) { StackFollowMemory() }
    SideEffect { followed.follow(items, state) }
    LaunchedEffect(count) { state.clampTo(count) }
    // The stack is held to its cards in the very frame they change (a card dismissed, a conversation gone), not a frame
    // later, so its position is never past the last card.
    SideEffect { state.clampTo(count) }
    val goTo: (Int) -> Boolean = mover::goTo
    val step: (Boolean) -> Boolean = mover::step
    val latestOpenActive by rememberUpdatedState(openActive)

    val frontIndex by remember(count) { derivedStateOf { StackFling.nearest(state.position, count) } }
    val composed by remember(count, geometry) { derivedStateOf { geometry.composedRange(state.position, count) } }
    val previous = stringResource(R.string.stream_previous_card)
    val next = stringResource(R.string.stream_next_card)
    val open = stringResource(R.string.stream_open_card)
    val bringToFront = stringResource(R.string.stream_bring_card_to_front)
    val position = stringResource(R.string.stream_card_position, frontIndex + 1, count)
    val dismiss = stringResource(R.string.stream_notification_dismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val stackBottom = LocalStreamPageInsets.current.stackBottom
        val bottomInsetPx = stackBottom.value * density
        val areaHeightPx = areaHeight.value * density
        val sideBleed = remember(density) { SideBleedShape(StackSideBleed.value * density) }
        StackLayout(
            state = state,
            geometry = geometry,
            cardWidth = cardWidth,
            bottomInset = stackBottom,
            covers = covers,
            stripPitchDp = stripPitchDp,
            modifier =
                Modifier
                    .matchParentSize()
                    // While a card is open the stack moves with it: the front card's place is where the open card's
                    // top is, so the cards behind peek out just above it.
                    .graphicsLayer {
                        // Where the front card's top stands now: its own height above the bottom (read here, in
                        // the draw phase, so a moving stack recomposes nothing).
                        // In whole pixels, as the layout places the slots and the layer places the open card: the
                        // strip behind ends exactly on the open card's top, with no row between them.
                        val frontTop =
                            areaHeightPx.roundToInt() - bottomInsetPx.roundToInt() -
                                (geometry.frontHeightAt(state.position) * density).roundToInt()
                        translationY = if (openActive) (frameGeometry.topPx.roundToInt() - frontTop).toFloat() else 0f
                        // Too little room above the open card for the strips: they are not drawn, not overlapped.
                        alpha = if (openActive && !frameGeometry.stripsVisible) 0f else 1f
                        // Clipped to the stack's own rectangle at the top and the bottom (cards sink into the Dock),
                        // but not at the sides: a card that gives a few dp sideways, or is taken to be dismissed,
                        // is drawn into the page's margin and is cut only by the pager at the edge of the surface.
                        clip = true
                        shape = sideBleed
                    }
                    .scrollable(
                        state = mover.scrollable,
                        orientation = Orientation.Vertical,
                        enabled = !openActive,
                        flingBehavior = mover.flingBehavior,
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp, Key.PageUp -> !latestOpenActive && step(true)
                                Key.DirectionDown, Key.PageDown -> !latestOpenActive && step(false)
                                else -> false
                            }
                        }
                    }
                    .semantics {
                        isTraversalGroup = true
                        // Nothing depends on the drag: the stack itself steps through its cards.
                        customActions =
                            listOf(
                                CustomAccessibilityAction(previous) { step(false) },
                                CustomAccessibilityAction(next) { step(true) },
                            )
                    },
        ) {
            for (index in composed) {
                val item = items[index]
                key(item.id) {
                    val isFront = index == frontIndex
                    // Every card owns a horizontal drag that starts on it (SwipeDismiss.ownerOf): the front card of a
                    // dismissible notification dismisses, any other resists and settles back; none reach the pager.
                    val swipe =
                        rememberSwipeDismissState(
                            item.swipeableContent(),
                            callbacks,
                            env.reducedMotion,
                            resists = true,
                            isFront = isFront,
                        )
                    StackSlot(
                        index = index,
                        cut = Modifier.stripCut(index, state, geometry, cardWidth, stripPitchDp),
                        isFront = isFront,
                        covered = isFront && OpenCardCover.hidesFront(openActive, layerCardIds, item.id, 1f),
                        layerAlpha = { layerAlpha(item.id) },
                        rank = abs(index - frontIndex),
                        dockFan = dockFan,
                        tag = streamStackCardTag(item.id),
                        spoken = item.spokenText(env),
                        position = position,
                        open = open,
                        bringToFront = bringToFront,
                        onOpen = { onOpen(item) },
                        onBringToFront = { if (latestOpenActive) onTab(index) else goTo(index) },
                        tabMorph = tabMorph.takeIf { openActive && !isFront },
                        tab =
                            if (openActive && !isFront) {
                                { tabModifier -> SlimTab(item, env, tabModifier, tuck = SlimTabTuck) }
                            } else {
                                null
                            },
                        tabLabel =
                            if (openActive && !isFront) {
                                stringResource(R.string.stream_open_tab, item.peekText(env))
                            } else {
                                ""
                            },
                        swipe = swipe,
                        resistLabel = item.resistLabel(isFront),
                        canDismiss = item.swipeableContent() != null,
                        dismissLabel = dismiss,
                        onDismiss = {
                            item.swipeableContent()?.let {
                                callbacks.onAction(
                                    it,
                                    StreamAction.Dismiss,
                                )
                            }
                        },
                    ) {
                        // A card behind the front one shows its header only: whole lines, never a cut one.
                        val place = remember(index, state) { StripPlace(index) { state.position } }
                        CompositionLocalProvider(LocalStripPlace provides place) {
                            StackEntryCard(
                                entry = item,
                                env = env,
                                callbacks = callbacks,
                                groupKey = groupKey,
                                onOpen = { onOpen(item) },
                                modifier = Modifier.size(width = cardWidth, height = heights[index]),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One card's slot. The front card is the live card (its own actions, an "Open card" action and Enter or Space to open
 * it); a peeking card is one tap target that brings it to the front, which reads what its strip hides (the whole text
 * of the card), and whose own controls are out of reach until it is in front.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun StackSlot(
    index: Int,
    /** Draws the card less the card in front of it, so it tucks under that card ([stripCut]). */
    cut: Modifier,
    isFront: Boolean,
    /** The front card with a card open over it: the open card is drawn in its place, so this one is not. */
    covered: Boolean,
    /** How opaque the layer draws this card (read in draw): a covered card is hidden only while it is above 0. */
    layerAlpha: () -> Float,
    rank: Int,
    dockFan: DockFanSource?,
    tag: String,
    spoken: String,
    position: String,
    open: String,
    bringToFront: String,
    onOpen: () -> Unit,
    onBringToFront: () -> Unit,
    /** The card's sideways drag: it dismisses the front card of a dismissible notification, else it resists. */
    swipe: SwipeDismissState?,
    /** What the front card says when it resists ("Ongoing", "Can't dismiss"); null for a strip or a dismissible one. */
    resistLabel: String?,
    /** Whether the card is a notification that offers Dismiss (its custom action, and the cue). */
    canDismiss: Boolean,
    /** The custom action that dismisses it without a gesture (its label), and what it does. */
    dismissLabel: String,
    onDismiss: () -> Unit,
    /** While a card is open: how far this strip has become a slim tab, read in draw; null when it is a strip. */
    tabMorph: (() -> Float)?,
    /** The tab that takes this strip's place as the card opens (it fills the slot); null when it is a strip. */
    tab: (@Composable (Modifier) -> Unit)?,
    /** What the tab says to assistive technology: its title, open. */
    tabLabel: String,
    card: @Composable () -> Unit,
) {
    CountRecomposition("stack-slot:$index")
    val quickActions = LocalFrontCardQuickActions.current
    val semantics =
        if (isFront) {
            Modifier.semantics {
                testTag = tag
                traversalIndex = 0f
                stateDescription = position
                swipeSemantics(swipe, resistLabel)
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(open) {
                            onOpen()
                            true
                        },
                        CustomAccessibilityAction(dismissLabel) {
                            onDismiss()
                            true
                        }.takeIf { canDismiss },
                    ) + quickActions
            }
        } else if (tab != null) {
            // With a card open the strip is a slim tab: one button, "<title>, open", that opens the card.
            Modifier.clearAndSetSemantics {
                testTag = tag
                traversalIndex = rank.toFloat()
                role = Role.Button
                swipeSemantics(swipe)
                contentDescription = tabLabel
                onClick(label = null) {
                    onBringToFront()
                    true
                }
            }
        } else {
            // One node for the strip: the whole card's text, one action. The card's own controls are not reachable.
            Modifier.clearAndSetSemantics {
                testTag = tag
                traversalIndex = rank.toFloat()
                swipeSemantics(swipe)
                contentDescription = spoken
                onClick(label = bringToFront) {
                    onBringToFront()
                    true
                }
            }
        }
    Box(
        modifier =
            Modifier
                .stackSlot(index)
                .reportsCardRect()
                .dockFan(dockFan, rank)
                // The cue is drawn where the card was, so it stays while the card goes (before the swipe's layer).
                .swipeCue(swipe.takeIf { canDismiss || resistLabel != null }, resistLabel)
                // The swipe moves the whole slot, outside its cut, so the card is never cut off as it goes.
                .swipeLayer(swipe)
                // The slot is as tall as the part of the card that shows (its strip: what a tap and a screen reader
                // find); the card is drawn past it, down to and around the rounded top of the card in front.
                .then(cut)
                .then(
                    if (covered) {
                        Modifier.graphicsLayer { alpha = if (layerAlpha() > 0f) 0f else 1f }.clearAndSetSemantics {}
                    } else {
                        semantics
                    },
                )
                .then(if (isFront && !covered) Modifier.frontCardKeys(onOpen) else Modifier)
                .then(if (!covered) Modifier.swipeToDismiss(swipe) else Modifier),
    ) {
        Box(
            modifier =
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).then(
                    // The card's face gives way to the tab's as the card opens: read in draw, nothing recomposes.
                    if (tabMorph != null) Modifier.graphicsLayer { alpha = 1f - tabMorph() } else Modifier,
                ),
        ) { card() }
        if (tab != null && tabMorph != null) {
            tab(Modifier.matchParentSize().graphicsLayer { alpha = tabMorph() })
        }
        if (!isFront) {
            // Above the real card, so a tap on the strip is the strip's and never reaches a button underneath.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBringToFront,
                        ),
            )
        }
    }
}

/** Enter and Space on the front card open it, as they did on the focused card of the earlier stack. */
private fun Modifier.frontCardKeys(onOpen: () -> Unit): Modifier =
    focusable().onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key in OpenKeys) {
            onOpen()
            true
        } else {
            false
        }
    }

private val OpenKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)

/** The test tag of the slot of the card whose content id is [contentId]. */
internal fun streamStackCardTag(contentId: String): String = "stream-stack-card:$contentId"

/** How far a card may be drawn past the stack's side edges: the page's margin (the pager still cuts at its own). */
private val StackSideBleed = RiffleSpacing.l

/** The stack's clip: its own rectangle, widened by [bleedPx] on each side. */
private class SideBleedShape(private val bleedPx: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(-bleedPx, 0f, size.width + bleedPx, size.height))
}

/**
 * Remembers the cards a media stack had, so that when they change the stack's position follows the card the person was
 * on ([StackFollow]) instead of staying on the same index. A stack with no queue card is left as it was.
 */
internal class StackFollowMemory {
    private var last: List<StackCardRef> = emptyList()

    fun follow(
        items: List<StackEntry>,
        state: StreamStackState,
    ) {
        val now = items.map { StackCardRef(it.id, goneMeansPlayed = it.isQueueCard()) }
        val previous = last
        last = now
        val ids = now.map { it.id }
        if (previous.isEmpty() || previous.map { it.id } == ids) return
        if (previous.none { it.goneMeansPlayed } && now.none { it.goneMeansPlayed }) return
        val from = StackFling.nearest(state.position, previous.size)
        val to = StackFollow.indexAfterChange(previous, from, ids)
        state.followBy(to - from, items.size)
    }
}

private fun StackEntry.isQueueCard(): Boolean = ((this as? ContentEntry)?.content as? MediaContent)?.queueItem != null
