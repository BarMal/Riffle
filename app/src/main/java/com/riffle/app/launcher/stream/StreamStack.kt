@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import com.riffle.app.R
import com.riffle.app.launcher.DockFanSource
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dockFan
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackGeometry
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_FONT_SCALE_FOR_CARDS = 1.8f

private val StackMaxCardWidth = StreamMaxReadableWidth

internal fun StreamContent.iconHandle(): ItemImageHandle? =
    when (this) {
        is NotificationContent -> icon
        is MediaContent -> icon
        is RecentAppContent -> icon
        else -> null
    }

/**
 * The collapsed height each card type is drawn at in a stack. Calm's card is 168 dp tall ([StreamCardStackSpec]);
 * a notification, an event and an article without a lead image take that, and the ones that carry more (transport and
 * the scrubber, a lead image, a checklist) are taller.
 */
internal fun StreamContent.cardHeight(): Dp =
    when (this) {
        is NotificationContent -> StreamCardHeights.notification
        is MediaContent -> StreamCardHeights.media
        is EventContent -> StreamCardHeights.event
        is ArticleContent -> if (image != null) StreamCardHeights.article else StreamCardHeights.articleWithoutImage
        is TaskContent -> StreamCardHeights.tasks
        is RecentAppContent -> StreamCardHeights.event
    }

/** One line saying what a card is about, for the strip of it that peeks out below the card in front. */
@Composable
internal fun StreamContent.peekText(): String =
    when (this) {
        is NotificationContent -> title ?: text ?: hiddenText()
        is MediaContent -> title ?: stringResource(R.string.stream_media_unknown_title)
        is EventContent -> eventTitle(this)
        is ArticleContent -> title ?: hiddenText()
        is TaskContent -> title ?: hiddenText()
        is RecentAppContent -> label ?: hiddenText()
    }

/** The card for any content, so a Stack draws without knowing what is in it. Apps are tiles, not cards. */
@Composable
@Suppress("LongParameterList")
internal fun StreamCard(
    content: StreamContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Said before an event's time: the day, in a pile that spans days. */
    caption: String? = null,
) {
    when (content) {
        is NotificationContent ->
            NotificationCard(content, env, callbacks, groupKey, expanded, onExpandedChange, modifier)
        is MediaContent -> MediaCard(content, env, callbacks, groupKey, expanded, onExpandedChange, modifier)
        is EventContent -> EventCard(content, env, callbacks, groupKey, modifier, caption)
        is ArticleContent -> ArticleCard(content, env, callbacks, groupKey, modifier)
        is TaskContent -> TasksCard(listOf(content), env, callbacks, modifier)
        is RecentAppContent -> RecentAppTile(content, env, callbacks, groupKey, modifier)
    }
}

/**
 * One page's stack of cards (ADR 0003, Amendment 1, "Browse, then open"; Amendment 3, "Motion"): anchored to the Dock.
 * The front card sits just above the Dock, nearest the thumb, and older cards peek above it, each one the real card
 * clipped to its top strip ([FocusedStack]). A card that can expand (a notification's thread and reply, now playing's
 * queue) replaces the stack in place while expanded, and Back collapses it. A lone card is a stack of one, drawn at the
 * same place. Not gesture-only: Next and Previous card as accessibility actions and as buttons, a tap on a peeking
 * card, and the keyboard.
 */
@Composable
internal fun StreamStack(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    stackKey: String,
    modifier: Modifier = Modifier,
    captions: Map<String, String> = emptyMap(),
) {
    var expandedId by remember(stackKey) { mutableStateOf<String?>(null) }
    val stackState = remember(stackKey) { StreamStackState() }
    val expandedItem = items.firstOrNull { it.id.value == expandedId }
    if (expandedItem != null) {
        BackHandler { expandedId = null }
        StackColumn(modifier) {
            StreamCard(
                content = expandedItem,
                env = env,
                callbacks = callbacks,
                groupKey = groupKey,
                expanded = true,
                onExpandedChange = { expand -> expandedId = if (expand) expandedItem.id.value else null },
                caption = captions[expandedItem.id.value],
                modifier = Modifier.fillMaxWidth().widthIn(max = StackMaxCardWidth),
            )
        }
        return
    }
    if (items.size == 1) {
        val only = items.single()
        LoneCard(modifier) {
            StreamCard(
                content = only,
                env = env,
                callbacks = callbacks,
                groupKey = groupKey,
                expanded = false,
                onExpandedChange = { expand -> if (expand) expandedId = only.id.value },
                caption = captions[only.id.value],
                modifier =
                    Modifier.fillMaxWidth().widthIn(
                        max = StackMaxCardWidth,
                    ).height(only.cardHeight().scaledForFont()),
            )
        }
        return
    }
    FocusedStack(items, env, callbacks, groupKey, stackState, captions, modifier) { expandedId = it.id.value }
}

/** An expanded card: it can be taller than the page, so it scrolls. */
@Composable
private fun StackColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    // An expanded card is the front card of the fan.
    Column(
        modifier =
            modifier
                .dockFan(LocalDockFan.current)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = RiffleSpacing.l),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) { content() }
}

/**
 * A lone card, where the front card of a stack is: just above the Dock (it scrolls only if it is taller than the page).
 */
@Composable
private fun LoneCard(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier =
                Modifier
                    .dockFan(LocalDockFan.current)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = StackBottomInset),
        ) { content() }
    }
}

@Composable
private fun Dp.scaledForFont(): Dp = this * LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)

/**
 * Everything a stack leaves under the front card, in the order of the slots: used for the content description of a
 * peeking card, so assistive technology reads what the strip hides (the body, not only the line that shows).
 */
@Composable
internal fun StreamContent.spokenText(env: StreamEnvironment): String =
    when (this) {
        is NotificationContent ->
            listOfNotNull(
                appLabel,
                title ?: hiddenText(),
                text?.takeIf { it.isNotBlank() },
                env.formats.age(postedAtEpochMillis),
            )
        is MediaContent -> listOfNotNull(appLabel, peekText(), artist)
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
        is RecentAppContent -> listOfNotNull(label ?: hiddenText())
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
@Suppress("LongMethod", "LongParameterList")
private fun FocusedStack(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    state: StreamStackState,
    captions: Map<String, String>,
    modifier: Modifier,
    onExpand: (StreamContent) -> Unit,
) {
    val count = items.size
    val dockFan = LocalDockFan.current
    val haptics = LocalLauncherHaptics.current
    val density = LocalDensity.current
    val slopDp = with(density) { LocalViewConfiguration.current.touchSlop.toDp().value }
    val scope = rememberCoroutineScope()
    val latestReduced by rememberUpdatedState(env.reducedMotion)
    val latestCount by rememberUpdatedState(count)
    val latestHaptics by rememberUpdatedState(haptics)
    val tuning = StackDefaultTuning
    val tick = { latestHaptics.perform(LauncherHapticEvent.DETENT) }
    LaunchedEffect(count) { state.clampTo(count) }

    val scrollable =
        rememberScrollableState { delta ->
            // The platform's delta is the finger's, in px, positive downward (reverseDirection stays false).
            state.dragBy(deltaDp = delta / density.density, slopDp = slopDp, count = latestCount)
            delta
        }
    val flingBehavior =
        remember(state) {
            object : FlingBehavior {
                override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                    state.release(
                        velocityCards = cardsPerSecond(initialVelocity, density.density),
                        count = latestCount,
                        tuning = tuning,
                        reduced = latestReduced,
                        onTick = tick,
                    )
                    return 0f
                }
            }
        }
    // A move without a finger runs inside the scrollable's scroll, so a finger landing on it cancels it and catches it.
    val goTo: (Int) -> Boolean = { target ->
        val moves = target in 0 until latestCount && target != state.heading
        if (moves) {
            scope.launch { scrollable.scroll { state.glideTo(target, tuning, latestReduced, tick) } }
        }
        moves
    }
    val step: (Boolean) -> Boolean = { older ->
        state.stepFromHeading(older, latestCount)?.let(goTo) ?: false
    }

    // Every card of a pile is as tall as the tallest, so the strips that peek out above the front one line up.
    val cardHeight = items.maxOf { it.cardHeight() }.scaledForFont()
    val geometry = remember(cardHeight) { StackGeometry(cardHeight.value) }
    val frontIndex by remember(count) { derivedStateOf { StackFling.nearest(state.position, count) } }
    val composed by remember(count, geometry) { derivedStateOf { geometry.composedRange(state.position, count) } }
    val previous = stringResource(R.string.stream_previous_card)
    val next = stringResource(R.string.stream_next_card)
    val open = stringResource(R.string.stream_open_card)
    val bringToFront = stringResource(R.string.stream_bring_card_to_front)
    val position = stringResource(R.string.stream_card_position, frontIndex + 1, count)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cardWidth = maxWidth.coerceAtMost(StackMaxCardWidth)
        StackLayout(
            state = state,
            geometry = geometry,
            cardWidth = cardWidth,
            bottomInset = StackBottomInset,
            modifier =
                Modifier
                    .matchParentSize()
                    .clipToBounds()
                    .scrollable(
                        state = scrollable,
                        orientation = Orientation.Vertical,
                        flingBehavior = flingBehavior,
                    )
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (event.key) {
                                Key.DirectionUp, Key.PageUp -> step(true)
                                Key.DirectionDown, Key.PageDown -> step(false)
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
                key(item.id.value) {
                    val isFront = index == frontIndex
                    StackSlot(
                        index = index,
                        isFront = isFront,
                        rank = abs(index - frontIndex),
                        dockFan = dockFan,
                        tag = streamStackCardTag(item.id.value),
                        spoken = item.spokenText(env),
                        position = position,
                        open = open,
                        bringToFront = bringToFront,
                        onOpen = { callbacks.onAction(item, StreamAction.Open) },
                        onBringToFront = { goTo(index) },
                    ) {
                        StreamCard(
                            content = item,
                            env = env,
                            callbacks = callbacks,
                            groupKey = groupKey,
                            expanded = false,
                            onExpandedChange = { expand -> if (expand) onExpand(item) },
                            caption = captions[item.id.value],
                            modifier = Modifier.size(width = cardWidth, height = cardHeight),
                        )
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
@Suppress("LongParameterList")
private fun StackSlot(
    index: Int,
    isFront: Boolean,
    rank: Int,
    dockFan: DockFanSource?,
    tag: String,
    spoken: String,
    position: String,
    open: String,
    bringToFront: String,
    onOpen: () -> Unit,
    onBringToFront: () -> Unit,
    card: @Composable () -> Unit,
) {
    val semantics =
        if (isFront) {
            Modifier.semantics {
                testTag = tag
                traversalIndex = 0f
                stateDescription = position
                customActions =
                    listOf(
                        CustomAccessibilityAction(open) {
                            onOpen()
                            true
                        },
                    )
            }
        } else {
            // One node for the strip: the whole card's text, one action. The card's own controls are not reachable.
            Modifier.clearAndSetSemantics {
                testTag = tag
                traversalIndex = rank.toFloat()
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
                .dockFan(dockFan, rank)
                .clipToBounds()
                .then(semantics)
                .then(if (isFront) Modifier.frontCardKeys(onOpen) else Modifier),
    ) {
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) { card() }
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

/** Where the front card stops above the area's bottom edge (the Dock's top, with the page's own inset). */
private val StackBottomInset = RiffleSpacing.l
