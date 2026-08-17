@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp
import com.riffle.core.domain.launcher.settings.MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("LongMethod", "LongParameterList")
internal fun GeneratedNotificationCardsPage(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    reducedMotion: Boolean,
    adaptiveStageAppearance: AdaptiveStageAppearanceSettings = AdaptiveStageAppearanceSettings.modern(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
    modifier: Modifier = Modifier,
) {
    val state = generatedNotificationCardsPageState(groups, notificationAccessStatus, apps)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (state) {
            is GeneratedNotificationCardsPageState.Content ->
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val showCardHeader = maxHeight >= MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP.dp
                    val controller = remember { CardStackController() }
                    val artworkCache =
                        remember {
                            AdaptiveStageArtworkCache<ImageBitmap>(decode = ::decodeAdaptiveStageArtwork)
                        }
                    val stackKey = remember { CardStackKey("generated-notification-cards") }
                    val cardIds = state.cards.map(::generatedNotificationCardId)
                    var focusedCardIdValue by rememberSaveable { mutableStateOf<String?>(null) }
                    var settleTransitionId by rememberSaveable { mutableIntStateOf(0) }
                    val reconciledFocusedCardId =
                        rememberReconciledFocusedCardId(
                            controller = controller,
                            stackKey = stackKey,
                            cardIds = cardIds,
                            focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                        ) { id -> focusedCardIdValue = id?.value }
                    val focusState = CardStackFocusState(stackKey, reconciledFocusedCardId)
                    val activeCardIndex = cardIds.indexOf(focusState.focusedCardId).takeIf { it >= 0 } ?: 0

                    fun applyFocus(result: CardStackFocusResult) {
                        if (result is CardStackFocusResult.Applied) {
                            focusedCardIdValue = result.state.focusedCardId?.value
                        }
                    }

                    // This stack's live scroll position, non-null for the whole continuous motion
                    // -- finger, momentum fling and the magnetize that ends it -- converted below
                    // into the fractional index the stack actually renders from. See CardStack's
                    // own CardStackScroll and CardStackInteraction.onLiveDrag docs.
                    var liveScrollPx by remember { mutableStateOf<Float?>(null) }
                    val liveActiveCardIndex =
                        cardStackLiveActiveCardIndex(
                            activeCardIndex = activeCardIndex,
                            cardCount = state.cards.size,
                            liveDragPx = liveScrollPx,
                            distancePerCardPx = GENERATED_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                        )
                    Column(
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Notification cards page" },
                    ) {
                        if (showCardHeader) {
                            GeneratedCardsHeading()
                            GeneratedCardStackControls(
                                focusedCardIndex = activeCardIndex,
                                cardCount = state.cards.size,
                                onPrevious = {
                                    applyFocus(
                                        controller.navigate(
                                            focusState,
                                            cardIds,
                                            CardStackNavigationDirection.PREVIOUS,
                                        ),
                                    )
                                },
                                onNext = {
                                    applyFocus(
                                        controller.navigate(
                                            focusState,
                                            cardIds,
                                            CardStackNavigationDirection.NEXT,
                                        ),
                                    )
                                },
                            )
                        }
                        BoxWithConstraints(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            val resolution =
                                adaptiveStageAppearance.resolveCardStack(
                                    viewport =
                                        AdaptiveStageViewportDp(
                                            widthDp = maxWidth.value.toInt(),
                                            heightDp = maxHeight.value.toInt(),
                                        ),
                                    capabilities = adaptiveStageRendererCapabilities(),
                                    globalReducedMotion = reducedMotion,
                                )
                            if (resolution.isUsable) {
                                CardStack(
                                    entries =
                                        resolution.layoutPolicy.entries(
                                            cardCount = state.cards.size,
                                            activeIndex = liveActiveCardIndex,
                                            reducedMotion = resolution.reducedMotion,
                                        ),
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .testTag(GENERATED_NOTIFICATION_CARD_STACK_TEST_TAG)
                                            .semantics {
                                                stateDescription =
                                                    generatedNotificationCardFocusDescription(
                                                        activeCardIndex,
                                                        state.cards.size,
                                                    )
                                            },
                                    animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
                                    animationSpec = resolution.animation,
                                    reducedMotion = resolution.reducedMotion,
                                    itemKey = { entry ->
                                        generatedNotificationCardKey(state.cards[entry.cardIndex].group)
                                    },
                                    interaction =
                                        CardStackInteraction(
                                            focusedItemKey =
                                                generatedNotificationCardKey(
                                                    state.cards[activeCardIndex].group,
                                                ),
                                            settleTransitionId = settleTransitionId,
                                            onFocusRequest = { entry ->
                                                applyFocus(
                                                    controller.jumpTo(
                                                        focusState,
                                                        cardIds,
                                                        cardIds[entry.cardIndex],
                                                    ),
                                                )
                                            },
                                            onSettle = { drag, velocity ->
                                                val result =
                                                    controller.settle(
                                                        focusState,
                                                        cardIds,
                                                        CardStackSettleRequest(
                                                            focusedCardId = focusState.focusedCardId,
                                                            verticalDragPx = drag,
                                                            verticalVelocityPxPerSecond = velocity,
                                                            distanceThresholdPx =
                                                            GENERATED_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                                            flingVelocityThresholdPxPerSecond = 1_000f,
                                                        ),
                                                    )
                                                if (
                                                    result is CardStackFocusResult.Applied &&
                                                    result.state.focusedCardId != focusState.focusedCardId
                                                ) {
                                                    settleTransitionId++
                                                }
                                                applyFocus(result)
                                            },
                                            onSettleHaptic = {
                                                haptics.adaptiveStageSettle(
                                                    adaptiveStageAppearance.motion.hapticStrength,
                                                )
                                            },
                                            onLiveDrag = { scrollPx -> liveScrollPx = scrollPx },
                                            // See CardStackScroll: the release velocity keeps
                                            // driving the position instead of the stack stopping
                                            // dead and animating to a freshly-picked card.
                                            scroll =
                                                CardStackScroll(
                                                    cardCount = state.cards.size,
                                                    activeCardIndex = activeCardIndex,
                                                    distancePerCardPx =
                                                    GENERATED_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                                    magnet = resolution.magnet,
                                                ),
                                        ),
                                ) { entry, pointerModifier ->
                                    GeneratedNotificationCard(
                                        card = state.cards[entry.cardIndex],
                                        onAction = onAction,
                                        isFocused = entry.cardIndex == activeCardIndex,
                                        appearance = adaptiveStageAppearance,
                                        artworkCache = artworkCache,
                                        appIconLoader = appIconLoader,
                                        cardWidth = resolution.cardWidthDp.dp,
                                        cardHeight = resolution.cardHeightDp.dp,
                                        contentPadding = generatedNotificationCardContentPadding(resolution),
                                        modifier = Modifier.fillMaxSize(),
                                        cardPointerModifier = pointerModifier,
                                    )
                                }
                            } else {
                                GeneratedNotificationCardsFallback(
                                    cards = state.cards,
                                    onAction = onAction,
                                    appearance = adaptiveStageAppearance,
                                    artworkCache = artworkCache,
                                    appIconLoader = appIconLoader,
                                )
                            }
                        }
                    }
                }

            is GeneratedNotificationCardsPageState.Message ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = state.title, style = MaterialTheme.typography.titleLarge)
                    Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                    if (notificationAccessStatus != NotificationAccessStatus.GRANTED) {
                        TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                            Text(text = "Allow notification access")
                        }
                    }
                }
        }
    }
}

@Composable
private fun GeneratedNotificationCardsFallback(
    cards: List<DockNotificationCardState>,
    onAction: (LauncherShellAction) -> Unit,
    appearance: AdaptiveStageAppearanceSettings,
    artworkCache: AdaptiveStageArtworkCache<ImageBitmap>,
    appIconLoader: AppIconLoader,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(GENERATED_NOTIFICATION_CARD_LIST_TEST_TAG),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { card -> generatedNotificationCardId(card).value }) { card ->
            GeneratedNotificationCardFallback(card, onAction, appearance, artworkCache, appIconLoader)
        }
    }
}

@Composable
private fun GeneratedNotificationCardFallback(
    card: DockNotificationCardState,
    onAction: (LauncherShellAction) -> Unit,
    appearance: AdaptiveStageAppearanceSettings,
    artworkCache: AdaptiveStageArtworkCache<ImageBitmap>,
    appIconLoader: AppIconLoader,
) {
    val label = dockNotificationCardLabel(card)
    val artwork =
        remember(card.group.notifications, artworkCache) {
            generatedNotificationArtwork(card, artworkCache)
        }
    val identity = card.app?.identity
    var appColor by remember(identity, appIconLoader) {
        mutableStateOf(identity?.let(appIconLoader::cachedColorFor))
    }
    LaunchedEffect(identity, appIconLoader) {
        appColor =
            identity?.let { appIdentity ->
                appIconLoader.cachedColorFor(appIdentity)
                    ?: withContext(Dispatchers.Default) { appIconLoader.colorFor(appIdentity) }
            }
    }
    AdaptiveStageCardSurface(
        appearance = appearance,
        background =
            AdaptiveStageCardBackground(
                artwork = artwork,
                appSeed = card.app?.identity?.packageName?.value ?: card.group.packageName.value,
                appColor = appColor,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = generatedNotificationCardContentDescription(card) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = dockNotificationCardSummary(card.group, canLaunchApp = card.app != null),
                style = MaterialTheme.typography.bodyMedium,
            )
            card.app?.identity?.let { identity ->
                TextButton(onClick = { onAction(LauncherShellAction.LaunchApp(identity)) }) {
                    Text(text = "Open app")
                }
            }
            card.clearAction?.let { action ->
                TextButton(
                    onClick = { onAction(action) },
                    modifier =
                        Modifier.semantics {
                            contentDescription = generatedNotificationCardClearContentDescription(card)
                        },
                ) {
                    Text(text = "Clear")
                }
            }
        }
    }
}

@Composable
private fun GeneratedCardsHeading() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text = "Cards", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Your current notifications", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GeneratedCardStackControls(
    focusedCardIndex: Int,
    cardCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (cardCount < 2) return

    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TextButton(
            onClick = onPrevious,
            modifier = Modifier.semantics { contentDescription = "Show previous card" },
        ) {
            Text(text = "Previous")
        }
        Text(
            text = generatedNotificationCardFocusDescription(focusedCardIndex, cardCount),
            modifier = Modifier.padding(top = 12.dp, start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(
            onClick = onNext,
            modifier = Modifier.semantics { contentDescription = "Show next card" },
        ) {
            Text(text = "Next")
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun GeneratedNotificationCard(
    card: DockNotificationCardState,
    onAction: (LauncherShellAction) -> Unit,
    isFocused: Boolean,
    appearance: AdaptiveStageAppearanceSettings,
    artworkCache: AdaptiveStageArtworkCache<ImageBitmap>,
    appIconLoader: AppIconLoader,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    contentPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    cardPointerModifier: Modifier = Modifier,
) {
    val label = dockNotificationCardLabel(card)
    val identity = card.app?.identity
    val artwork =
        remember(card.group.notifications, artworkCache) {
            generatedNotificationArtwork(card, artworkCache)
        }
    var appColor by remember(identity, appIconLoader) {
        mutableStateOf(identity?.let(appIconLoader::cachedColorFor))
    }
    LaunchedEffect(identity, appIconLoader) {
        appColor =
            identity?.let { appIdentity ->
                appIconLoader.cachedColorFor(appIdentity)
                    ?: withContext(Dispatchers.Default) { appIconLoader.colorFor(appIdentity) }
            }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AdaptiveStageCardSurface(
            appearance = appearance,
            background =
                AdaptiveStageCardBackground(
                    artwork = artwork,
                    appSeed = card.app?.identity?.packageName?.value ?: card.group.packageName.value,
                    appColor = appColor,
                ),
            modifier =
                Modifier
                    .requiredWidth(cardWidth)
                    .requiredHeight(cardHeight)
                    .then(cardPointerModifier)
                    .then(
                        Modifier
                            .semantics {
                                contentDescription = generatedNotificationCardContentDescription(card)
                            }.clickable(enabled = identity != null && isFocused) {
                                generatedNotificationCardLaunchAction(card)?.let(onAction)
                            },
                    ),
            contentPadding = contentPadding,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = dockNotificationCardSummary(card.group, canLaunchApp = card.app != null),
                    style = MaterialTheme.typography.bodyMedium,
                )
                card.clearAction?.takeIf { isFocused }?.let { action ->
                    TextButton(
                        onClick = { onAction(action) },
                        modifier =
                            Modifier.semantics {
                                contentDescription = generatedNotificationCardClearContentDescription(card)
                            },
                    ) {
                        Text(text = "Clear")
                    }
                }
            }
        }
    }
}

internal sealed interface GeneratedNotificationCardsPageState {
    data class Content(val cards: List<DockNotificationCardState>) : GeneratedNotificationCardsPageState

    data class Message(val title: String, val message: String) : GeneratedNotificationCardsPageState
}

internal fun generatedNotificationCardsPageState(
    groups: List<AppNotificationGroup>,
    notificationAccessStatus: NotificationAccessStatus,
    apps: List<InstalledApp>,
): GeneratedNotificationCardsPageState =
    when (notificationAccessStatus) {
        NotificationAccessStatus.GRANTED ->
            if (groups.isEmpty()) {
                GeneratedNotificationCardsPageState.Message("No notifications", "New notifications will appear here.")
            } else {
                GeneratedNotificationCardsPageState.Content(
                    groups.map { group ->
                        DockNotificationCardState(app = apps.firstOrNull { app -> app.matches(group) }, group = group)
                    },
                )
            }

        else ->
            GeneratedNotificationCardsPageState.Message(
                "Notification access needed",
                "Allow notification access to show your notification cards.",
            )
    }

internal fun generatedNotificationCardKey(group: AppNotificationGroup): AppNotificationGroupKey =
    AppNotificationGroupKey(packageName = group.packageName, profileId = group.profileId)

internal fun generatedNotificationCardId(card: DockNotificationCardState): LauncherCardId =
    LauncherCardId("${card.group.packageName.value}:${card.group.profileId.value}")

internal fun generatedNotificationCardClearContentDescription(card: DockNotificationCardState): String =
    dockNotificationClearContentDescription(
        label = dockNotificationCardLabel(card),
        clearableCount = card.group.clearableCount,
    )

internal fun generatedNotificationCardContentDescription(card: DockNotificationCardState): String =
    dockNotificationCardContentDescription(
        card = card,
        label = dockNotificationCardLabel(card),
    )

/** Resolves only the currently composed card's artwork and caches both valid and corrupt input. */
internal fun generatedNotificationArtwork(
    card: DockNotificationCardState,
    artworkCache: AdaptiveStageArtworkCache<ImageBitmap>,
    revisions: AdaptiveStageArtworkRevisionLookup = adaptiveStageArtworkRevisions,
): ImageBitmap? {
    val notification = card.group.notifications.maxByOrNull { item -> item.postedAtEpochMillis }
    val artwork = notification?.largeIconPngBase64
    val sourceKey = generatedNotificationArtworkSourceKey(card, revisions) ?: return null
    return artworkCache.getOrDecode(sourceKey, artwork)
}

/** Content-addressed revision prevents distinct untrusted payloads from sharing artwork cache entries. */
internal fun generatedNotificationArtworkSourceKey(
    card: DockNotificationCardState,
    revisions: AdaptiveStageArtworkRevisionLookup = adaptiveStageArtworkRevisions,
): String? {
    val notification = card.group.notifications.maxByOrNull { item -> item.postedAtEpochMillis }
    val revision = notification?.let(revisions::revisionFor) ?: return null
    return "${generatedNotificationCardId(card).value}:${notification.key.value}:$revision"
}

internal fun generatedNotificationCardStackEntries(
    cards: List<DockNotificationCardState>,
    focusedCardIndex: Int = 0,
): List<CardStackLayoutEntry> =
    CardStackLayoutPolicy().entries(
        cardCount = cards.size,
        activeIndex = focusedCardIndex,
    )

internal fun generatedNotificationCardFocusDescription(
    focusedCardIndex: Int,
    cardCount: Int,
): String = "Card ${focusedCardIndex + 1} of $cardCount"

internal fun generatedNotificationCardContentPadding(
    resolution: com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackResolution,
): androidx.compose.ui.unit.Dp = resolution.contentPaddingDp.dp

internal const val GENERATED_NOTIFICATION_CARD_STACK_TEST_TAG = "generated-notification-card-stack"
internal const val GENERATED_NOTIFICATION_CARD_LIST_TEST_TAG = "generated-notification-card-list"

/**
 * How far this stack's own axis travels per card -- both the distance a settle is measured against
 * and the distance the continuous scroll advances by, which have to agree so the magnetized
 * position a fling reports lands on an exact card boundary. See [CardStackScroll].
 */
private const val GENERATED_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX = 48f

internal fun generatedNotificationCardLaunchAction(card: DockNotificationCardState): LauncherShellAction.LaunchApp? =
    card.app?.identity?.let(LauncherShellAction::LaunchApp)
