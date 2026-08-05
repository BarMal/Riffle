@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher.rss

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.CardStack
import com.riffle.app.launcher.CardStackInteraction
import com.riffle.app.launcher.AdaptiveStageCardBackground
import com.riffle.app.launcher.AdaptiveStageCardDetailState
import com.riffle.app.launcher.AdaptiveStageCardSurface
import com.riffle.app.launcher.AdaptiveStageDetailRecoveryMessage
import com.riffle.app.launcher.adaptiveStageNotificationStackEntries
import com.riffle.app.launcher.adaptiveStageRendererCapabilities
import com.riffle.app.launcher.adaptiveStageResolvedContentPadding
import com.riffle.app.launcher.transitionDurationMillis
import com.riffle.core.domain.launcher.cards.CardExpansionPhase
import com.riffle.core.domain.launcher.cards.CardExpansionState
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.rss.FeedStage
import com.riffle.core.domain.launcher.rss.FeedStageId
import com.riffle.core.domain.launcher.rss.FeedStageLifecycle
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageMotion
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp
import kotlinx.coroutines.delay

/**
 * Renders one reconciled [FeedStage]: honest loading/empty/stale/unavailable/error/profile-locked
 * placeholders, and -- when articles are available -- a focus-only card stack with accessible
 * previous/next navigation and a sanitized detail flow. Mirrors `AdaptiveStageNotificationStack` and
 * `AdaptiveStageCardDetailSurface` (`AdaptiveStageAppStageSurface.kt`/`AdaptiveStageCardDetailSurface.kt`).
 *
 * Artwork is only ever decoded from already-cached local bytes via [artworkLoader]; there is no
 * live network image loader here (see ADR 0001). [browserLauncher] is the only way an article's
 * canonical URL is ever opened -- never a silent or embedded navigation.
 */
@Composable
@Suppress("LongParameterList")
fun FeedStageSurface(
    stage: FeedStage,
    articles: List<CachedFeedArticle>,
    appearance: AdaptiveStageAppearanceSettings,
    globalReducedMotion: Boolean = false,
    artworkLoader: FeedArtworkLoader = EmptyFeedArtworkLoader,
    browserLauncher: FeedArticleBrowserLauncher = NoOpFeedArticleBrowserLauncher,
    focusedDigest: String? = null,
    onFocusedDigestChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val detailState = rememberFeedCardDetailState(stage.id, appearance.motion, globalReducedMotion)
    val cards = remember(stage.items, articles) { stage.joinArticleCards(articles) }
    LaunchedEffect(cards) {
        detailState.reconcile(cards.map { card -> LauncherCardId(card.digest) }.toSet())
    }
    // FeedArticleStack reports focus changes back through a callback rather than owning its own
    // saveable state, so a caller that leaves focusedDigest/onFocusedDigestChanged at their no-op
    // defaults needs somewhere durable to land: without this, every recomposition would re-read
    // the original (unchanged) focusedDigest argument and navigation would appear to silently
    // revert. This remembers the latest reported focus and feeds it back in as the source of
    // truth, while still forwarding changes to an external caller that does supply its own state.
    var internalFocusedDigest by rememberSaveable(stage.id.feedId.value) { mutableStateOf(focusedDigest) }

    when (stage.lifecycle) {
        FeedStageLifecycle.LOADING -> FeedStagePlaceholder(FEED_MESSAGE_LOADING, modifier = modifier)
        FeedStageLifecycle.UNAVAILABLE -> FeedStagePlaceholder(FEED_MESSAGE_UNAVAILABLE, modifier = modifier)
        FeedStageLifecycle.ERROR -> FeedStagePlaceholder(FEED_MESSAGE_ERROR, modifier = modifier)
        FeedStageLifecycle.PROFILE_LOCKED -> FeedStagePlaceholder(FEED_MESSAGE_PROFILE_LOCKED, modifier = modifier)
        FeedStageLifecycle.EMPTY -> FeedStagePlaceholder(FEED_MESSAGE_EMPTY, modifier = modifier)
        FeedStageLifecycle.ACTIVE, FeedStageLifecycle.STALE ->
            if (cards.isEmpty()) {
                // A card removed (cache cleared, feed removed) while its detail was open still
                // gets an explanation here, mirroring AdaptiveStageEmptyStage's recovery message.
                FeedStagePlaceholder(
                    FEED_MESSAGE_EMPTY,
                    recoveryMessage = detailState.sourceRemovalMessage,
                    modifier = modifier,
                )
            } else {
                FeedArticleStack(
                    stageId = stage.id,
                    cards = cards,
                    appearance = appearance,
                    globalReducedMotion = globalReducedMotion,
                    isStale = stage.lifecycle == FeedStageLifecycle.STALE,
                    artworkLoader = artworkLoader,
                    browserLauncher = browserLauncher,
                    detailState = detailState,
                    focusedDigest = internalFocusedDigest,
                    onFocusedDigestChanged = { digest ->
                        internalFocusedDigest = digest
                        onFocusedDigestChanged(digest)
                    },
                    modifier = modifier,
                )
            }
    }
}

internal const val FEED_MESSAGE_LOADING = "Loading feed…"
internal const val FEED_MESSAGE_UNAVAILABLE = "This feed is turned off."
internal const val FEED_MESSAGE_ERROR = "Couldn't load this feed. It will try again later."
internal const val FEED_MESSAGE_PROFILE_LOCKED = "This profile is locked."
internal const val FEED_MESSAGE_EMPTY = "No articles yet."
internal const val FEED_MESSAGE_STALE_BANNER = "Showing saved articles. Refresh to check for updates."
internal const val FEED_OPEN_IN_BROWSER_LABEL = "Open in browser"
internal const val FEED_BACK_LABEL = "Back"
internal const val FEED_PREVIOUS_ARTICLE_LABEL = "Previous article"
internal const val FEED_NEXT_ARTICLE_LABEL = "Next article"
internal const val FEED_SHOW_DETAILS_LABEL = "Show details"

@Composable
private fun FeedStagePlaceholder(
    message: String,
    modifier: Modifier,
    recoveryMessage: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
        )
        AdaptiveStageDetailRecoveryMessage(recoveryMessage)
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun FeedArticleStack(
    stageId: FeedStageId,
    cards: List<FeedArticleCard>,
    appearance: AdaptiveStageAppearanceSettings,
    globalReducedMotion: Boolean,
    isStale: Boolean,
    artworkLoader: FeedArtworkLoader,
    browserLauncher: FeedArticleBrowserLauncher,
    detailState: AdaptiveStageCardDetailState,
    focusedDigest: String?,
    onFocusedDigestChanged: (String?) -> Unit,
    modifier: Modifier,
) {
    val cardIds = cards.map { card -> LauncherCardId(card.digest) }
    val controller = remember(stageId) { CardStackController() }
    val stackKey =
        remember(stageId) { CardStackKey("feed:${stageId.feedId.value}") }
    var previousCardIds by remember(stageId) { mutableStateOf(emptyList<LauncherCardId>()) }
    var settleTransitionId by rememberSaveable(stageId.feedId.value) { mutableIntStateOf(0) }
    val focusState = CardStackFocusState(stackKey, focusedDigest?.let(::LauncherCardId))

    LaunchedEffect(cardIds) {
        val reconciliation =
            if (focusState.focusedCardId == null) {
                controller.restore(focusState, cardIds)
            } else {
                controller.reconcile(focusState, previousCardIds, cardIds)
            }
        if (reconciliation is CardStackFocusResult.Applied) {
            onFocusedDigestChanged(reconciliation.state.focusedCardId?.value)
        }
        previousCardIds = cardIds
    }

    val activeIndex = cardIds.indexOf(focusState.focusedCardId).takeIf { index -> index >= 0 } ?: 0
    val activeCard = cards.getOrNull(activeIndex) ?: return

    LaunchedEffect(activeCard.digest) { onFocusedDigestChanged(activeCard.digest) }

    fun navigate(direction: CardStackNavigationDirection): Boolean {
        val result = controller.navigate(focusState, cardIds, direction)
        if (result is CardStackFocusResult.Applied) {
            if (result.focusChanged) settleTransitionId++
            onFocusedDigestChanged(result.state.focusedCardId?.value)
            return !result.boundaryReached
        }
        return false
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(appearance, globalReducedMotion, viewport) {
                appearance.resolveCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                    globalReducedMotion = globalReducedMotion,
                )
            }
        if (detailState.expansionState.isVisible) {
            cards
                .firstOrNull { card -> LauncherCardId(card.digest) == detailState.expansionState.cardId }
                ?.let { card ->
                    FeedArticleDetailSurface(
                        card = card,
                        detailState = detailState,
                        browserLauncher = browserLauncher,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isStale) {
                    Text(
                        text = FEED_MESSAGE_STALE_BANNER,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CardStack(
                        entries =
                            adaptiveStageNotificationStackEntries(
                                resolution = resolution,
                                cardCount = cards.size,
                                activeCardIndex = activeIndex,
                            ),
                        animationSpec = resolution.animation,
                        reducedMotion = resolution.reducedMotion,
                        itemKey = { entry -> cards[entry.cardIndex].digest },
                        interaction =
                            CardStackInteraction(
                                focusedItemKey = activeCard.digest,
                                settleTransitionId = settleTransitionId,
                                onFocusRequest = { entry ->
                                    controller
                                        .jumpTo(focusState, cardIds, cardIds[entry.cardIndex])
                                        .let { result ->
                                            if (result is CardStackFocusResult.Applied) {
                                                onFocusedDigestChanged(result.state.focusedCardId?.value)
                                            }
                                        }
                                },
                                onSettle = { drag, velocity ->
                                    controller
                                        .settle(
                                            focusState,
                                            cardIds,
                                            CardStackSettleRequest(
                                                focusedCardId = LauncherCardId(activeCard.digest),
                                                verticalDragPx = drag,
                                                verticalVelocityPxPerSecond = velocity,
                                                distanceThresholdPx = 64f,
                                                flingVelocityThresholdPxPerSecond = 500f,
                                            ),
                                        ).let { result ->
                                            if (result is CardStackFocusResult.Applied) {
                                                if (result.state.focusedCardId != focusState.focusedCardId) {
                                                    settleTransitionId++
                                                }
                                                onFocusedDigestChanged(result.state.focusedCardId?.value)
                                            }
                                        }
                                },
                                onNavigate = ::navigate,
                                onExpand = { detailState.expand(LauncherCardId(activeCard.digest)) },
                            ),
                    ) { entry, cardModifier ->
                        val card = cards[entry.cardIndex]
                        var artwork by remember(card.digest) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(card.digest, artworkLoader) {
                            artwork = artworkLoader.artworkForOrNull(card.digest)
                        }
                        val focusedSemantics =
                            if (entry.cardIndex == activeIndex) {
                                Modifier.semantics {
                                    contentDescription = feedCardContentDescription(card)
                                    stateDescription = "Card ${entry.cardIndex + 1} of ${cards.size}"
                                    liveRegion = LiveRegionMode.Polite
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction(FEED_PREVIOUS_ARTICLE_LABEL) {
                                                navigate(CardStackNavigationDirection.PREVIOUS)
                                            },
                                            CustomAccessibilityAction(FEED_NEXT_ARTICLE_LABEL) {
                                                navigate(CardStackNavigationDirection.NEXT)
                                            },
                                            CustomAccessibilityAction(FEED_SHOW_DETAILS_LABEL) {
                                                detailState.expand(LauncherCardId(card.digest))
                                                true
                                            },
                                        )
                                }
                            } else {
                                Modifier
                            }
                        AdaptiveStageCardSurface(
                            appearance = appearance,
                            background =
                                AdaptiveStageCardBackground(artwork = artwork, appSeed = stageId.feedId.value),
                            modifier =
                                cardModifier.size(
                                    width = resolution.cardWidthDp.dp,
                                    height = resolution.cardHeightDp.dp,
                                ).then(focusedSemantics),
                            contentPadding = adaptiveStageResolvedContentPadding(resolution),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, style = MaterialTheme.typography.titleMedium)
                                card.summary?.let { summary ->
                                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                FeedArticleNavigationControls(
                    position = activeIndex + 1,
                    count = cards.size,
                    onPrevious = { navigate(CardStackNavigationDirection.PREVIOUS) },
                    onNext = { navigate(CardStackNavigationDirection.NEXT) },
                )
                TextButton(onClick = { detailState.expand(LauncherCardId(activeCard.digest)) }) {
                    Text(FEED_SHOW_DETAILS_LABEL)
                }
                AdaptiveStageDetailRecoveryMessage(detailState.sourceRemovalMessage)
            }
        }
    }
}

private fun feedCardContentDescription(card: FeedArticleCard): String =
    buildString {
        append("Focused article card: ")
        append(card.title)
        card.summary?.let { summary ->
            append(". ")
            append(summary)
        }
    }

@Composable
private fun FeedArticleNavigationControls(
    position: Int,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = position > 1) { Text(FEED_PREVIOUS_ARTICLE_LABEL) }
        Text(
            text = "Article $position of $count",
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription = "Focused article position"
                    stateDescription = "Card $position of $count"
                },
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = onNext, enabled = position < count) { Text(FEED_NEXT_ARTICLE_LABEL) }
    }
}

/**
 * Sanitized article detail: title/author/date/summary as plain text only (never a WebView/
 * AndroidView rendering raw HTML), an explicit "Open in browser" action, and Back-to-stage
 * restoration via system Back and an explicit Back button. Mirrors `AdaptiveStageCardDetailSurface`.
 */
@Composable
internal fun FeedArticleDetailSurface(
    card: FeedArticleCard,
    detailState: AdaptiveStageCardDetailState,
    browserLauncher: FeedArticleBrowserLauncher,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    FeedArticleDetailContainer(detailState = detailState, onClose = onClose, modifier = modifier) {
        Text(card.title, style = MaterialTheme.typography.headlineSmall)
        card.author?.let { author -> Text(author, style = MaterialTheme.typography.labelLarge) }
        card.summary?.let { summary -> Text(summary, style = MaterialTheme.typography.bodyLarge) }
        card.canonicalUrl
            ?.takeIf(::isHttpsFeedArticleUrl)
            ?.let { url ->
                TextButton(onClick = { browserLauncher.launch(url) }) { Text(FEED_OPEN_IN_BROWSER_LABEL) }
            }
    }
}

@Composable
private fun FeedArticleDetailContainer(
    detailState: AdaptiveStageCardDetailState,
    onClose: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val phase = detailState.expansionState.phase
    val alpha by
        animateFloatAsState(
            targetValue = if (phase == CardExpansionPhase.COLLAPSING) 0f else 1f,
            animationSpec =
                if (detailState.reducedMotion) {
                    snap()
                } else {
                    tween(detailState.transitionDurationMillis(phase))
                },
            label = "feed-article-detail-alpha",
        )

    val closeDetail = {
        onClose()
        detailState.close()
    }
    BackHandler(enabled = detailState.expansionState.isVisible, onBack = closeDetail)

    LaunchedEffect(phase, detailState.reducedMotion) {
        if (phase == CardExpansionPhase.EXPANDING || phase == CardExpansionPhase.COLLAPSING) {
            if (!detailState.reducedMotion) delay(detailState.transitionDurationMillis(phase).toLong())
            detailState.completeTransition()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .verticalScroll(rememberScrollState())
                .semantics {
                    stateDescription = "Article details open"
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = closeDetail) { Text(FEED_BACK_LABEL) }
        content()
    }
}

/**
 * Transient, saveable detail ownership for one visible feed stage. Reuses the framework-generic
 * [AdaptiveStageCardDetailState] (it is keyed only on card/expansion state, not on `AppStageId`) so the
 * expand/collapse/reconcile policy stays identical to the app-stage detail flow.
 */
@Composable
internal fun rememberFeedCardDetailState(
    stageId: FeedStageId,
    motion: AdaptiveStageMotion,
    globalReducedMotion: Boolean = false,
): AdaptiveStageCardDetailState {
    var expansion by
        rememberSaveable(stageId.feedId.value, stateSaver = FeedCardExpansionStateSaver) {
            mutableStateOf(CardExpansionState())
        }
    var recoveryMessage by rememberSaveable(stageId.feedId.value) { mutableStateOf<String?>(null) }
    return remember(stageId, motion, globalReducedMotion) {
        AdaptiveStageCardDetailState(
            currentExpansion = { expansion },
            updateExpansion = { expansion = it },
            currentRecoveryMessage = { recoveryMessage },
            updateRecoveryMessage = { recoveryMessage = it },
            motion = motion,
            globalReducedMotion = globalReducedMotion,
        )
    }
}

private val FeedCardExpansionStateSaver =
    Saver<CardExpansionState, List<String>>(
        save = { state -> listOf(state.phase.name, state.cardId?.value.orEmpty()) },
        restore = { saved ->
            saved.getOrNull(0)
                ?.let { name -> runCatching { CardExpansionPhase.valueOf(name) }.getOrNull() }
                ?.let { phase ->
                    val cardId = saved.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::LauncherCardId)
                    runCatching { CardExpansionState(phase, cardId) }.getOrNull()
                }
        },
    )
