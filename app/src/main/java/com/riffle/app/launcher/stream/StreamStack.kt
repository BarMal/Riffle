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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.CardStack
import com.riffle.app.launcher.CardStackInteraction
import com.riffle.app.launcher.CardStackOrientation
import com.riffle.app.launcher.CardStackScroll
import com.riffle.app.launcher.cardStackLiveActiveCardIndex
import com.riffle.app.launcher.cardStackSettledCardIndex
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.expressions.CardStackFocusState
import com.riffle.app.launcher.rememberCardStackTravel
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/** Distance between neighbouring cards' resting positions, in dp: how much of the next card peeks out. */
private const val STACK_PITCH_DP = 28f
private const val MAX_FONT_SCALE_FOR_CARDS = 1.8f

private val StackPeek = RiffleSpacing.xl + RiffleSpacing.xs
private val StackMinCardWidth = 240.dp
private val StackMaxCardWidth = 480.dp

/** The focused card in front and its neighbours peeking out either side; the platform's scroll and fling move it. */
private val StreamStackPolicy =
    CardStackLayoutPolicy(
        maxVisibleDepth = 2,
        offsetStep = 0f,
        offsetDirection = 0f,
        verticalOffsetStep = STACK_PITCH_DP,
        reducedMotionScaleStep = 0f,
        reducedMotionOffsetStep = 0f,
        aboveFocusDepth = 1,
        composedDepthMargin = 1,
    )

internal fun StreamContent.canExpand(): Boolean =
    when (this) {
        is NotificationContent ->
            thread.isNotEmpty() ||
                actions.any {
                    it == com.riffle.core.domain.launcher.stream.NotificationAction.REPLY
                }
        is MediaContent -> queue.isNotEmpty()
        else -> false
    }

private fun StreamContent.iconHandle(): ItemImageHandle? =
    when (this) {
        is NotificationContent -> icon
        is MediaContent -> icon
        is RecentAppContent -> icon
        else -> null
    }

internal fun StreamContent.cardHeight(): Dp =
    when (this) {
        is NotificationContent -> StreamCardHeights.notification
        is MediaContent -> StreamCardHeights.media
        is EventContent -> StreamCardHeights.event
        is ArticleContent -> if (image != null) StreamCardHeights.article else StreamCardHeights.articleWithoutImage
        is TaskContent -> StreamCardHeights.tasks
        is RecentAppContent -> StreamCardHeights.event
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
    /** A card behind the focused one: its glass and colour only, so no text shows through the glass in front. */
    hollow: Boolean = false,
) {
    if (hollow) {
        StreamCardSurface(content.id.value, content.appRef(), content.iconHandle(), env, modifier) {
            Box(modifier = it.fillMaxSize())
        }
        return
    }
    when (content) {
        is NotificationContent ->
            NotificationCard(content, env, callbacks, groupKey, expanded, onExpandedChange, modifier)
        is MediaContent -> MediaCard(content, env, callbacks, groupKey, expanded, onExpandedChange, modifier)
        is EventContent -> EventCard(content, env, callbacks, groupKey, modifier)
        is ArticleContent -> ArticleCard(content, env, callbacks, groupKey, modifier)
        is TaskContent -> TasksCard(listOf(content), env, callbacks, modifier)
        is RecentAppContent -> RecentAppTile(content, env, callbacks, groupKey, modifier)
    }
}

/**
 * One stack of cards (ADR 0003): the focused card in front, the others peeking out, moved by the platform's scrollable
 * and fling through the existing card-stack physics (the stack runs along the horizontal axis, so the page's own
 * vertical scroll is never contested). A card that can expand (a notification's thread and reply, now playing's queue)
 * replaces the stack while expanded, and Back collapses it. Not gesture-only: previous and next buttons and the same
 * as accessibility actions, and keyboard navigation through the stack's own handling.
 */
@Composable
internal fun StreamStack(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
) {
    var expandedId by remember(groupKey) { mutableStateOf<String?>(null) }
    val expandedItem = items.firstOrNull { it.id.value == expandedId }
    if (expandedItem != null) {
        BackHandler { expandedId = null }
        StreamCard(
            content = expandedItem,
            env = env,
            callbacks = callbacks,
            groupKey = groupKey,
            expanded = true,
            onExpandedChange = { expand -> expandedId = if (expand) expandedItem.id.value else null },
            modifier = modifier.fillMaxWidth().widthIn(max = StackMaxCardWidth),
        )
        return
    }
    if (items.size == 1) {
        val only = items.single()
        StreamCard(
            content = only,
            env = env,
            callbacks = callbacks,
            groupKey = groupKey,
            expanded = false,
            onExpandedChange = { expand -> if (expand) expandedId = only.id.value },
            modifier =
                modifier.fillMaxWidth().widthIn(
                    max = StackMaxCardWidth,
                ).height(only.cardHeight().scaledForFont()),
        )
        return
    }
    FocusedStack(items, env, callbacks, groupKey, modifier) { expandedId = it.id.value }
}

@Composable
private fun Dp.scaledForFont(): Dp = this * LocalDensity.current.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)

@Composable
@Suppress("LongMethod")
private fun FocusedStack(
    items: List<StreamContent>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier,
    onExpand: (StreamContent) -> Unit,
) {
    val count = items.size
    val focus = remember(groupKey) { CardStackFocusState() }
    var liveScrollPx by remember { mutableStateOf<Float?>(null) }
    val travel = rememberCardStackTravel(renderedCardPitchDp = STACK_PITCH_DP)
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
        StreamStackPolicy.entries(
            cardCount = count,
            activeIndex = cardStackLiveActiveCardIndex(activeIndex, count, liveScrollPx, travel.distancePerCardPx),
            reducedMotion = env.reducedMotion,
        )
    val focused = items[activeIndex]
    val stackHeight = items.maxOf { it.cardHeight() }.scaledForFont()
    val previous = stringResource(R.string.stream_previous_card)
    val next = stringResource(R.string.stream_next_card)
    val open = stringResource(R.string.stream_open_card)
    val position = stringResource(R.string.stream_card_position, activeIndex + 1, count)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(stackHeight),
            contentAlignment = Alignment.Center,
        ) {
            val cardWidth = (maxWidth - StackPeek * 2).coerceIn(StackMinCardWidth, StackMaxCardWidth)
            CardStack(
                entries = entries,
                modifier = Modifier.matchParentSize(),
                reducedMotion = env.reducedMotion,
                itemKey = { entry -> items[entry.cardIndex].id.value },
                orientation = CardStackOrientation.HORIZONTAL,
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
                    modifier =
                        cardModifier
                            .size(width = cardWidth, height = item.cardHeight().scaledForFont())
                            .then(focusSemantics),
                )
            }
        }
        StackPager(activeIndex, count, previous, next, position) { direction -> focus.step(direction, count) }
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
    onStep: (CardStackNavigationDirection) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = { onStep(CardStackNavigationDirection.PREVIOUS) },
            enabled = index > 0,
            modifier = Modifier.size(StreamMinTarget),
        ) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = previousLabel) }
        Text(
            text = positionText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { onStep(CardStackNavigationDirection.NEXT) },
            enabled = index < count - 1,
            modifier = Modifier.size(StreamMinTarget),
        ) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = nextLabel) }
    }
}
