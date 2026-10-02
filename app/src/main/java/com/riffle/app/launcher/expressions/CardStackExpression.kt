package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.CardStack
import com.riffle.app.launcher.CardStackInteraction
import com.riffle.app.launcher.CardStackScroll
import com.riffle.app.launcher.cardStackLiveActiveCardIndex
import com.riffle.app.launcher.cardStackSettledCardIndex
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.rememberCardStackTravel
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The CardStack expression: the lens result as a focused stack of cards, the focused one in front
 * and the next few peeking out below. This adapts the launcher's existing `CardStack` (platform
 * `scrollable` fling and magnet, see ADR 0002) and lays it out with its `CardStackLayoutPolicy`;
 * it adds no pointer handling of its own. Owns the vertical scroll axis.
 *
 * Not gesture-only: the focused card exposes Previous card / Next card / Open as accessibility
 * actions, and the stack answers keyboard navigation through the same callbacks. Needs a bounded
 * height from its host. Reduced motion is forwarded to the stack, which then drops travel and
 * keeps a static separation.
 */
@Composable
fun CardStackExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (Item, ItemAction) -> Unit = { _, _ -> },
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val items = remember(result) { result.allItems() }
    ExpressionStateHost(
        state = state,
        isEmpty = items.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
        maxContentWidth = ExpressionMaxReadableWidth,
    ) {
        FocusedCardStack(items = items, onItemClick = onItemClick, onAction = onAction, environment = environment)
    }
}

@Composable
private fun FocusedCardStack(
    items: List<Item>,
    onItemClick: (Item) -> Unit,
    onAction: (Item, ItemAction) -> Unit,
    environment: ExpressionEnvironment,
) {
    val focus = remember { CardStackFocusState() }
    var liveScrollPx by remember { mutableStateOf<Float?>(null) }
    val travel = rememberCardStackTravel(renderedCardPitchDp = STACK_PITCH_DP)
    val count = items.size
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
        StackLayoutPolicy.entries(
            cardCount = count,
            activeIndex = cardStackLiveActiveCardIndex(activeIndex, count, liveScrollPx, travel.distancePerCardPx),
            reducedMotion = environment.reducedMotion,
        )
    val focused = items[activeIndex]
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cardWidth = (maxWidth - StackInset * 2).coerceIn(StackMinCardWidth, StackMaxCardWidth)
        val cardHeight = stackCardHeight(maxHeight)
        CardStack(
            entries = entries,
            modifier = Modifier.matchParentSize(),
            reducedMotion = environment.reducedMotion,
            itemKey = { entry -> items[entry.cardIndex].lazyKey() },
            interaction =
                CardStackInteraction(
                    focusedItemKey = focused.lazyKey(),
                    settleTransitionId = focus.settleTransitionId,
                    settleStepCount = focus.settleStepCount,
                    onFocusRequest = { entry -> focus.moveTo(entry.cardIndex, count) },
                    onSettle = { drag, _ -> focus.moveTo(cardStackSettledCardIndex(drag, scroll), count) },
                    onNavigate = { direction -> focus.step(direction, count) },
                    onExpand = { onItemClick(focused) },
                    onLiveDrag = { dragPx -> liveScrollPx = dragPx },
                    scroll = scroll,
                ),
        ) { entry, cardModifier ->
            val item = items[entry.cardIndex]
            val semanticsForFocus =
                if (entry.cardIndex == activeIndex) {
                    Modifier.semantics {
                        stateDescription = "Card ${entry.cardIndex + 1} of $count"
                        customActions = stackActions(focus, count, item, onItemClick)
                    }
                } else {
                    Modifier
                }
            ItemCard(
                item = item,
                environment = environment,
                onItemClick = onItemClick,
                onAction = onAction,
                modifier = cardModifier.size(width = cardWidth, height = cardHeight).then(semanticsForFocus),
                hideEnabled = entry.cardIndex == activeIndex,
            )
        }
    }
}

private fun stackActions(
    focus: CardStackFocusState,
    count: Int,
    item: Item,
    onItemClick: (Item) -> Unit,
): List<CustomAccessibilityAction> =
    listOf(
        CustomAccessibilityAction(PREVIOUS_CARD_LABEL) { focus.step(CardStackNavigationDirection.PREVIOUS, count) },
        CustomAccessibilityAction(NEXT_CARD_LABEL) { focus.step(CardStackNavigationDirection.NEXT, count) },
        CustomAccessibilityAction(OPEN_CARD_LABEL) {
            onItemClick(item)
            true
        },
    )

private fun stackCardHeight(available: Dp): Dp =
    if (available == Dp.Infinity) {
        StackMaxCardHeight
    } else {
        (available * STACK_HEIGHT_FRACTION).coerceIn(StackMinCardHeight, StackMaxCardHeight)
    }

internal const val PREVIOUS_CARD_LABEL = "Previous card"
internal const val NEXT_CARD_LABEL = "Next card"
internal const val OPEN_CARD_LABEL = "Open card"

/** Distance between adjacent cards' resting positions, in dp (the policy's vertical step). */
private const val STACK_PITCH_DP = 28f
private const val STACK_HEIGHT_FRACTION = 0.6f

private val StackInset = RiffleSpacing.xl
private val StackMinCardWidth = 240.dp
private val StackMaxCardWidth = 480.dp
private val StackMinCardHeight = 160.dp
private val StackMaxCardHeight = 360.dp

/** Focused card centred, later cards peeking below it, one earlier card just above. */
private val StackLayoutPolicy =
    CardStackLayoutPolicy(
        maxVisibleDepth = 3,
        offsetStep = 0f,
        offsetDirection = 0f,
        verticalOffsetStep = STACK_PITCH_DP,
        reducedMotionScaleStep = 0f,
        reducedMotionOffsetStep = 0f,
        aboveFocusDepth = 1,
        composedDepthMargin = 1,
    )
