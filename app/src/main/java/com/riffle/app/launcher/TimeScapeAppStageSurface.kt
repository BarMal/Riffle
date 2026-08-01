@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AndroidNotificationStageActionGateway
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.AppStageShellStateReconciler
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.CardStackController
import com.riffle.core.domain.launcher.cards.CardStackFocusResult
import com.riffle.core.domain.launcher.cards.CardStackFocusState
import com.riffle.core.domain.launcher.cards.CardStackKey
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import com.riffle.core.domain.launcher.cards.CardStackSettleRequest
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.cards.TimeScapeDynamicSlot
import com.riffle.core.domain.launcher.cards.TimeScapeInteractionContext
import com.riffle.core.domain.launcher.cards.TimeScapePaneArrangement
import com.riffle.core.domain.launcher.cards.TimeScapePaneLayout
import com.riffle.core.domain.launcher.cards.TimeScapePaneLayoutPolicy
import com.riffle.core.domain.launcher.cards.TimeScapePaneMode
import com.riffle.core.domain.launcher.cards.TimeScapePosture
import com.riffle.core.domain.launcher.cards.TimeScapePostureTransitionState
import com.riffle.core.domain.launcher.cards.TimeScapeRailSide
import com.riffle.core.domain.launcher.cards.TimeScapeStaticElement
import com.riffle.core.domain.launcher.cards.TimeScapeStaticElementType
import com.riffle.core.domain.launcher.cards.TimeScapeTemplateCatalogDefaults
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.cards.variantFor
import com.riffle.core.domain.launcher.cards.visibleStaticElements
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeCardStackResolution
import com.riffle.core.domain.launcher.settings.TimeScapeViewportDp
import com.riffle.core.domain.launcher.settings.resolveTimeScapeCardStack
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Cards mode reuses the persisted home-gesture bindings, but only lets a subset of actions
 * through: stage navigation, exiting back to Standard Home, and reaching the app drawer/search so
 * Cards mode stays a normal, discoverable overlay rather than an isolated static surface.
 */
internal fun timeScapeAppStageActionFilter(action: LauncherShellAction): Boolean =
    when (action) {
        LauncherShellAction.SelectNextAppStage,
        LauncherShellAction.SelectPreviousAppStage,
        LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
        LauncherShellAction.OpenAppDrawer,
        LauncherShellAction.OpenSearch,
        -> true

        else -> false
    }

@Suppress("UNUSED_PARAMETER")
internal fun resolveTimeScapeRailSide(
    configuredRailSide: TimeScapeRailSide,
    templateRailSide: TimeScapeRailSide?,
): TimeScapeRailSide = configuredRailSide

/**
 * Mirrors [resolveTimeScapeRailSide]'s shape: the pane arrangement is a plain configured user
 * preference today, with no template or device override to reconcile against yet.
 */
internal fun resolveTimeScapePaneArrangement(value: TimeScapePaneArrangement): TimeScapePaneArrangement = value

/** The Cards home surface, compact by default and pane-adaptive for the current launcher window. */
@Composable
internal fun TimeScapeAppStageSurface(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    windowLayout: TimeScapeWindowLayout? = null,
    context: TimeScapeInteractionContext = TimeScapeInteractionContext(),
    onContextChanged: (TimeScapeInteractionContext) -> Unit = {},
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
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
    var detailCardKey by rememberSaveable { mutableStateOf(context.detailCardKey) }
    var detailStageKey by rememberSaveable {
        mutableStateOf(context.selectedStageKey.takeIf { context.detailCardKey != null })
    }
    var focusedCardIdValue by
        rememberSaveable(selectedStage?.id?.profileId?.value, selectedStage?.id?.packageName?.value) {
            mutableStateOf(context.focusedCardKey)
        }
    var detailRecoveryMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val stageRailScrollState = rememberScrollState(context.scrollOffsetPx.coerceAtLeast(0))
    val latestContext by rememberUpdatedState(context)
    val latestContextChanged by rememberUpdatedState(onContextChanged)
    LaunchedEffect(stageRailScrollState) {
        snapshotFlow { stageRailScrollState.value }
            .distinctUntilChanged()
            .collect { offset ->
                if (offset != latestContext.scrollOffsetPx) {
                    latestContextChanged(latestContext.copy(scrollOffsetPx = offset))
                }
            }
    }
    val detailState =
        selectedStage?.let { stage ->
            rememberTimeScapeCardDetailState(
                stageId = stage.id,
                motion = state.launcherSettings.cards.timeScapeAppearance.motion,
                globalReducedMotion = state.launcherSettings.motion.reducedMotion,
            )
        }

    LaunchedEffect(selectedStage?.id, state.launcherSettings.cards.timeScapeTemplateId) {
        onContextChanged(
            context.copy(
                selectedStageKey = selectedStage?.id?.let(::timeScapeStageKey),
                templateId = state.launcherSettings.cards.timeScapeTemplateId.value,
            ),
        )
    }

    LaunchedEffect(shellState.snapshot.stages, shellState.notificationCards, shellState.emptyAppCards) {
        val availableStageKeys = shellState.snapshot.stages.map { stage -> timeScapeStageKey(stage.id) }.toSet()
        val availableCardKeys =
            shellState.notificationCards.map { card -> card.content.id.value }.toSet() +
                shellState.emptyAppCards.keys.map { stageId -> timeScapeEmptyDetailCardId(stageId).value }
        val reconciled = context.reconcile(availableStageKeys, availableCardKeys)
        if (reconciled != context) onContextChanged(reconciled)
    }

    val detailOrigin =
        detailCardKey?.let { cardKey ->
            TimeScapeDetailOrigin(detailStageKey, LauncherCardId(cardKey))
        }
    LaunchedEffect(context.selectedStageKey, shellState.snapshot.stages) {
        shellState.snapshot.stages
            .firstOrNull { stage -> timeScapeStageKey(stage.id) == context.selectedStageKey }
            ?.takeIf { stage -> stage.id != selectedStage?.id }
            ?.let { stage -> onAction(LauncherShellAction.SelectAppStage(stage.id)) }
    }
    LaunchedEffect(detailOrigin, selectedStage) {
        detailOrigin?.let { origin ->
            val isStillAvailable =
                selectedStage?.let { stage ->
                    timeScapeStageKey(stage.id) == origin.stageKey &&
                        (
                            stage.content.any { it.id == origin.cardId } ||
                                origin.cardId == timeScapeEmptyDetailCardId(stage.id)
                        )
                } == true
            if (!isStillAvailable) {
                detailCardKey = null
                detailStageKey = null
                onContextChanged(context.copy(detailCardKey = null))
                detailRecoveryMessage = "The selected card is no longer available."
            }
        }
    }

    Surface(
        modifier =
            modifier
                .fillMaxSize()
                // Reuse the persisted gesture bindings, but only claim mode exit and stage
                // navigation here. Focused cards consume their one-finger vertical drags first.
                .homeGestureInput(
                    enabled = detailOrigin == null,
                    settings = state.launcherSettings.gestures.homeGestures,
                    onAction = onAction,
                    actionFilter = ::timeScapeAppStageActionFilter,
                ),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
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
            var postureTransition by rememberSaveable(stateSaver = TimeScapePostureTransitionStateSaver) {
                mutableStateOf(TimeScapePostureTransitionState())
            }
            LaunchedEffect(adaptiveWindow.posture) {
                postureTransition = postureTransition.transitionTo(adaptiveWindow.posture)
                withFrameNanos { }
                postureTransition = postureTransition.settle()
            }
            val initialPaneLayout =
                remember(adaptiveWindow, postureTransition.effectivePosture) {
                    TimeScapePaneLayoutPolicy().layoutFor(
                        adaptiveWindow.copy(posture = postureTransition.effectivePosture),
                    )
                }
            val templateVariant =
                remember(
                    state.launcherSettings.cards.timeScapeTemplateId,
                    state.settingsLayoutDeviceClass,
                    initialPaneLayout.mode,
                ) {
                    TimeScapeTemplateCatalogDefaults.templates
                        .firstOrNull { template -> template.id == state.launcherSettings.cards.timeScapeTemplateId }
                        ?.variantFor(state.settingsLayoutDeviceClass, initialPaneLayout.mode)
                }
            val railSide =
                resolveTimeScapeRailSide(
                    configuredRailSide = state.launcherSettings.cards.timeScapeRailSide,
                    templateRailSide = templateVariant?.railSide,
                )
            val paneArrangement =
                resolveTimeScapePaneArrangement(value = state.launcherSettings.cards.timeScapePaneArrangement)
            val paneLayout =
                remember(adaptiveWindow, postureTransition.effectivePosture, railSide, paneArrangement) {
                    TimeScapePaneLayoutPolicy().layoutFor(
                        window = adaptiveWindow.copy(posture = postureTransition.effectivePosture),
                        railSide = railSide,
                        arrangement = paneArrangement,
                    )
                }
            val visibleTemplateElements = templateVariant?.visibleStaticElements().orEmpty()
            Box(
                modifier =
                    Modifier.offset(y = paneLayout.contentTopDp.dp)
                        .offset(x = paneLayout.contentStartDp.dp)
                        .width(paneLayout.contentWidthDp.dp)
                        .height(paneLayout.contentHeightDp.dp),
            ) {
                TimeScapeTemplateStaticCanvas(
                    elements = visibleTemplateElements,
                    dynamicSlots = templateVariant?.dynamicSlots.orEmpty(),
                    canvasWidthDp = paneLayout.contentWidthDp,
                    canvasHeightDp = paneLayout.contentHeightDp,
                    gridColumns = templateVariant?.canvas?.grid?.columns ?: 1,
                    gridRows = templateVariant?.canvas?.grid?.rows ?: 1,
                    leadingPaneWidthDp = paneLayout.leadingRegionWidthDp,
                    hingeGapDp = paneLayout.hingeGapDp,
                    trailingPaneWidthDp = paneLayout.trailingRegionWidthDp,
                )
                when (paneLayout.mode) {
                    TimeScapePaneMode.COMPACT ->
                        TimeScapeCompactContent(
                            selectedStage = selectedStage,
                            state = state,
                            shellState = shellState,
                            detailRecoveryMessage = detailRecoveryMessage,
                            detailState = detailState,
                            focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                            onFocusedCardChanged = {
                                focusedCardIdValue = it?.value
                                onContextChanged(context.copy(focusedCardKey = it?.value))
                            },
                            onDetailVisibilityChanged = { cardId ->
                                detailCardKey = cardId?.value
                                detailStageKey = cardId?.let { selectedStage?.id?.let(::timeScapeStageKey) }
                                onContextChanged(context.copy(detailCardKey = cardId?.value))
                                if (cardId != null) detailRecoveryMessage = null
                            },
                            onAction = onAction,
                            appIconLoader = appIconLoader,
                        )

                    TimeScapePaneMode.SPLIT ->
                        TimeScapeSplitContent(
                            selectedStage = selectedStage,
                            state = state,
                            shellState = shellState,
                            detailRecoveryMessage = detailRecoveryMessage,
                            detailState = detailState,
                            focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                            selectedDetailCardId = detailOrigin?.cardId ?: focusedCardIdValue?.let(::LauncherCardId),
                            paneLayout = paneLayout,
                            onFocusedCardChanged = {
                                focusedCardIdValue = it?.value
                                onContextChanged(context.copy(focusedCardKey = it?.value))
                            },
                            onDetailVisibilityChanged = { cardId ->
                                detailCardKey = cardId?.value
                                detailStageKey = cardId?.let { selectedStage?.id?.let(::timeScapeStageKey) }
                                onContextChanged(context.copy(detailCardKey = cardId?.value))
                                if (cardId != null) detailRecoveryMessage = null
                            },
                            onAction = onAction,
                            appIconLoader = appIconLoader,
                        )

                    TimeScapePaneMode.TWO_PANE, TimeScapePaneMode.THREE_PANE ->
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (railSide == TimeScapeRailSide.LEADING) {
                                TimeScapeStageRail(
                                    stages = shellState.snapshot.stages,
                                    selectedStageId = selectedStage?.id,
                                    state = state,
                                    appIconLoader = appIconLoader,
                                    onAction = onAction,
                                    scrollState = stageRailScrollState,
                                    modifier = Modifier.width(paneLayout.railWidthDp.dp),
                                )
                            }
                            Column(modifier = Modifier.width(paneLayout.splineWidthDp.dp).fillMaxSize()) {
                                TimeScapeStageHeader(
                                    selectedStage = selectedStage,
                                    stages = shellState.snapshot.stages,
                                    state = state,
                                    appIconLoader = appIconLoader,
                                    onAction = onAction,
                                )
                                TimeScapeStageBody(
                                    selectedStage = selectedStage,
                                    state = state,
                                    shellState = shellState,
                                    detailRecoveryMessage = detailRecoveryMessage,
                                    detailState = detailState,
                                    focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                                    onDetailVisibilityChanged = { cardId ->
                                        detailCardKey = cardId?.value
                                        detailStageKey = cardId?.let { selectedStage?.id?.let(::timeScapeStageKey) }
                                        onContextChanged(context.copy(detailCardKey = cardId?.value))
                                        if (cardId != null) detailRecoveryMessage = null
                                    },
                                    onFocusedCardChanged = {
                                        focusedCardIdValue = it?.value
                                        onContextChanged(context.copy(focusedCardKey = it?.value))
                                    },
                                    showDetailInline = !paneLayout.showsDetailPane,
                                    onAction = onAction,
                                    appIconLoader = appIconLoader,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (paneLayout.leadingRemainderDp > 0) {
                                Spacer(modifier = Modifier.width(paneLayout.leadingRemainderDp.dp))
                            }
                            if (paneLayout.hingeGapDp > 0) {
                                Spacer(modifier = Modifier.width(paneLayout.hingeGapDp.dp))
                            }
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
                            if (railSide == TimeScapeRailSide.TRAILING) {
                                TimeScapeStageRail(
                                    stages = shellState.snapshot.stages,
                                    selectedStageId = selectedStage?.id,
                                    state = state,
                                    appIconLoader = appIconLoader,
                                    onAction = onAction,
                                    scrollState = stageRailScrollState,
                                    modifier = Modifier.width(paneLayout.railWidthDp.dp),
                                )
                            }
                        }
                }
            }
        }
    }
}

/**
 * Compact (single-pane phone) content. Unlike the two-pane/rail layout, there's no persistent
 * rail to tap here, so this is where the continuous horizontal drag pager and its synced spine
 * carousel live. The wider-screen layout keeps its existing rail-based navigation unchanged; the
 * rail already gives equivalent discoverability there, and mirroring the graphicsLayer-offset
 * pager approach for a rail+content split isn't a trivial extension, so the drag pager stays
 * compact-only for now.
 */
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
    appIconLoader: AppIconLoader,
) {
    val stages = shellState.snapshot.stages
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val pagerState =
        rememberTimeScapeStagePagerState(
            stages = stages,
            selectedStageId = selectedStage?.id,
            reducedMotion = reducedMotion,
            onAction = onAction,
        )
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeScapeStageHeader(
            selectedStage = selectedStage,
            stages = stages,
            state = state,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
        TimeScapeCompactStagePager(
            stages = stages,
            selectedStage = selectedStage,
            pagerState = pagerState,
            state = state,
            shellState = shellState,
            detailRecoveryMessage = detailRecoveryMessage,
            detailState = detailState,
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = Modifier.weight(1f),
        )
        TimeScapeStageSpine(
            stages = stages,
            selectedStageId = selectedStage?.id,
            pagePosition = pagerState.pagePosition,
            state = state,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
    }
}

/**
 * The Phase 3 split arrangement: a header, then an upper focus/detail region (reusing
 * [TimeScapeSupportingPane] verbatim -- it already renders the expanded card/empty-app detail
 * surface, or a summary, without any phone-vs-wide gating inside it) sized to
 * [TimeScapePaneLayout.upperRegionHeightDp], over a lower region sized to
 * [TimeScapePaneLayout.lowerRegionHeightDp] hosting the exact same drag-based
 * [TimeScapeCompactStagePager] and synced [TimeScapeStageSpine] that [TimeScapeCompactContent]
 * uses -- same drag gesture, same settle behavior, same tap-to-select -- just constrained to the
 * remaining height instead of filling the whole column.
 */
@Composable
@Suppress("LongParameterList")
private fun TimeScapeSplitContent(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: TimeScapeCardDetailState?,
    focusedCardId: LauncherCardId?,
    selectedDetailCardId: LauncherCardId?,
    paneLayout: TimeScapePaneLayout,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
) {
    val stages = shellState.snapshot.stages
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val pagerState =
        rememberTimeScapeStagePagerState(
            stages = stages,
            selectedStageId = selectedStage?.id,
            reducedMotion = reducedMotion,
            onAction = onAction,
        )
    Column(modifier = Modifier.fillMaxSize()) {
        TimeScapeStageHeader(
            selectedStage = selectedStage,
            stages = stages,
            state = state,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
        TimeScapeSupportingPane(
            stage = selectedStage,
            selectedCardId = selectedDetailCardId,
            state = state,
            notificationCards = shellState.notificationCards,
            emptyCard = selectedStage?.let { shellState.emptyAppCards[it.id] },
            detailState = detailState,
            onAction = onAction,
            modifier = Modifier.fillMaxWidth().height(paneLayout.upperRegionHeightDp.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().height(paneLayout.lowerRegionHeightDp.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeScapeCompactStagePager(
                stages = stages,
                selectedStage = selectedStage,
                pagerState = pagerState,
                state = state,
                shellState = shellState,
                detailRecoveryMessage = detailRecoveryMessage,
                detailState = detailState,
                focusedCardId = focusedCardId,
                onDetailVisibilityChanged = onDetailVisibilityChanged,
                onFocusedCardChanged = onFocusedCardChanged,
                onAction = onAction,
                appIconLoader = appIconLoader,
                modifier = Modifier.weight(1f),
                // The upper TimeScapeSupportingPane already renders the expanded card's detail;
                // showing it inline here too would duplicate it.
                showDetailInline = false,
            )
            TimeScapeStageSpine(
                stages = stages,
                selectedStageId = selectedStage?.id,
                pagePosition = pagerState.pagePosition,
                state = state,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )
        }
    }
}

/**
 * Lays out every stage's content side by side, offsetting each via `graphicsLayer` translation
 * driven by [TimeScapeStagePagerState.pagePosition] -- mirroring [ImmediateWorkspacePager]'s
 * approach for Standard Home's own pages -- and attaches [timeScapeStagePagerDrag] to claim
 * horizontal drags. With zero or one stage there is nothing to page between, so this falls back to
 * rendering [TimeScapeStageBody] directly without a drag gesture.
 */
@Composable
@Suppress("LongParameterList")
private fun TimeScapeCompactStagePager(
    stages: List<AppStage>,
    selectedStage: AppStage?,
    pagerState: TimeScapeStagePagerState,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: TimeScapeCardDetailState?,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
    // false in SPLIT mode, where TimeScapeSplitContent already renders the expanded card's detail
    // in its upper TimeScapeSupportingPane -- rendering it inline here too would duplicate it.
    showDetailInline: Boolean = true,
) {
    if (selectedStage == null || stages.size <= 1) {
        TimeScapeStageBody(
            selectedStage = selectedStage,
            state = state,
            shellState = shellState,
            detailRecoveryMessage = detailRecoveryMessage,
            detailState = detailState,
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            showDetailInline = showDetailInline,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = modifier,
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .timeScapeStagePagerDrag(
                        enabled = true,
                        stageWidthPx = stageWidthPx,
                        stages = stages,
                        selectedStageId = selectedStage.id,
                        pagerState = pagerState,
                        reducedMotion = reducedMotion,
                        launchStageMotion = { action ->
                            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { action() }
                        },
                    ),
        ) {
            stages.forEachIndexed { index, stage ->
                val stageModifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (index - pagerState.pagePosition) * stageWidthPx
                        }
                if (stage.id == selectedStage.id) {
                    TimeScapeStageBody(
                        selectedStage = stage,
                        state = state,
                        shellState = shellState,
                        detailRecoveryMessage = detailRecoveryMessage,
                        detailState = detailState,
                        focusedCardId = focusedCardId,
                        onDetailVisibilityChanged = onDetailVisibilityChanged,
                        onFocusedCardChanged = onFocusedCardChanged,
                        showDetailInline = showDetailInline,
                        onAction = onAction,
                        appIconLoader = appIconLoader,
                        modifier = stageModifier,
                    )
                } else {
                    TimeScapeNeighborStagePage(
                        stage = stage,
                        state = state,
                        shellState = shellState,
                        onAction = onAction,
                        appIconLoader = appIconLoader,
                        modifier = stageModifier,
                    )
                }
            }
        }
    }
}

/**
 * A non-selected stage rendered only because it is (or was just) adjacent during a pager drag.
 * It owns its own ephemeral detail/focus state rather than the durable, context-restorable state
 * the actually-selected stage uses -- that state is only meaningful once a settle commits this
 * stage as selected, at which point [TimeScapeCompactStagePager] switches it to the durable path.
 */
@Composable
private fun TimeScapeNeighborStagePage(
    stage: AppStage,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    val detailState =
        rememberTimeScapeCardDetailState(
            stageId = stage.id,
            motion = state.launcherSettings.cards.timeScapeAppearance.motion,
            globalReducedMotion = state.launcherSettings.motion.reducedMotion,
        )
    var focusedCardId by remember(stage.id) { mutableStateOf<LauncherCardId?>(null) }
    TimeScapeStageBody(
        selectedStage = stage,
        state = state,
        shellState = shellState,
        detailRecoveryMessage = null,
        detailState = detailState,
        focusedCardId = focusedCardId,
        onDetailVisibilityChanged = {},
        onFocusedCardChanged = { focusedCardId = it },
        onAction = onAction,
        appIconLoader = appIconLoader,
        modifier = modifier,
    )
}

@Composable
private fun TimeScapeTemplateStaticCanvas(
    elements: List<TimeScapeStaticElement>,
    dynamicSlots: List<TimeScapeDynamicSlot>,
    canvasWidthDp: Int,
    canvasHeightDp: Int,
    gridColumns: Int,
    gridRows: Int,
    leadingPaneWidthDp: Int,
    hingeGapDp: Int,
    trailingPaneWidthDp: Int,
) {
    if (gridColumns <= 0 || gridRows <= 0) return
    val cellWidthDp = canvasWidthDp.toFloat() / gridColumns
    val cellHeightDp = canvasHeightDp.toFloat() / gridRows
    val paneIntervals =
        timeScapeTemplatePaneIntervals(
            canvasWidthDp = canvasWidthDp,
            leadingPaneWidthDp = leadingPaneWidthDp,
            hingeGapDp = hingeGapDp,
            trailingPaneWidthDp = trailingPaneWidthDp,
        )
    elements.forEach { element ->
        val placement = element.placement
        val x = cellWidthDp * placement.cell.column
        val y = cellHeightDp * placement.cell.row
        val width = cellWidthDp * placement.span.columns
        val height = cellHeightDp * placement.span.rows
        timeScapeTemplateFragments(x, width, paneIntervals).forEachIndexed { index, fragment ->
            Box(
                modifier =
                    Modifier
                        .offset(x = fragment.startDp.dp, y = y.dp)
                        .width(fragment.widthDp.dp)
                        .height(height.dp)
                        .testTag(timeScapeTemplateFragmentTestTag(element.id.value, index, isSlot = false)),
            ) {
                if (index == 0) {
                    Text(
                        text = timeScapeTemplateElementLabel(element.type),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
    dynamicSlots.forEach { slot ->
        val placement = slot.placement
        val x = cellWidthDp * placement.cell.column
        val width = cellWidthDp * placement.span.columns
        timeScapeTemplateFragments(x, width, paneIntervals).forEachIndexed { index, fragment ->
            Box(
                modifier =
                    Modifier
                        .offset(
                            x = fragment.startDp.dp,
                            y = (cellHeightDp * placement.cell.row).dp,
                        ).width(fragment.widthDp.dp)
                        .height((cellHeightDp * placement.span.rows).dp)
                        .testTag(timeScapeTemplateFragmentTestTag(slot.id.value, index, isSlot = true)),
            )
        }
    }
}

internal fun timeScapeTemplateElementTestTag(id: String): String = "timescape-template-element-$id"

internal fun timeScapeTemplateSlotTestTag(id: String): String = "timescape-template-slot-$id"

internal fun timeScapeTemplatePaneFragmentTestTag(
    baseTag: String,
    paneIndex: Int,
): String = "$baseTag-pane-$paneIndex"

private fun timeScapeTemplateFragmentTestTag(
    id: String,
    fragmentIndex: Int,
    isSlot: Boolean,
): String {
    val baseTag =
        if (isSlot) {
            timeScapeTemplateSlotTestTag(id)
        } else {
            timeScapeTemplateElementTestTag(id)
        }
    return if (fragmentIndex == 0) baseTag else timeScapeTemplatePaneFragmentTestTag(baseTag, fragmentIndex)
}

private data class TimeScapeTemplateHorizontalInterval(
    val startDp: Float,
    val endDp: Float,
) {
    val widthDp: Float
        get() = endDp - startDp
}

private fun timeScapeTemplatePaneIntervals(
    canvasWidthDp: Int,
    leadingPaneWidthDp: Int,
    hingeGapDp: Int,
    trailingPaneWidthDp: Int,
): List<TimeScapeTemplateHorizontalInterval> {
    if (hingeGapDp <= 0) {
        return listOf(TimeScapeTemplateHorizontalInterval(0f, canvasWidthDp.toFloat()))
    }
    val trailingStartDp = leadingPaneWidthDp + hingeGapDp
    return buildList {
        if (leadingPaneWidthDp > 0) {
            add(TimeScapeTemplateHorizontalInterval(0f, leadingPaneWidthDp.toFloat()))
        }
        if (trailingPaneWidthDp > 0) {
            add(
                TimeScapeTemplateHorizontalInterval(
                    startDp = trailingStartDp.toFloat(),
                    endDp = (trailingStartDp + trailingPaneWidthDp).coerceAtMost(canvasWidthDp).toFloat(),
                ),
            )
        }
    }
}

private fun timeScapeTemplateFragments(
    placementStartDp: Float,
    placementWidthDp: Float,
    paneIntervals: List<TimeScapeTemplateHorizontalInterval>,
): List<TimeScapeTemplateHorizontalInterval> {
    val placementEndDp = placementStartDp + placementWidthDp
    return paneIntervals.mapNotNull { pane ->
        val startDp = maxOf(placementStartDp, pane.startDp)
        val endDp = minOf(placementEndDp, pane.endDp)
        if (endDp > startDp) TimeScapeTemplateHorizontalInterval(startDp, endDp) else null
    }
}

private fun timeScapeTemplateElementLabel(type: TimeScapeStaticElementType): String =
    when (type) {
        TimeScapeStaticElementType.CLOCK -> "Clock"
        TimeScapeStaticElementType.SEARCH -> "Search"
        TimeScapeStaticElementType.APP_CAROUSEL -> "Apps"
        TimeScapeStaticElementType.DOCK -> "Dock"
        TimeScapeStaticElementType.IMAGE -> "Image"
        TimeScapeStaticElementType.SHAPE -> "Shape"
        TimeScapeStaticElementType.WIDGET -> "Widget"
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
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    if (selectedStage == null) {
        TimeScapeUnavailableState(
            access = state.notificationAccessStatus,
            recoveryMessage = detailRecoveryMessage,
            installedApps = state.installedApps,
            onAction = onAction,
            modifier = modifier,
        )
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
            appIconLoader,
            modifier,
        )
    }
}

@Composable
private fun TimeScapeStageRail(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(8.dp).verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Stages", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onAction(LauncherShellAction.SelectPreviousAppStage) }) { Text("Previous") }
        stages.forEach { stage ->
            TimeScapeStageRailTile(
                stageId = stage.id,
                isSelected = stage.id == selectedStageId,
                label = stageLabel(stage.id, state),
                identity = stageAppIdentity(stage.id, state),
                appearance = state.launcherSettings.cards.timeScapeAppearance,
                appIconLoader = appIconLoader,
                onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) },
            )
        }
        TextButton(onClick = { onAction(LauncherShellAction.SelectNextAppStage) }) { Text("Next") }
    }
}

/**
 * A single stage tile in the rail: a small deterministically-tinted icon slot (reusing the same
 * per-app seed color mechanism as populated [TimeScapeCardSurface] cards, via
 * [resolveTimeScapeCardColors]) with a short caption below, and a clear ring/elevation treatment
 * for the currently selected stage.
 */
@Composable
private fun TimeScapeStageRailTile(
    stageId: AppStageId,
    isSelected: Boolean,
    label: String,
    identity: AppIdentity?,
    appearance: TimeScapeAppearanceSettings,
    appIconLoader: AppIconLoader,
    onClick: () -> Unit,
) {
    val materialBackground = MaterialTheme.colorScheme.onSurface
    val materialAccent = MaterialTheme.colorScheme.primary
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
    val colors =
        remember(appearance, stageId, materialBackground, materialAccent, appColor) {
            resolveTimeScapeCardColors(
                appearance = appearance,
                background = TimeScapeCardBackground(appSeed = stageId.packageName.value, appColor = appColor),
                materialBackground = materialBackground,
                materialAccent = materialAccent,
            )
        }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.background,
        contentColor = colors.foreground,
        tonalElevation = if (isSelected) 6.dp else 0.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        border = if (isSelected) BorderStroke(2.dp, colors.accent) else null,
        modifier =
            Modifier.width(64.dp).semantics {
                contentDescription =
                    if (isSelected) "$label, selected. Open stage" else "$label. Open stage"
            },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (identity != null) {
                LauncherAppIcon(
                    identity = identity,
                    label = label,
                    iconLoader = appIconLoader,
                    modifier = Modifier.launcherIconSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .launcherIconSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label.firstOrNull()?.uppercase().orEmpty(), color = colors.foreground)
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    stages: List<AppStage>,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label = selectedStage?.let { stageLabel(it.id, state) } ?: "TimeScape"
    var overflowExpanded by rememberSaveable(selectedStage?.let(::timeScapeStageSelectorItemKey)) {
        mutableStateOf(false)
    }
    var addStageExpanded by rememberSaveable(selectedStage?.let(::timeScapeStageSelectorItemKey)) {
        mutableStateOf(false)
    }
    val pinnedStageIds = stages.filter(AppStage::isPinned).map(AppStage::id).toSet()
    val addableApps =
        state.installedApps
            .filterNot { app ->
                AppStageId(app.identity.packageName, app.identity.profile.id) in pinnedStageIds
            }
            .distinctBy { app -> "${app.identity.profile.id.value}:${app.identity.packageName.value}" }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { app -> app.label })
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
        if (selectedApp != null) {
            LauncherAppIcon(
                identity = selectedApp.identity,
                label = label,
                iconLoader = appIconLoader,
                modifier = Modifier.launcherIconSize().padding(end = 12.dp),
            )
        }
        Column(
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription = "TimeScape stage: $label"
                    stateDescription = selectedStage?.timeScapeStageStateDescription() ?: "No stage selected"
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Text(text = "TimeScape", style = MaterialTheme.typography.labelMedium)
        }
        if (selectedStage != null) {
            if (addableApps.isNotEmpty()) {
                Box {
                    TextButton(onClick = { addStageExpanded = true }) { Text("Add stage") }
                    RiffleContextMenu(
                        expanded = addStageExpanded,
                        onDismissRequest = { addStageExpanded = false },
                    ) {
                        addableApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text("Pin ${app.label}") },
                                onClick = {
                                    addStageExpanded = false
                                    onAction(
                                        LauncherShellAction.ToggleAppStagePinned(
                                            AppStageId(app.identity.packageName, app.identity.profile.id),
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
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
                RiffleContextMenu(
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
    appIconLoader: AppIconLoader,
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
            TimeScapeEmptyStage(
                stage = stage,
                shellState = shellState,
                detailState = detailState,
                showDetailInline = showDetailInline,
                state = state,
                appIconLoader = appIconLoader,
                onAction = onAction,
                modifier = modifier,
            )
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
                appIconLoader = appIconLoader,
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
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)
    val stageAppIdentityValue = remember(stage.id, state) { stageAppIdentity(stage.id, state) }
    var stageAppColor by remember(stageAppIdentityValue, appIconLoader) {
        mutableStateOf(stageAppIdentityValue?.let(appIconLoader::cachedColorFor))
    }
    LaunchedEffect(stageAppIdentityValue, appIconLoader) {
        stageAppColor =
            stageAppIdentityValue?.let { appIdentity ->
                appIconLoader.cachedColorFor(appIdentity)
                    ?: withContext(Dispatchers.Default) { appIconLoader.colorFor(appIdentity) }
            }
    }
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

    fun navigate(direction: CardStackNavigationDirection): Boolean {
        val result = controller.navigate(focusState, cardIds, direction)
        if (result is CardStackFocusResult.Applied) {
            if (result.focusChanged) settleTransitionId++
            onFocusedCardChanged(result.state.focusedCardId)
            return !result.boundaryReached
        }
        return false
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
        val isDetailVisible = detailState.expansionState.isVisible && showDetailInline
        // Siblings stay composed (and thus discoverable/re-focusable) but dimmed while a card's
        // detail is expanded, instead of being torn down entirely.
        val stackDimFactor = if (isDetailVisible) TIME_SCAPE_SIBLING_DIM_FACTOR else 1f
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CardStack(
                        entries =
                            timeScapeNotificationStackEntries(
                                resolution = resolution,
                                cardCount = cards.size,
                                activeCardIndex = activeCardIndex,
                            ),
                        animationSpec = resolution.animation,
                        reducedMotion = resolution.reducedMotion,
                        itemKey = { entry -> cards[entry.cardIndex].content.id },
                        dimFactor = stackDimFactor,
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
                                onNavigate = ::navigate,
                                onExpand = { detailState.expand(activeCard.content.id) },
                            ),
                    ) { entry, cardModifier ->
                        val card = cards[entry.cardIndex]
                        val artwork =
                            remember(card.artworkSourceKey, card.artworkBase64, artworkCache) {
                                card.artworkSourceKey?.let { sourceKey ->
                                    artworkCache.getOrDecode(sourceKey, card.artworkBase64)
                                }
                            }
                        val focusedCardSemantics =
                            if (entry.cardIndex == activeCardIndex) {
                                Modifier.semantics {
                                    contentDescription =
                                        "Focused ${timeScapeCardKindLabel(card)} card: ${card.title}. ${card.text}"
                                    stateDescription = "Card ${entry.cardIndex + 1} of ${cards.size}"
                                    liveRegion = LiveRegionMode.Polite
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction("Previous card") {
                                                navigate(CardStackNavigationDirection.PREVIOUS)
                                            },
                                            CustomAccessibilityAction("Next card") {
                                                navigate(CardStackNavigationDirection.NEXT)
                                            },
                                            CustomAccessibilityAction("Show details") {
                                                detailState.expand(card.content.id)
                                                true
                                            },
                                        )
                                }
                            } else {
                                Modifier
                            }
                        TimeScapeCardSurface(
                            appearance = state.launcherSettings.cards.timeScapeAppearance,
                            background =
                                TimeScapeCardBackground(
                                    artwork = artwork,
                                    appSeed = stage.id.packageName.value,
                                    appColor = stageAppColor,
                                ),
                            modifier =
                                cardModifier.size(
                                    width = resolution.cardWidthDp.dp,
                                    height = resolution.cardHeightDp.dp,
                                ).then(focusedCardSemantics),
                            contentPadding = timeScapeResolvedContentPadding(resolution),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.title, style = MaterialTheme.typography.titleMedium)
                                Text(card.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                TimeScapeCardNavigationControls(
                    position = activeCardIndex + 1,
                    count = cards.size,
                    onPrevious = { navigate(CardStackNavigationDirection.PREVIOUS) },
                    onNext = { navigate(CardStackNavigationDirection.NEXT) },
                )
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
            if (isDetailVisible) {
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
            }
        }
    }
}

internal const val TIME_SCAPE_SIBLING_DIM_FACTOR = 0.18f

/** Keeps every active notification reachable even when the visual stack depth is smaller. */
internal fun timeScapeNotificationStackEntries(
    resolution: TimeScapeCardStackResolution,
    cardCount: Int,
    activeCardIndex: Int,
): List<CardStackLayoutEntry> =
    resolution.layoutPolicy
        .copy(maxVisibleDepth = maxOf(resolution.layoutPolicy.maxVisibleDepth, cardCount - 1))
        .entries(
            cardCount = cardCount,
            activeIndex = activeCardIndex,
            reducedMotion = resolution.reducedMotion,
        )

@Composable
private fun TimeScapeCardNavigationControls(
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
        TextButton(onClick = onPrevious, enabled = position > 1) { Text("Previous card") }
        Text(
            text = "Card $position of $count",
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription = "Focused card position"
                    stateDescription = "Card $position of $count"
                },
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = onNext, enabled = position < count) { Text("Next card") }
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
@Suppress("LongMethod")
private fun TimeScapeEmptyStage(
    stage: AppStage,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailState: TimeScapeCardDetailState,
    showDetailInline: Boolean,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val notificationAccessStatus = state.notificationAccessStatus
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
    val identity = stageAppIdentity(stage.id, state)
    val label = stageLabel(stage.id, state)
    var emptyStageAppColor by remember(identity, appIconLoader) {
        mutableStateOf(identity?.let(appIconLoader::cachedColorFor))
    }
    LaunchedEffect(identity, appIconLoader) {
        emptyStageAppColor =
            identity?.let { appIdentity ->
                appIconLoader.cachedColorFor(appIdentity)
                    ?: withContext(Dispatchers.Default) { appIconLoader.colorFor(appIdentity) }
            }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val viewport = TimeScapeViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveTimeScapeCardStack(
                    viewport = viewport,
                    capabilities = timeScapeRendererCapabilities(),
                )
            }
        TimeScapeCardSurface(
            appearance = state.launcherSettings.cards.timeScapeAppearance,
            background = TimeScapeCardBackground(appSeed = stage.id.packageName.value, appColor = emptyStageAppColor),
            modifier =
                Modifier
                    .size(width = resolution.cardWidthDp.dp, height = resolution.cardHeightDp.dp)
                    .testTag(TIME_SCAPE_EMPTY_STAGE_CARD_TEST_TAG),
            contentPadding = timeScapeResolvedContentPadding(resolution),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (identity != null) {
                    LauncherAppIcon(
                        identity = identity,
                        label = label,
                        iconLoader = appIconLoader,
                        modifier = Modifier.size(56.dp),
                    )
                }
                if (notificationAccessStatus != NotificationAccessStatus.GRANTED) {
                    Text(
                        text = notificationAccessStatus.timeScapeAccessMessage,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (
                        notificationAccessStatus == NotificationAccessStatus.NOT_GRANTED ||
                        notificationAccessStatus == NotificationAccessStatus.REVOKED
                    ) {
                        TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                            Text("Allow access")
                        }
                    }
                }
                Text(
                    when {
                        stage.lifecycle.name == "PROFILE_LOCKED" -> "Profile unavailable"
                        notificationAccessStatus == NotificationAccessStatus.GRANTED -> "Nothing new"
                        else -> "Stage ready"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "This stage stays available so you can return to it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
    }
}

internal const val TIME_SCAPE_EMPTY_STAGE_CARD_TEST_TAG = "timescape-empty-stage-card"

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
    installedApps: List<com.riffle.core.domain.launcher.apps.InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = access.timeScapeAccessMessage,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
        )
        TimeScapeDetailRecoveryMessage(recoveryMessage)
        if (access == NotificationAccessStatus.NOT_GRANTED || access == NotificationAccessStatus.REVOKED) {
            TextButton(onClick = { onAction(LauncherShellAction.RequestNotificationAccess) }) {
                Text("Allow access")
            }
        }
        if (installedApps.isEmpty()) {
            Text("Install an app to create your first stage.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Choose an app to keep as a stage.", style = MaterialTheme.typography.bodyMedium)
            val stageApps =
                installedApps
                    .distinctBy { app -> "${app.identity.profile.id.value}:${app.identity.packageName.value}" }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { app -> app.label })
            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = stageApps,
                    key = { app ->
                        "${app.identity.profile.id.value}:${app.identity.packageName.value}"
                    },
                ) { app ->
                    TextButton(
                        onClick = {
                            onAction(
                                LauncherShellAction.ToggleAppStagePinned(
                                    AppStageId(app.identity.packageName, app.identity.profile.id),
                                ),
                            )
                        },
                    ) {
                        Text("Pin ${app.label}")
                    }
                }
            }
        }
    }
}

private val NotificationAccessStatus.timeScapeAccessMessage: String
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> "No active stages yet. New notifications will appear here."
            NotificationAccessStatus.NOT_GRANTED -> "Allow notification access to show your app stages."
            NotificationAccessStatus.REVOKED -> "Notification access was revoked. Restore access to update stages."
            NotificationAccessStatus.UNKNOWN -> "Checking notification access."
        }

private data class TimeScapeDetailOrigin(
    val stageKey: String?,
    val cardId: LauncherCardId,
)

private fun timeScapeEmptyDetailCardId(stageId: AppStageId): LauncherCardId =
    LauncherCardId("stage-empty:${stageId.profileId.value}:${stageId.packageName.value}")

internal fun timeScapeStageKey(stageId: AppStageId): String {
    return "${stageId.profileId.value}:${stageId.packageName.value}"
}

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

private val TimeScapePostureTransitionStateSaver =
    Saver<TimeScapePostureTransitionState, List<String>>(
        save = { state -> listOf(state.settledPosture.name, state.pendingPosture?.name.orEmpty()) },
        restore = { saved ->
            val settled = saved.getOrNull(0)?.let(::timeScapePostureOrNull) ?: TimeScapePosture.UNKNOWN
            val pending = saved.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::timeScapePostureOrNull)
            TimeScapePostureTransitionState(settled, pending)
        },
    )

private fun timeScapePostureOrNull(value: String): TimeScapePosture? {
    return runCatching { TimeScapePosture.valueOf(value) }.getOrNull()
}

/**
 * A slim, always-centered-on-selection horizontal strip synced to the pager's fractional
 * [pagePosition] -- mirroring the reference app's chapter-carousel concept with Compose idioms:
 * each item's alpha/scale is a function of its distance from [pagePosition], so it visually tracks
 * an in-progress drag rather than jumping only when a page fully settles. Evolves the previous
 * static [TimeScapeStageSelector] in place rather than adding a redundant second control; its
 * Previous/Next buttons remain for non-drag (keyboard, switch, accessibility) navigation.
 */
@Composable
private fun TimeScapeStageSpine(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    pagePosition: Float,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
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
                val index = stages.indexOf(stage)
                val proximity = 1f - abs(index - pagePosition).coerceIn(0f, 1f)
                val itemAlpha = TIME_SCAPE_SPINE_MIN_ALPHA + (1f - TIME_SCAPE_SPINE_MIN_ALPHA) * proximity
                val itemScale = TIME_SCAPE_SPINE_MIN_SCALE + (1f - TIME_SCAPE_SPINE_MIN_SCALE) * proximity
                val identity = stageAppIdentity(stage.id, state)
                val label = stageLabel(stage.id, state)
                TextButton(
                    onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) },
                    modifier =
                        Modifier
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                                alpha = itemAlpha
                            }
                            .semantics {
                                contentDescription =
                                    "$label" +
                                    if (stage.id == selectedStageId) {
                                        ", selected. Open stage"
                                    } else {
                                        ". Open stage"
                                    }
                            },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (identity != null) {
                            LauncherAppIcon(
                                identity = identity,
                                label = label,
                                iconLoader = appIconLoader,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(label)
                    }
                }
            }
        }
        TextButton(onClick = { onAction(LauncherShellAction.SelectNextAppStage) }) { Text("Next") }
    }
}

private const val TIME_SCAPE_SPINE_MIN_ALPHA = 0.45f
private const val TIME_SCAPE_SPINE_MIN_SCALE = 0.85f

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

private fun stageAppIdentity(
    id: AppStageId,
    state: LauncherShellState,
): AppIdentity? =
    state.installedApps.firstOrNull { app ->
        app.identity.packageName == id.packageName && app.identity.profile.id == id.profileId
    }?.identity

private fun AppStage.timeScapeStageStateDescription(): String =
    buildList {
        add(
            origins
                .sortedBy { origin -> origin.name }
                .joinToString(" + ") { origin -> origin.name.lowercase().replaceFirstChar(Char::uppercase) },
        )
        add("Profile ${id.profileId.value}")
        add(if (isPinned) "Pinned" else "Dynamic")
        add(
            when (lifecycle) {
                com.riffle.core.domain.launcher.cards.AppStageLifecycle.ACTIVE -> "Active"
                com.riffle.core.domain.launcher.cards.AppStageLifecycle.EMPTY -> "Empty"
                com.riffle.core.domain.launcher.cards.AppStageLifecycle.PROFILE_LOCKED -> "Profile unavailable"
            },
        )
        add("${content.size} ${if (content.size == 1) "card" else "cards"}")
    }.joinToString(", ")

private fun timeScapeCardKindLabel(card: AppStageNotificationCard): String =
    when (card.content.kind) {
        com.riffle.core.domain.launcher.cards.AppStageContentKind.NOTIFICATION -> "notification"
        com.riffle.core.domain.launcher.cards.AppStageContentKind.MEDIA -> "media"
    }

private fun NotificationStageAction.label(): String =
    when (this) {
        NotificationStageAction.Open -> "Open"
        NotificationStageAction.Dismiss -> "Dismiss"
        is NotificationStageAction.MediaControl ->
            command.name.lowercase().replaceFirstChar { character -> character.titlecase() }
        is NotificationStageAction.ProviderAction -> "Action"
    }
