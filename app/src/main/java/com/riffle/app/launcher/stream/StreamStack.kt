@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.CardStack
import com.riffle.app.launcher.CardStackInteraction
import com.riffle.app.launcher.CardStackOrientation
import com.riffle.app.launcher.CardStackScroll
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalDockFan
import com.riffle.app.launcher.cardStackLiveActiveCardIndex
import com.riffle.app.launcher.cardStackSettledCardIndex
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dockFan
import com.riffle.app.launcher.expressions.CardStackFocusState
import com.riffle.app.launcher.rememberCardStackTravel
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
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
    /** A card behind the focused one: its glass, its colour and one line of what it is, no controls. */
    hollow: Boolean = false,
    /** Said before an event's time: the day, in a pile that spans days. */
    caption: String? = null,
) {
    if (hollow) {
        StreamCardSurface(
            content.id.value,
            content.appRef(),
            content.iconHandle(),
            env,
            modifier.showOnlyBottom(StreamCardStackSpec.PITCH_DP.dp),
        ) {
            Box(modifier = it.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                StreamPeekRow(content, env)
            }
        }
        return
    }
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
 * Draws only the bottom [strip] of this card. A card behind the focused one is only ever seen as the strip below the
 * card in front; drawing the rest would show its glass through the front card's.
 */
internal fun Modifier.showOnlyBottom(strip: Dp): Modifier =
    drawWithContent {
        clipRect(top = (size.height - strip.toPx()).coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
    }

/** The strip of a card that shows below the one in front of it: its icon and a line, one pitch tall. */
@Composable
private fun StreamPeekRow(
    content: StreamContent,
    env: StreamEnvironment,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(StreamCardStackSpec.PITCH_DP.dp)
                .padding(horizontal = RiffleSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        content.iconHandle()?.let { StreamIcon(it, env, RiffleSpacing.xl) }
        Text(
            text = content.peekText(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One page's stack of cards (ADR 0003, Views): Calm's pile, run vertically. The focused card sits in front near the top
 * with the next ones peeking out below it, [StreamCardStackSpec] cards ahead and behind, moved by the platform's
 * scrollable and fling through the existing card-stack physics and magnet settle; the page's own horizontal swipe is
 * never contested because the stack only takes vertical drags. A card that can expand (a notification's thread and
 * reply, now playing's queue) replaces the stack in place while expanded, and Back collapses it. Not gesture-only:
 * previous and next buttons, the same as accessibility actions, and keyboard navigation.
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
        StackColumn(modifier) {
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
    FocusedStack(items, env, callbacks, groupKey, stackKey, captions, modifier) { expandedId = it.id.value }
}

/** A lone or expanded card: it can be taller than the page, so it scrolls. */
@Composable
private fun StackColumn(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    // A lone or expanded card is the front card of the fan.
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

@Composable
private fun Dp.scaledForFont(): Dp = this * LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)

@Composable
@Suppress("LongMethod", "LongParameterList")
private fun FocusedStack(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    stackKey: String,
    captions: Map<String, String>,
    modifier: Modifier,
    onExpand: (StreamContent) -> Unit,
) {
    val count = items.size
    val dockFan = LocalDockFan.current
    val focus = remember(stackKey) { CardStackFocusState() }
    var liveScrollPx by remember { mutableStateOf<Float?>(null) }
    val travel = rememberCardStackTravel(renderedCardPitchDp = StreamCardStackSpec.PITCH_DP)
    val activeIndex = focus.clamped(count)
    val scroll =
        CardStackScroll(
            cardCount = count,
            activeCardIndex = activeIndex,
            distancePerCardPx = travel.distancePerCardPx,
            flingVelocityThresholdPxPerSecond = travel.flingVelocityThresholdPxPerSecond,
            maxFlingCards = travel.maxFlingCards,
        )
    val entries =
        StreamCardStackSpec.policy.entries(
            cardCount = count,
            activeIndex = cardStackLiveActiveCardIndex(activeIndex, count, liveScrollPx, travel.distancePerCardPx),
            reducedMotion = env.reducedMotion,
        )
    val focused = items[activeIndex]
    // Every card of a pile is as tall as the tallest, so the strips that peek out below the front one line up.
    val cardHeight = items.maxOf { it.cardHeight() }.scaledForFont()
    val previous = stringResource(R.string.stream_previous_card)
    val next = stringResource(R.string.stream_next_card)
    val open = stringResource(R.string.stream_open_card)
    val position = stringResource(R.string.stream_card_position, activeIndex + 1, count)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cardWidth = maxWidth.coerceAtMost(StackMaxCardWidth)
        CardStack(
            entries = entries,
            modifier = Modifier.matchParentSize().clipToBounds(),
            reducedMotion = env.reducedMotion,
            itemKey = { entry -> items[entry.cardIndex].id.value },
            orientation = CardStackOrientation.VERTICAL,
            stackPeakFraction = StreamCardStackSpec.PEAK_FRACTION,
            interaction =
                CardStackInteraction(
                    focusedItemKey = focused.id.value,
                    settleTransitionId = focus.settleTransitionId,
                    settleStepCount = focus.settleStepCount,
                    onFocusRequest = { entry -> focus.moveTo(entry.cardIndex, count) },
                    onSettle = { drag, _ -> focus.moveTo(cardStackSettledCardIndex(drag, scroll), count) },
                    onNavigate = { direction -> focus.step(direction, count) },
                    onExpand = { callbacks.onAction(focused, StreamAction.Open) },
                    onLiveDrag = { dragPx -> liveScrollPx = dragPx },
                    scroll = scroll,
                ),
        ) { entry, cardModifier ->
            val item = items[entry.cardIndex]
            val isFocused = entry.cardIndex == activeIndex
            val focusSemantics =
                if (isFocused) {
                    Modifier.semantics {
                        stateDescription = position
                        customActions =
                            listOf(
                                CustomAccessibilityAction(previous) {
                                    focus.step(CardStackNavigationDirection.PREVIOUS, count)
                                },
                                CustomAccessibilityAction(next) {
                                    focus.step(CardStackNavigationDirection.NEXT, count)
                                },
                                CustomAccessibilityAction(open) {
                                    callbacks.onAction(item, StreamAction.Open)
                                    true
                                },
                            )
                    }
                } else {
                    Modifier
                }
            StreamCard(
                content = item,
                env = env,
                callbacks = callbacks,
                groupKey = groupKey,
                expanded = false,
                onExpandedChange = { expand -> if (expand) onExpand(item) },
                hollow = !isFocused,
                caption = captions[item.id.value],
                modifier =
                    cardModifier
                        // The cards come out of the Dock in sequence, the focused one first.
                        .dockFan(dockFan, abs(entry.cardIndex - activeIndex))
                        .size(width = cardWidth, height = cardHeight)
                        .then(focusSemantics),
            )
        }
        StackPager(
            index = activeIndex,
            count = count,
            previousLabel = previous,
            nextLabel = next,
            positionText = position,
            modifier = Modifier.align(Alignment.BottomCenter).dockFan(dockFan).padding(bottom = RiffleSpacing.s),
        ) { direction -> focus.step(direction, count) }
    }
}

/** Previous and next buttons with the position: the non-gesture way through a stack. */
@Composable
private fun StackPager(
    index: Int,
    count: Int,
    previousLabel: String,
    nextLabel: String,
    positionText: String,
    modifier: Modifier = Modifier,
    onStep: (CardStackNavigationDirection) -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        weight = GlassWeight.Thin,
        shape = RiffleShapes.full,
        tint = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(
                onClick = { onStep(CardStackNavigationDirection.PREVIOUS) },
                enabled = index > 0,
                modifier = Modifier.size(StreamMinTarget),
            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = previousLabel) }
            Text(
                text = positionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { onStep(CardStackNavigationDirection.NEXT) },
                enabled = index < count - 1,
                modifier = Modifier.size(StreamMinTarget),
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = nextLabel) }
        }
    }
}
