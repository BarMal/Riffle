package com.riffle.app.launcher.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.CountRecomposition
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SearchGridResult
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.profileDisplayLabel
import com.riffle.app.launcher.stream.LocalStreamCardSizes
import com.riffle.app.launcher.stream.StackLayout
import com.riffle.app.launcher.stream.StackMover
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamMaxReadableWidth
import com.riffle.app.launcher.stream.StreamStackState
import com.riffle.app.launcher.stream.rememberStackMover
import com.riffle.app.launcher.stream.stackSlot
import com.riffle.app.launcher.stream.stripCut
import com.riffle.core.domain.launcher.search.SearchStackCard
import com.riffle.core.domain.launcher.search.SearchStackLayout
import com.riffle.core.domain.launcher.search.SearchStackTap
import com.riffle.core.domain.launcher.search.StripTapOutcome
import com.riffle.core.domain.launcher.stream.QuickAction
import com.riffle.core.domain.launcher.stream.StackFling
import com.riffle.core.domain.launcher.stream.StackGeometry
import kotlin.math.abs

/** The test tag of the search stack's area (its fixed rectangle). */
internal const val SEARCH_STACK_TEST_TAG = "search-stack"

/** The test tag of the slot of the card with [key] (the front card's whole card, a strip's visible part). */
internal fun searchStackCardTag(key: String): String = "search-stack-card:$key"

/** The test tag of the quiet mark on the strip of the card with [key], which has more to show. */
internal fun searchStackMoreMarkTag(key: String): String = "search-stack-more:$key"

/** The test tag of the line that says what an app's newest notification says. */
internal const val SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG = "search-stack-notification-line"

/** Where the front card stops above the area's bottom edge: the field's own seam is just under it. */
private val SearchStackBottomInset = RiffleSpacing.xs

/** What the stack does for the card it is told about; the surface wires them to the shell and the one gateway. */
internal class SearchStackActions(
    /** Opens the result of a card (the front card's tap, a strip with nothing more, Enter). */
    val open: (SearchStackCard) -> Unit,
    /** Launches one quick action of an app card, through the one action gateway. */
    val launchQuickAction: (SearchStackCard.App, QuickAction) -> Unit,
)

/** The action that opens a card's result: the one the earlier presentations' rows run. */
internal fun SearchStackCard.openAction(): LauncherShellAction =
    when (this) {
        is SearchStackCard.App -> SearchGridResult.App(app).action
        is SearchStackCard.Shortcut -> SearchGridResult.Shortcut(shortcut).action
        is SearchStackCard.Setting -> SearchGridResult.Setting(result).action
        is SearchStackCard.Web -> LauncherShellAction.SearchWeb(query)
    }

/** The card in front of [state] among [count] cards (the one Enter opens), or null with no cards. */
internal fun StreamStackState.frontIndex(count: Int): Int? =
    if (count <= 0) null else StackFling.nearest(position, count)

/**
 * Search results as a stack of cards, the Stream's own: the best match is the front card (the app's icon and name, its
 * quick actions and a line for its newest notification), the next matches are strips tucked under it in rank order,
 * and "Search web" is the last card. The stack stands in one fixed rectangle at the bottom of the room it is given,
 * just
 * above the search field: the front card is one height and the area one height for a posture, so a new query changes
 * what the cards say and never where they stand ([SearchStackLayout]); only less room (the keyboard) shows fewer
 * strips. The strips that show are the posture's (the Stream's); the rest are reached by dragging the stack, its Next
 * and Previous card actions or the keys, as in the Stream.
 *
 * A tap on the front card opens it. A tap on a strip with quick actions or a notification brings it to the front and
 * shows them; a strip with nothing more than a name opens its result ([SearchStackTap]), and the strips that have more
 * carry a small quiet mark so the difference can be told beforehand. The stack's position is read in layout and in
 * draw ([StackLayout], [stripCut]), so a drag recomposes nothing.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun SearchResultStack(
    cards: List<SearchStackCard>,
    stackState: StreamStackState,
    env: StreamEnvironment,
    context: AppListContext,
    emptyText: String,
    actions: SearchStackActions,
    modifier: Modifier = Modifier,
) {
    val sizes = LocalStreamCardSizes.current
    val count = cards.size
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val desired = SearchStackLayout.desiredHeightDp(sizes, SearchStackBottomInset.value)
        // The posture's height, shortened only by the room there is (the keyboard): never by how many results.
        val area = minOf(desired, maxHeight.value).dp
        val peeks = SearchStackLayout.peeksFor(sizes, area.value, SearchStackBottomInset.value)
        val frontHeight = SearchStackLayout.frontHeightDp(sizes)
        val cardWidth = maxWidth.coerceAtMost(StreamMaxReadableWidth)
        val geometry =
            remember(count, sizes, peeks) {
                StackGeometry(
                    heightsDp = List(count) { frontHeight },
                    pitchDp = sizes.pitchDp,
                    visiblePeeks = peeks,
                    scaleStep = sizes.scaleStep,
                )
            }
        SideEffect { stackState.clampTo(count) }
        val mover = rememberStackMover(stackState, count, env.reducedMotion)
        val frontIndex by remember(count) { derivedStateOf { StackFling.nearest(stackState.position, count) } }
        val composed by remember(
            count,
            geometry,
        ) { derivedStateOf { geometry.composedRange(stackState.position, count) } }
        val previous = stringResource(R.string.stream_previous_card)
        val next = stringResource(R.string.stream_next_card)
        val latestActions by rememberUpdatedState(actions)
        Box(modifier = Modifier.fillMaxWidth().height(area).testTag(SEARCH_STACK_TEST_TAG)) {
            StackLayout(
                state = stackState,
                geometry = geometry,
                cardWidth = cardWidth,
                bottomInset = SearchStackBottomInset,
                modifier =
                    Modifier
                        .matchParentSize()
                        .scrollable(
                            state = mover.scrollable,
                            orientation = Orientation.Vertical,
                            enabled = count > 1,
                            flingBehavior = mover.flingBehavior,
                        )
                        .semantics {
                            isTraversalGroup = true
                            if (count > 1) {
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(previous) { mover.step(false) },
                                        CustomAccessibilityAction(next) { mover.step(true) },
                                    )
                            }
                        },
            ) {
                for (index in composed) {
                    val card = cards[index]
                    key(card.key) {
                        SearchStackSlot(
                            index = index,
                            count = count,
                            card = card,
                            isFront = index == frontIndex,
                            rank = abs(index - frontIndex),
                            cut = Modifier.stripCut(index, stackState, geometry, cardWidth),
                            mover = mover,
                            actions = { latestActions },
                        ) {
                            SearchStackCardContent(
                                card = card,
                                isFront = index == frontIndex,
                                env = env,
                                context = context,
                                emptyText = emptyText,
                                actions = { latestActions },
                                modifier = Modifier.size(width = cardWidth, height = frontHeight.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One card's slot. The front card is one node for assistive technology, named for its result, whose click opens it
 * and whose quick actions are custom actions; a strip is one button that says (with a state) whether it has more to
 * show, and what its tap does follows [SearchStackTap].
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun SearchStackSlot(
    index: Int,
    count: Int,
    card: SearchStackCard,
    isFront: Boolean,
    rank: Int,
    cut: Modifier,
    mover: StackMover,
    actions: () -> SearchStackActions,
    content: @Composable () -> Unit,
) {
    val name = card.spokenName()
    val openLabel = stringResource(R.string.search_stack_open_result, name)
    val bringLabel = stringResource(R.string.search_stack_bring_to_front)
    val hasMoreState = stringResource(R.string.search_stack_has_more)
    val position = stringResource(R.string.stream_card_position, index + 1, count)
    val tag = searchStackCardTag(card.key)
    CountRecomposition("search-stack-slot:$index")
    val onStripTap = {
        when (SearchStackTap.onStrip(card)) {
            StripTapOutcome.BRING_TO_FRONT -> mover.goTo(index)
            StripTapOutcome.OPEN -> actions().open(card)
        }
        Unit
    }
    val semantics =
        if (isFront) {
            val quick = (card as? SearchStackCard.App)?.quickActions.orEmpty()
            Modifier.semantics {
                testTag = tag
                traversalIndex = 0f
                role = Role.Button
                contentDescription = name
                if (count > 1) stateDescription = position
                onClick(label = openLabel) {
                    actions().open(card)
                    true
                }
                if (quick.isNotEmpty() && card is SearchStackCard.App) {
                    customActions =
                        quick.map { action ->
                            CustomAccessibilityAction(action.label) {
                                actions().launchQuickAction(card, action)
                                true
                            }
                        }
                }
            }
        } else {
            Modifier.clearAndSetSemantics {
                testTag = tag
                traversalIndex = rank.toFloat()
                role = Role.Button
                contentDescription = name
                if (card.hasMore) stateDescription = hasMoreState
                onClick(label = if (card.hasMore) bringLabel else openLabel) {
                    onStripTap()
                    true
                }
            }
        }
    Box(
        modifier =
            Modifier
                .stackSlot(index)
                .then(cut)
                .then(semantics)
                .then(
                    if (isFront) {
                        Modifier.pointerInput(card.key) { detectTapGestures { actions().open(card) } }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) { content() }
        if (!isFront) {
            // Above the real card, so a tap on the strip is the strip's and never reaches a chip underneath.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onStripTap,
                        ),
            )
        }
    }
}

/** What a card says to assistive technology: the app's (or result's) name, with its profile as the lists say it. */
@Composable
internal fun SearchStackCard.spokenName(): String =
    when (this) {
        is SearchStackCard.App -> app.identity.profile.profileDisplayLabel(app.label)
        is SearchStackCard.Shortcut -> SearchGridResult.Shortcut(shortcut).label
        is SearchStackCard.Setting -> result.title
        is SearchStackCard.Web -> stringResource(R.string.search_stack_web_title)
    }
