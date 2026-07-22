@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AndroidNotificationStageActionGateway
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.AppStageShellStateReconciler
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.cards.TimeScapePaneLayoutPolicy
import com.riffle.core.domain.launcher.cards.TimeScapePaneMode
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.TimeScapeViewportDp
import com.riffle.core.domain.launcher.settings.resolveTimeScapeCardStack

/** The Cards home surface, compact by default and pane-adaptive for the current launcher window. */
@Composable
internal fun TimeScapeAppStageSurface(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    windowLayout: TimeScapeWindowLayout? = null,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets =
        TimeScapeSafeInsetsDp(
            start = with(density) { windowInsets.getLeft(this, layoutDirection).toDp().value.toInt() },
            top = with(density) { windowInsets.getTop(this).toDp().value.toInt() },
            end = with(density) { windowInsets.getRight(this, layoutDirection).toDp().value.toInt() },
            bottom = with(density) { windowInsets.getBottom(this).toDp().value.toInt() },
        )
    val reconciler = remember { AppStageShellStateReconciler(AndroidNotificationStageActionGateway) }
    val shellState = reconciler.reconcile(state)
    val selectedStage = shellState.snapshot.selectedStage
    var detailOrigin by remember { mutableStateOf<TimeScapeDetailOrigin?>(null) }
    var focusedCardIdValue by
        rememberSaveable(selectedStage?.id?.profileId?.value, selectedStage?.id?.packageName?.value) {
            mutableStateOf<String?>(null)
        }
    var detailRecoveryMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val detailState =
        selectedStage?.let { stage ->
            rememberTimeScapeCardDetailState(
                stageId = stage.id,
                motion = state.launcherSettings.cards.timeScapeAppearance.motion,
                globalReducedMotion = state.launcherSettings.motion.reducedMotion,
            )
        }

    LaunchedEffect(detailOrigin, selectedStage) {
        detailOrigin?.let { origin ->
            val isStillAvailable =
                selectedStage?.id == origin.stageId &&
                    (
                        selectedStage.content.any { it.id == origin.cardId } ||
                            origin.cardId == timeScapeEmptyDetailCardId(selectedStage.id)
                    )
            if (!isStillAvailable) {
                detailOrigin = null
                detailRecoveryMessage = "The selected card is no longer available."
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            val measuredWindow = TimeScapeWindowLayout(maxWidth.value.toInt(), maxHeight.value.toInt())
            // Window metrics arrive independently of the Cards-mode selection. Until Android has
            // reported a usable window, keep the recovery body in the measured Compose bounds
            // instead of laying out a zero-sized adaptive pane beneath the header.
            val adaptiveWindow =
                windowLayout
                    ?.insetLocal(safeInsets)
                    ?.takeIf(TimeScapeWindowLayout::hasUsableBounds)
                    ?: measuredWindow
            val paneLayout = remember(adaptiveWindow) { TimeScapePaneLayoutPolicy().layoutFor(adaptiveWindow) }
            Box(
                modifier =
                    Modifier.offset(y = paneLayout.contentTopDp.dp)
                        .offset(x = paneLayout.contentStartDp.dp)
                        .width(paneLayout.contentWidthDp.dp)
                        .height(paneLayout.contentHeightDp.dp),
            ) {
                if (paneLayout.mode == TimeScapePaneMode.COMPACT) {
                    TimeScapeCompactContent(
                        selectedStage = selectedStage,
                        state = state,
                        shellState = shellState,
                        detailRecoveryMessage = detailRecoveryMessage,
                        detailState = detailState,
                        focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                        onFocusedCardChanged = { focusedCardIdValue = it?.value },
                        onDetailVisibilityChanged = { cardId ->
                            detailOrigin =
                                selectedStage?.id?.let { stageId -> cardId?.let { TimeScapeDetailOrigin(stageId, it) } }
                            if (cardId != null) detailRecoveryMessage = null
                        },
                        onAction = onAction,
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        TimeScapeStageRail(
                            stages = shellState.snapshot.stages,
                            selectedStageId = selectedStage?.id,
                            state = state,
                            onAction = onAction,
                            modifier = Modifier.width(paneLayout.railWidthDp.dp),
                        )
                        Column(modifier = Modifier.width(paneLayout.splineWidthDp.dp).fillMaxSize()) {
                            TimeScapeStageHeader(selectedStage, state, onAction)
                            TimeScapeStageBody(
                                selectedStage = selectedStage,
                                state = state,
                                shellState = shellState,
                                detailRecoveryMessage = detailRecoveryMessage,
                                detailState = detailState,
                                focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                                onDetailVisibilityChanged = { cardId ->
                                    detailOrigin =
                                        selectedStage?.id?.let { stageId ->
                                            cardId?.let { TimeScapeDetailOrigin(stageId, it) }
                                        }
                                    if (cardId != null) detailRecoveryMessage = null
                                },
                                onFocusedCardChanged = { focusedCardIdValue = it?.value },
                                showDetailInline = !paneLayout.showsDetailPane,
                                onAction = onAction,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (paneLayout.leadingRemainderDp > 0) {
                            Spacer(modifier = Modifier.width(paneLayout.leadingRemainderDp.dp))
                        }
                        if (paneLayout.hingeGapDp > 0) Spacer(modifier = Modifier.width(paneLayout.hingeGapDp.dp))
                        if (paneLayout.showsDetailPane) {
                            TimeScapeSupportingPane(
                                stage = selectedStage,
                                selectedCardId = detailOrigin?.cardId ?: focusedCardIdValue?.let(::LauncherCardId),
                                state = state,
                                notificationCards = shellState.notificationCards,
                                emptyCard = selectedStage?.let { shellState.emptyAppCards[it.id] },
                                detailState = detailState,
                                onAction = onAction,
                                modifier = Modifier.width(paneLayout.detailWidthDp.dp).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeScapeCompactContent(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: TimeScapeCardDetailState?,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeScapeStageHeader(selectedStage, state, onAction)
        TimeScapeStageBody(
            selectedStage = selectedStage,
            state = state,
            shellState = shellState,
            detailRecoveryMessage = detailRecoveryMessage,
            detailState = detailState,
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        TimeScapeStageSelector(shellState.snapshot.stages, selectedStage?.id, state, onAction)
    }
}

@Composable
private fun TimeScapeStageBody(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: TimeScapeCardDetailState?,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    showDetailInline: Boolean = true,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    if (state.notificationAccessStatus != NotificationAccessStatus.GRANTED || selectedStage == null) {
        TimeScapeUnavailableState(state.notificationAccessStatus, detailRecoveryMessage, onAction, modifier)
    } else {
        TimeScapeStageContent(
            selectedStage,
            state,
            shellState,
            requireNotNull(detailState),
            focusedCardId,
            onDetailVisibilityChanged,
            onFocusedCardChanged,
            showDetailInline,
            onAction,
            modifier,
        )
    }
}

@Composable
private fun TimeScapeStageRail(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        Text("Stages", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onAction(LauncherShellAction.SelectPreviousAppStage) }) { Text("Previous") }
        stages.forEach { stage ->
            TextButton(onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) }) {
                val label = stageLabel(stage.id, state)
                Text(if (stage.id == selectedStageId) "$label •" else label)
            }
        }
        TextButton(onClick = { onAction(LauncherShellAction.SelectNextAppStage) }) { Text("Next") }
    }
}

@Composable
private fun TimeScapeSupportingPane(
    stage: AppStage?,
    selectedCardId: LauncherCardId?,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    emptyCard: AppStageEmptyAppCard?,
    detailState: TimeScapeCardDetailState?,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val card = notificationCards.firstOrNull { it.content.id == selectedCardId }
    val paneModifier = modifier.testTag(TIME_SCAPE_SUPPORTING_PANE_TEST_TAG)
    if (
        emptyCard != null &&
        selectedCardId == stage?.id?.let(::timeScapeEmptyDetailCardId) &&
        detailState?.expansionState?.isVisible == true
    ) {
        TimeScapeEmptyAppDetailSurface(emptyCard, detailState, onAction, modifier = paneModifier)
        return
    }
    if (card != null && detailState?.expansionState?.isVisible == true) {
        TimeScapeCardDetailSurface(card, detailState, onAction, modifier = paneModifier)
        return
    }
    Column(
        modifier =
            paneModifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Details", style = MaterialTheme.typography.titleMedium)
        if (card == null) {
            Text("Select a card to keep its context visible here.")
        } else {
            stage?.let { Text(stageLabel(it.id, state), style = MaterialTheme.typography.labelLarge) }
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text(card.text, style = MaterialTheme.typography.bodyMedium)
            TimeScapeContextShelf(
                card = card,
                onAction = onAction,
                onDetailRequested = { detailState?.expand(card.content.id) },
            )
        }
    }
}

@Composable
private fun TimeScapeStageHeader(
    selectedStage: AppStage?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label = selectedStage?.let { stageLabel(it.id, state) } ?: "TimeScape"
    var overflowExpanded by rememberSaveable(selectedStage?.let(::timeScapeStageSelectorItemKey)) {
        mutableStateOf(false)
    }
    val selectedApp =
        selectedStage?.let { stage ->
            state.installedApps.firstOrNull { app ->
                app.identity.packageName == stage.id.packageName &&
                    app.identity.profile.id == stage.id.profileId
            }
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Text(text = "TimeScape", style = MaterialTheme.typography.labelMedium)
        }
        if (selectedStage != null) {
            TextButton(onClick = { onAction(LauncherShellAction.ToggleAppStagePinned(selectedStage.id)) }) {
                Text(if (selectedStage.isPinned) "Unpin" else "Pin")
            }
            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "More stage options" },
                ) {
                    Text(text = "⋮")
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (selectedStage.isPinned) "Unpin stage" else "Pin stage") },
                        onClick = {
                            overflowExpanded = false
                            onAction(LauncherShellAction.ToggleAppStagePinned(selectedStage.id))
                        },
                    )
                    selectedApp?.let { app ->
                        DropdownMenuItem(
                            text = { Text("Open ${app.label}") },
                            onClick = {
                                overflowExpanded = false
                                onAction(LauncherShellAction.LaunchApp(app.identity))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("App info") },
                            onClick = {
                                overflowExpanded = false
                                onAction(LauncherShellAction.OpenAppInfo(app.identity))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeScapeStageContent(
    stage: AppStage,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailState: TimeScapeCardDetailState,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    showDetailInline: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val availableCardIds = stage.content.map { content -> content.id }.toSet()
    LaunchedEffect(detailState.expansionState) {
        onDetailVisibilityChanged(
            detailState.expansionState.cardId.takeIf { detailState.expansionState.isVisible },
        )
    }
    // Reconcile before selecting the empty-stage fallback so a removal during detail or drag
    // closes the transient presentation deterministically. Empty pinned stages reconcile their
    // synthetic detail card below, after that card has been projected.
    if (stage.content.isNotEmpty()) {
        LaunchedEffect(availableCardIds) { detailState.reconcile(availableCardIds) }
    }
    when {
        stage.content.isEmpty() ->
            TimeScapeEmptyStage(stage, shellState, detailState, showDetailInline, onAction, modifier)
        else ->
            TimeScapeNotificationStack(
                stage = stage,
                state = state,
                notificationCards = shellState.notificationCards,
                detailState = detailState,
                focusedCardId = focusedCardId,
                onFocusedCardChanged = onFocusedCardChanged,
                showDetailInline = showDetailInline,
                onAction = onAction,
                modifier = modifier,
            )
    }
}

@Composable
private fun TimeScapeNotificationStack(
    stage: AppStage,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    detailState: TimeScapeCardDetailState,
    focusedCardId: LauncherCardId?,
    onFocusedCardChanged: (LauncherCardId?) -> Unit,
    showDetailInline: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)
    val cards =
        remember(stage.content, notificationCards) {
            val cardsById = notificationCards.associateBy { card -> card.content.id }
            stage.content.mapNotNull { content -> cardsById[content.id] }
        }
    val cardIds = cards.map { card -> card.content.id }
    val controller = remember(stage.id) { CardStackController() }
    val artworkCache =
        remember(stage.id) {
            TimeScapeArtworkCache<ImageBitmap>(decode = ::decodeTimeScapeArtwork)
        }
    val stackKey =
        remember(stage.id) {
            CardStackKey("timescape:${stage.id.profileId.value}:${stage.id.packageName.value}")
        }
    var previousCardIds by remember(stage.id) { mutableStateOf(emptyList<LauncherCardId>()) }
    var settleTransitionId by rememberSaveable(stage.id.profileId.value, stage.id.packageName.value) {
        mutableIntStateOf(0)
    }
    val focusState = CardStackFocusState(stackKey, focusedCardId)
    LaunchedEffect(cardIds) {
        val reconciliation =
            if (focusState.focusedCardId == null) {
                controller.restore(focusState, cardIds)
            } else {
                controller.reconcile(focusState, previousCardIds, cardIds)
            }
        if (reconciliation is CardStackFocusResult.Applied) {
            onFocusedCardChanged(reconciliation.state.focusedCardId)
        }
        previousCardIds = cardIds
    }
    val activeCardIndex = cardIds.indexOf(focusState.focusedCardId).takeIf { index -> index >= 0 } ?: 0
    val focusedCard = cards.getOrNull(activeCardIndex)
    val activeCard = focusedCard ?: return
    val detailFocusRequester = remember { FocusRequester() }
    var restoreDetailFocusForCardId by remember { mutableStateOf<LauncherCardId?>(null) }

    LaunchedEffect(activeCard.content.id) {
        onFocusedCardChanged(activeCard.content.id)
    }
    LaunchedEffect(cardIds) {
        if (restoreDetailFocusForCardId !in cardIds) restoreDetailFocusForCardId = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = TimeScapeViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveTimeScapeCardStack(
                    viewport = viewport,
                    capabilities = timeScapeRendererCapabilities(),
                )
            }
        if (detailState.expansionState.isVisible && showDetailInline) {
            cards
                .firstOrNull { card -> card.content.id == detailState.expansionState.cardId }
                ?.let { card ->
                    TimeScapeCardDetailSurface(
                        card = card,
                        detailState = detailState,
                        onAction = onAction,
                        onClose = { restoreDetailFocusForCardId = card.content.id },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CardStack(
                        entries =
                            resolution.layoutPolicy.entries(
                                cards.size,
                                activeCardIndex,
                                resolution.reducedMotion,
                            ),
                        animationSpec = resolution.animation,
                        reducedMotion = resolution.reducedMotion,
                        itemKey = { entry -> cards[entry.cardIndex].content.id },
                        interaction =
                            CardStackInteraction(
                                focusedItemKey = activeCard.content.id,
                                settleTransitionId = settleTransitionId,
                                onFocusRequest = { entry ->
                                    controller
                                        .jumpTo(focusState, cardIds, cardIds[entry.cardIndex])
                                        .let { result ->
                                            if (result is CardStackFocusResult.Applied) {
                                                onFocusedCardChanged(result.state.focusedCardId)
                                            }
                                        }
                                },
                                onSettle = { drag, velocity ->
                                    controller
                                        .settle(
                                            focusState,
                                            cardIds,
                                            CardStackSettleRequest(
                                                focusedCardId = activeCard.content.id,
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
                                                onFocusedCardChanged(result.state.focusedCardId)
                                            }
                                        }
                                },
                                onSettleHaptic = {
                                    haptics.timeScapeSettle(
                                        state.launcherSettings.cards.timeScapeAppearance.motion.hapticStrength,
                                    )
                                },
                            ),
                    ) { entry, cardModifier ->
                        val card = cards[entry.cardIndex]
                        val artwork =
                            remember(card.artworkSourceKey, card.artworkBase64, artworkCache) {
                                card.artworkSourceKey?.let { sourceKey ->
                                    artworkCache.getOrDecode(sourceKey, card.artworkBase64)
                                }
                            }
                        TimeScapeCardSurface(
                            appearance = state.launcherSettings.cards.timeScapeAppearance,
                            background =
                                TimeScapeCardBackground(
                                    artwork = artwork,
                                    appSeed = stage.id.packageName.value,
                                ),
                            modifier =
                                cardModifier.size(
                                    width = resolution.cardWidthDp.dp,
                                    height = resolution.cardHeightDp.dp,
                                ),
                            contentPadding = timeScapeResolvedContentPadding(resolution),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, style = MaterialTheme.typography.titleMedium)
                                Text(card.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                TimeScapeContextShelf(
                    card = activeCard,
                    onAction = onAction,
                    onDetailRequested = { detailState.expand(activeCard.content.id) },
                    detailFocusRequester = detailFocusRequester,
                    restoreDetailFocus = restoreDetailFocusForCardId == activeCard.content.id,
                    onDetailFocusRestored = { restoreDetailFocusForCardId = null },
                )
                TimeScapeDetailRecoveryMessage(detailState.sourceRemovalMessage)
            }
        }
    }
}

@Composable
internal fun TimeScapeContextShelf(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
    onDetailRequested: (() -> Unit)? = null,
    detailFocusRequester: FocusRequester? = null,
    restoreDetailFocus: Boolean = false,
    onDetailFocusRestored: (() -> Unit)? = null,
) {
    if (card.supportedActions.isEmpty() && onDetailRequested == null) return
    var detailControlLaidOut by remember { mutableStateOf(false) }
    RestoreFocusAfterLayout(
        enabled = restoreDetailFocus,
        focusRequester = detailFocusRequester,
        isLaidOut = detailControlLaidOut,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        card.supportedActions.sortedBy { action -> action.label() }.forEach { action ->
            TextButton(
                onClick = {
                    onAction(LauncherShellAction.PerformNotificationStageAction(card.notificationKey, action))
                },
            ) {
                Text(action.label())
            }
        }
        onDetailRequested?.let { requestDetail ->
            TextButton(
                onClick = requestDetail,
                modifier =
                    detailFocusRequester?.let { requester ->
                        Modifier.focusRequester(requester).onGloballyPositioned {
                            if (restoreDetailFocus) detailControlLaidOut = true
                        }
                            .onFocusChanged { focusState ->
                                if (restoreDetailFocus && focusState.isFocused) {
                                    onDetailFocusRestored?.invoke()
                                    detailControlLaidOut = false
                                }
                            }.focusable()
                    }
                        ?: Modifier,
            ) {
                Text("Details")
            }
        }
    }
}

@Composable
private fun TimeScapeEmptyStage(
    stage: AppStage,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailState: TimeScapeCardDetailState,
    showDetailInline: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val emptyCard = shellState.emptyAppCards[stage.id]
    val detailCardId = timeScapeEmptyDetailCardId(stage.id)
    val availableCardIds = if (emptyCard == null) emptySet() else setOf(detailCardId)
    val detailFocusRequester = remember { FocusRequester() }
    var restoreDetailFocusForCardId by remember { mutableStateOf<LauncherCardId?>(null) }
    var detailControlLaidOut by remember { mutableStateOf(false) }
    LaunchedEffect(availableCardIds) {
        detailState.reconcile(availableCardIds)
        if (restoreDetailFocusForCardId !in availableCardIds) restoreDetailFocusForCardId = null
    }
    if (detailState.expansionState.isVisible && showDetailInline && emptyCard != null) {
        TimeScapeEmptyAppDetailSurface(
            card = emptyCard,
            detailState = detailState,
            onAction = onAction,
            onClose = { restoreDetailFocusForCardId = detailCardId },
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (stage.lifecycle.name == "PROFILE_LOCKED") "Profile unavailable" else "Nothing new",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("This stage stays available so you can return to it.", style = MaterialTheme.typography.bodyMedium)
        emptyCard?.let { card ->
            TextButton(onClick = { onAction(LauncherShellAction.LaunchApp(card.app.identity)) }) {
                Text("Open ${card.app.label}")
            }
            card.shortcuts.forEach { shortcut ->
                TextButton(onClick = { onAction(LauncherShellAction.LaunchAppShortcut(shortcut)) }) {
                    Text(shortcut.shortLabel)
                }
            }
            TextButton(
                onClick = { detailState.expand(detailCardId) },
                modifier =
                    Modifier.focusRequester(detailFocusRequester).onGloballyPositioned {
                        if (restoreDetailFocusForCardId == detailCardId) detailControlLaidOut = true
                    }
                        .onFocusChanged { focusState ->
                            if (restoreDetailFocusForCardId == detailCardId && focusState.isFocused) {
                                restoreDetailFocusForCardId = null
                                detailControlLaidOut = false
                            }
                        }.focusable(),
            ) {
                Text("Details")
            }
            RestoreFocusAfterLayout(
                enabled = restoreDetailFocusForCardId == detailCardId,
                focusRequester = detailFocusRequester,
                isLaidOut = detailControlLaidOut,
            )
        }
        TimeScapeDetailRecoveryMessage(detailState.sourceRemovalMessage)
    }
}

@Composable
private fun RestoreFocusAfterLayout(
    enabled: Boolean,
    focusRequester: FocusRequester?,
    isLaidOut: Boolean,
) {
    LaunchedEffect(enabled, focusRequester, isLaidOut) {
        if (!enabled || focusRequester == null || !isLaidOut) return@LaunchedEffect
        withFrameNanos { }
        focusRequester.requestFocus()
    }
}

@Composable
private fun TimeScapeUnavailableState(
    access: NotificationAccessStatus,
    recoveryMessage: String?,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val message =
        when (access) {
            NotificationAccessStatus.GRANTED -> "No active stages yet. New notifications will appear here."
            NotificationAccessStatus.NOT_GRANTED -> "Allow notification access to show your app stages."
            NotificationAccessStatus.REVOKED -> "Notification access was revoked. Restore access to update stages."
            NotificationAccessStatus.UNKNOWN -> "Checking notification access."
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        TimeScapeDetailRecoveryMessage(recoveryMessage)
        if (access == NotificationAccessStatus.NOT_GRANTED || access == NotificationAccessStatus.REVOKED) {
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                Text("Allow access")
            }
        }
    }
}

private data class TimeScapeDetailOrigin(
    val stageId: AppStageId,
    val cardId: LauncherCardId,
)

private fun timeScapeEmptyDetailCardId(stageId: AppStageId): LauncherCardId =
    LauncherCardId("stage-empty:${stageId.profileId.value}:${stageId.packageName.value}")

internal const val TIME_SCAPE_SUPPORTING_PANE_TEST_TAG = "timescape-supporting-pane"

private data class TimeScapeSafeInsetsDp(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
)

/** Converts full-window hinge coordinates into the inset content coordinates used by the surface. */
private fun TimeScapeWindowLayout.insetLocal(insets: TimeScapeSafeInsetsDp): TimeScapeWindowLayout =
    copy(
        widthDp = (widthDp - insets.start - insets.end).coerceAtLeast(0),
        heightDp = (heightDp - insets.top - insets.bottom).coerceAtLeast(0),
        safeStartDp = 0,
        safeTopDp = 0,
        safeEndDp = 0,
        safeBottomDp = 0,
        separatingHinges =
            separatingHinges.map { hinge ->
                hinge.copy(
                    leftDp = hinge.leftDp - insets.start,
                    topDp = hinge.topDp - insets.top,
                    rightDp = hinge.rightDp - insets.start,
                    bottomDp = hinge.bottomDp - insets.top,
                )
            },
    )

private fun TimeScapeWindowLayout.hasUsableBounds(): Boolean = widthDp > 0 && heightDp > 0

@Composable
private fun TimeScapeStageSelector(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (stages.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onAction(LauncherShellAction.SelectPreviousAppStage) }) { Text("Previous") }
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stages, key = ::timeScapeStageSelectorItemKey) { stage ->
                TextButton(
                    onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "${stageLabel(stage.id, state)}" +
                                if (stage.id == selectedStageId) {
                                    ", selected. Open stage"
                                } else {
                                    ". Open stage"
                                }
                        },
                ) { Text(stageLabel(stage.id, state)) }
            }
        }
        TextButton(onClick = { onAction(LauncherShellAction.SelectNextAppStage) }) { Text("Next") }
    }
}

/** Lazy layouts require item keys that Android can store in a Bundle across recreation. */
internal fun timeScapeStageSelectorItemKey(stage: AppStage): String {
    return "${stage.id.profileId.value}:${stage.id.packageName.value}"
}

private fun stageLabel(
    id: AppStageId,
    state: LauncherShellState,
): String =
    state.installedApps.firstOrNull { app ->
        app.identity.packageName == id.packageName && app.identity.profile.id == id.profileId
    }?.let { app ->
        app.identity.profile.profileDisplayLabel(app.label)
    } ?: "${id.packageName.value} (${id.profileId.value})"

private fun NotificationStageAction.label(): String =
    when (this) {
        NotificationStageAction.Open -> "Open"
        NotificationStageAction.Dismiss -> "Dismiss"
        is NotificationStageAction.MediaControl ->
            command.name.lowercase().replaceFirstChar { character -> character.titlecase() }
        is NotificationStageAction.ProviderAction -> "Action"
    }
