@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AndroidNotificationStageActionGateway
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.AppStageShellStateReconciler
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.cards.AdaptiveStageDynamicSlot
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneLayout
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneLayoutPolicy
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneMode
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStagePostureTransitionState
import com.riffle.core.domain.launcher.cards.AdaptiveStageRailSide
import com.riffle.core.domain.launcher.cards.AdaptiveStageStaticElement
import com.riffle.core.domain.launcher.cards.AdaptiveStageTemplateCatalogDefaults
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
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
import com.riffle.core.domain.launcher.cards.mergedContentByRecency
import com.riffle.core.domain.launcher.cards.variantFor
import com.riffle.core.domain.launcher.cards.visibleStaticElements
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackResolution
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp
import com.riffle.core.domain.launcher.settings.resolveAdaptiveStageCardStack
import com.riffle.core.domain.launcher.settings.resolveAdaptiveStageRailCardStack
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Cards mode reuses the persisted home-gesture bindings, but only lets a subset of actions
 * through: stage navigation, exiting back to Standard Home, and reaching the app drawer/search so
 * Cards mode stays a normal, discoverable overlay rather than an isolated static surface.
 */
internal fun adaptiveStageAppStageActionFilter(action: LauncherShellAction): Boolean =
    when (action) {
        LauncherShellAction.SelectNextAppStage,
        LauncherShellAction.SelectPreviousAppStage,
        LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
        LauncherShellAction.OpenAppDrawer,
        LauncherShellAction.OpenSearch,
        -> true

        else -> false
    }

/**
 * `null` [configuredRailSide] means the user has never chosen a rail edge, so the active
 * template's [templateRailSide] applies; once the user picks an edge in settings it always wins,
 * matching how every other explicit user preference in this file overrides its template default.
 */
internal fun resolveAdaptiveStageRailSide(
    configuredRailSide: AdaptiveStageRailSide?,
    templateRailSide: AdaptiveStageRailSide?,
): AdaptiveStageRailSide = configuredRailSide ?: templateRailSide ?: AdaptiveStageRailSide.LEADING

/**
 * Mirrors [resolveAdaptiveStageRailSide]'s shape: the pane arrangement is a plain configured user
 * preference today, with no template or device override to reconcile against yet.
 */
@Suppress("MaxLineLength")
internal fun resolveAdaptiveStagePaneArrangement(value: AdaptiveStagePaneArrangement): AdaptiveStagePaneArrangement = value

/** The Cards home surface, compact by default and pane-adaptive for the current launcher window. */
@Composable
internal fun AdaptiveStageAppStageSurface(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    windowLayout: AdaptiveStageWindowLayout? = null,
    context: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    onContextChanged: (AdaptiveStageInteractionContext) -> Unit = {},
    appIconLoader: AppIconLoader = EmptyAppIconLoader,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets =
        AdaptiveStageSafeInsetsDp(
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
    val detailState =
        selectedStage?.let { stage ->
            rememberAdaptiveStageCardDetailState(
                scopeKey = stage.id,
                motion = state.launcherSettings.cards.adaptiveStageAppearance.motion,
                globalReducedMotion = state.launcherSettings.motion.reducedMotion,
            )
        }
    // Local, non-persisted UI state -- the "All notifications" page merges content across every
    // real stage (see #1056/#1057), so it deliberately isn't a real AppStageId and never touches
    // AppStagePlanner, LauncherShellAction's stage-selection reducer, or persisted preferences the
    // way a real stage selection does. Selecting a real stage through any of the existing paths
    // (SelectAppStage/prev/next/tap) implicitly leaves this page, since those all flow through the
    // callbacks below that explicitly clear it.
    var allNotificationsSelected by rememberSaveable { mutableStateOf(false) }
    val allNotificationsDetailState =
        rememberAdaptiveStageCardDetailState(
            scopeKey = "all-notifications",
            motion = state.launcherSettings.cards.adaptiveStageAppearance.motion,
            globalReducedMotion = state.launcherSettings.motion.reducedMotion,
        )

    LaunchedEffect(selectedStage?.id, state.launcherSettings.cards.adaptiveStageTemplateId) {
        onContextChanged(
            context.copy(
                selectedStageKey = selectedStage?.id?.let(::adaptiveStageStageKey),
                templateId = state.launcherSettings.cards.adaptiveStageTemplateId.value,
            ),
        )
    }

    LaunchedEffect(shellState.snapshot.stages, shellState.notificationCards, shellState.emptyAppCards) {
        val availableStageKeys = shellState.snapshot.stages.map { stage -> adaptiveStageStageKey(stage.id) }.toSet()
        val availableCardKeys =
            shellState.notificationCards.map { card -> card.content.id.value }.toSet() +
                shellState.emptyAppCards.keys.map { stageId -> adaptiveStageEmptyDetailCardId(stageId).value }
        val reconciled = context.reconcile(availableStageKeys, availableCardKeys)
        if (reconciled != context) onContextChanged(reconciled)
    }

    val detailOrigin =
        detailCardKey?.let { cardKey ->
            AdaptiveStageDetailOrigin(detailStageKey, LauncherCardId(cardKey))
        }
    LaunchedEffect(context.selectedStageKey, shellState.snapshot.stages) {
        shellState.snapshot.stages
            .firstOrNull { stage -> adaptiveStageStageKey(stage.id) == context.selectedStageKey }
            ?.takeIf { stage -> stage.id != selectedStage?.id }
            ?.let { stage -> onAction(LauncherShellAction.SelectAppStage(stage.id)) }
    }
    LaunchedEffect(detailOrigin, selectedStage) {
        detailOrigin?.let { origin ->
            val isStillAvailable =
                selectedStage?.let { stage ->
                    adaptiveStageStageKey(stage.id) == origin.stageKey &&
                        (
                            stage.content.any { it.id == origin.cardId } ||
                                origin.cardId == adaptiveStageEmptyDetailCardId(stage.id)
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
                    actionFilter = ::adaptiveStageAppStageActionFilter,
                ),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            val measuredWindow = AdaptiveStageWindowLayout(maxWidth.value.toInt(), maxHeight.value.toInt())
            // Window metrics arrive independently of the Cards-mode selection. Until Android has
            // reported a usable window, keep the recovery body in the measured Compose bounds
            // instead of laying out a zero-sized adaptive pane beneath the header.
            val adaptiveWindow =
                windowLayout
                    ?.insetLocal(safeInsets)
                    ?.takeIf(AdaptiveStageWindowLayout::hasUsableBounds)
                    ?: measuredWindow
            var postureTransition by rememberSaveable(stateSaver = AdaptiveStagePostureTransitionStateSaver) {
                mutableStateOf(AdaptiveStagePostureTransitionState())
            }
            LaunchedEffect(adaptiveWindow.posture) {
                postureTransition = postureTransition.transitionTo(adaptiveWindow.posture)
                withFrameNanos { }
                postureTransition = postureTransition.settle()
            }
            val initialPaneLayout =
                remember(adaptiveWindow, postureTransition.effectivePosture) {
                    AdaptiveStagePaneLayoutPolicy().layoutFor(
                        adaptiveWindow.copy(posture = postureTransition.effectivePosture),
                    )
                }
            val templateVariant =
                remember(
                    state.launcherSettings.cards.adaptiveStageTemplateId,
                    state.settingsLayoutDeviceClass,
                    initialPaneLayout.mode,
                ) {
                    AdaptiveStageTemplateCatalogDefaults.templates
                        .firstOrNull { template -> template.id == state.launcherSettings.cards.adaptiveStageTemplateId }
                        ?.variantFor(state.settingsLayoutDeviceClass, initialPaneLayout.mode)
                }
            val railSide =
                resolveAdaptiveStageRailSide(
                    configuredRailSide = state.launcherSettings.cards.adaptiveStageRailSide,
                    templateRailSide = templateVariant?.railSide,
                )
            val paneArrangement =
                resolveAdaptiveStagePaneArrangement(value = state.launcherSettings.cards.adaptiveStagePaneArrangement)
            val paneLayout =
                remember(adaptiveWindow, postureTransition.effectivePosture, railSide, paneArrangement) {
                    AdaptiveStagePaneLayoutPolicy().layoutFor(
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
                // The Cards stage sits directly over the live wallpaper with nothing behind it to
                // separate the two, so a busy wallpaper competes with card content for attention
                // (hands-on testing feedback). A flat, subtle scrim for the whole stage area gives
                // that separation without touching any individual card's own glass/blur/texture
                // settings.
                val backdropScrim =
                    MaterialTheme.colorScheme.scrim.copy(alpha = ADAPTIVE_STAGE_BACKDROP_SCRIM_ALPHA)
                Box(modifier = Modifier.matchParentSize().background(backdropScrim))
                AdaptiveStageTemplateStaticCanvas(
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
                    AdaptiveStagePaneMode.COMPACT ->
                        AdaptiveStageCompactContent(
                            selectedStage = selectedStage,
                            state = state,
                            shellState = shellState,
                            detailRecoveryMessage = detailRecoveryMessage,
                            detailState = detailState,
                            allNotificationsDetailState = allNotificationsDetailState,
                            focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                            allNotificationsSelected = allNotificationsSelected,
                            onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
                            onFocusedCardChanged = {
                                focusedCardIdValue = it?.value
                                onContextChanged(context.copy(focusedCardKey = it?.value))
                            },
                            onDetailVisibilityChanged = { cardId ->
                                detailCardKey = cardId?.value
                                detailStageKey = cardId?.let { selectedStage?.id?.let(::adaptiveStageStageKey) }
                                onContextChanged(context.copy(detailCardKey = cardId?.value))
                                if (cardId != null) detailRecoveryMessage = null
                            },
                            onAction = onAction,
                            appIconLoader = appIconLoader,
                        )

                    AdaptiveStagePaneMode.SPLIT ->
                        AdaptiveStageSplitContent(
                            selectedStage = selectedStage,
                            state = state,
                            shellState = shellState,
                            detailRecoveryMessage = detailRecoveryMessage,
                            detailState = detailState,
                            allNotificationsDetailState = allNotificationsDetailState,
                            focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                            selectedDetailCardId = detailOrigin?.cardId ?: focusedCardIdValue?.let(::LauncherCardId),
                            allNotificationsSelected = allNotificationsSelected,
                            onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
                            paneLayout = paneLayout,
                            onFocusedCardChanged = {
                                focusedCardIdValue = it?.value
                                onContextChanged(context.copy(focusedCardKey = it?.value))
                            },
                            onDetailVisibilityChanged = { cardId ->
                                detailCardKey = cardId?.value
                                detailStageKey = cardId?.let { selectedStage?.id?.let(::adaptiveStageStageKey) }
                                onContextChanged(context.copy(detailCardKey = cardId?.value))
                                if (cardId != null) detailRecoveryMessage = null
                            },
                            onAction = onAction,
                            appIconLoader = appIconLoader,
                        )

                    AdaptiveStagePaneMode.TWO_PANE, AdaptiveStagePaneMode.THREE_PANE -> {
                        // TOP/BOTTOM rails run as a horizontal strip outside the leading/trailing
                        // Row below, since they reserve height (paneLayout.railHeightDp) rather
                        // than width -- see AdaptiveStagePaneLayoutPolicy.reserveHorizontalRail.
                        val horizontalRail: @Composable () -> Unit = {
                            AdaptiveStageStageRail(
                                stages = shellState.snapshot.stages,
                                selectedStageId = selectedStage?.id,
                                allNotificationsSelected = allNotificationsSelected,
                                onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
                                state = state,
                                notificationCards = shellState.notificationCards,
                                appIconLoader = appIconLoader,
                                onAction = onAction,
                                modifier = Modifier.fillMaxWidth().height(paneLayout.railHeightDp.dp),
                                horizontal = true,
                            )
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (railSide == AdaptiveStageRailSide.TOP) horizontalRail()
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (railSide == AdaptiveStageRailSide.LEADING) {
                                    AdaptiveStageStageRail(
                                        stages = shellState.snapshot.stages,
                                        selectedStageId = selectedStage?.id,
                                        allNotificationsSelected = allNotificationsSelected,
                                        onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
                                        state = state,
                                        notificationCards = shellState.notificationCards,
                                        appIconLoader = appIconLoader,
                                        onAction = onAction,
                                        modifier = Modifier.width(paneLayout.railWidthDp.dp),
                                    )
                                }
                                Column(modifier = Modifier.width(paneLayout.stackWidthDp.dp).fillMaxSize()) {
                                    AdaptiveStageStageHeader(
                                        selectedStage = selectedStage,
                                        allNotificationsSelected = allNotificationsSelected,
                                        stages = shellState.snapshot.stages,
                                        state = state,
                                        appIconLoader = appIconLoader,
                                        onAction = onAction,
                                    )
                                    AdaptiveStagePageBody(
                                        page =
                                            if (allNotificationsSelected) {
                                                AdaptiveStagePage.AllNotifications
                                            } else {
                                                selectedStage?.let(AdaptiveStagePage::Stage)
                                            },
                                        state = state,
                                        shellState = shellState,
                                        detailRecoveryMessage = detailRecoveryMessage,
                                        detailState = detailState,
                                        allNotificationsDetailState = allNotificationsDetailState,
                                        focusedCardId = focusedCardIdValue?.let(::LauncherCardId),
                                        onDetailVisibilityChanged = { cardId ->
                                            detailCardKey = cardId?.value
                                            detailStageKey =
                                                cardId?.let { selectedStage?.id?.let(::adaptiveStageStageKey) }
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
                                    AdaptiveStageSupportingPane(
                                        stage = selectedStage,
                                        selectedCardId =
                                            detailOrigin?.cardId ?: focusedCardIdValue?.let(::LauncherCardId),
                                        state = state,
                                        notificationCards = shellState.notificationCards,
                                        emptyCard = selectedStage?.let { shellState.emptyAppCards[it.id] },
                                        detailState = detailState,
                                        onAction = onAction,
                                        modifier = Modifier.width(paneLayout.detailWidthDp.dp).fillMaxSize(),
                                    )
                                }
                                if (railSide == AdaptiveStageRailSide.TRAILING) {
                                    // The stack/detail panes are capped (MIN/MAX_STACK_WIDTH_DP,
                                    // DETAIL_WIDTH_DP) and don't necessarily consume this Row's
                                    // whole width -- without this filler, a Row default-packs its
                                    // children to the leading edge, so the leftover width lands
                                    // after the trailing rail instead of before it, leaving the
                                    // rail short of the true trailing screen edge.
                                    Spacer(modifier = Modifier.weight(1f))
                                    AdaptiveStageStageRail(
                                        stages = shellState.snapshot.stages,
                                        selectedStageId = selectedStage?.id,
                                        allNotificationsSelected = allNotificationsSelected,
                                        onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
                                        state = state,
                                        notificationCards = shellState.notificationCards,
                                        appIconLoader = appIconLoader,
                                        onAction = onAction,
                                        modifier = Modifier.width(paneLayout.railWidthDp.dp),
                                    )
                                }
                            }
                            if (railSide == AdaptiveStageRailSide.BOTTOM) horizontalRail()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One entry in the compact-mode pager/rail's navigable list: either a real app stage, or the
 * virtual "All notifications" page. Deliberately a plain app-layer type, not a real
 * [AppStage]/[AppStageId] -- this concept never touches [AppStagePlanner], persistence, or the
 * reducer's stage-selection gating (which requires real stage membership). See #1057.
 */
internal sealed interface AdaptiveStagePage {
    data class Stage(val stage: AppStage) : AdaptiveStagePage

    data object AllNotifications : AdaptiveStagePage
}

internal fun List<AppStage>.withAllNotificationsPage(): List<AdaptiveStagePage> =
    map(AdaptiveStagePage::Stage) + AdaptiveStagePage.AllNotifications

/**
 * The pager's index into [pages] for whatever is currently shown, independent of pages.size.
 *
 * Deliberately returns -1 (out of bounds, [List.getOrNull] resolves it to `null`) rather than
 * falling back to the All-notifications page when there's no real selected stage and the user
 * hasn't explicitly chosen All notifications -- that's the "no notification access" / zero-stages
 * case, which must keep showing [AdaptiveStageUnavailableState], not an empty merged view.
 */
internal fun adaptiveStageSelectedPageIndex(
    pages: List<AdaptiveStagePage>,
    selectedStageId: AppStageId?,
    allNotificationsSelected: Boolean,
): Int =
    if (allNotificationsSelected) {
        pages.lastIndex.coerceAtLeast(0)
    } else {
        pages.indexOfFirst { page -> page is AdaptiveStagePage.Stage && page.stage.id == selectedStageId }
    }

/**
 * What settling the pager (or tapping a rail tile) on [index] means: select the real stage and leave
 * the All-notifications page, or select the All-notifications page -- entirely local UI state, not a
 * [LauncherShellAction], since it isn't a real, persistable stage selection.
 */
internal fun adaptiveStageOnPageSettled(
    pages: List<AdaptiveStagePage>,
    index: Int,
    onAction: (LauncherShellAction) -> Unit,
    onAllNotificationsSelectedChanged: (Boolean) -> Unit,
) {
    when (val page = pages.getOrNull(index)) {
        is AdaptiveStagePage.Stage -> {
            onAllNotificationsSelectedChanged(false)
            onAction(LauncherShellAction.SelectAppStage(page.stage.id))
        }
        AdaptiveStagePage.AllNotifications -> onAllNotificationsSelectedChanged(true)
        null -> Unit
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
private fun AdaptiveStageCompactContent(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: AdaptiveStageCardDetailState?,
    allNotificationsDetailState: AdaptiveStageCardDetailState,
    focusedCardId: LauncherCardId?,
    allNotificationsSelected: Boolean,
    onAllNotificationsSelectedChanged: (Boolean) -> Unit,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
) {
    val stages = shellState.snapshot.stages
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val pages = remember(stages) { stages.withAllNotificationsPage() }
    val selectedPageIndex =
        adaptiveStageSelectedPageIndex(pages, selectedStage?.id, allNotificationsSelected)
    val pagerState =
        rememberAdaptiveStageStagePagerState(
            pageCount = pages.size,
            selectedIndex = selectedPageIndex,
            reducedMotion = reducedMotion,
            onSettle = { index ->
                adaptiveStageOnPageSettled(pages, index, onAction, onAllNotificationsSelectedChanged)
            },
        )
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveStageStageHeader(
            selectedStage = selectedStage,
            allNotificationsSelected = allNotificationsSelected,
            stages = stages,
            state = state,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
        AdaptiveStageCompactStagePager(
            pages = pages,
            selectedPageIndex = selectedPageIndex,
            pagerState = pagerState,
            state = state,
            shellState = shellState,
            detailRecoveryMessage = detailRecoveryMessage,
            detailState = detailState,
            allNotificationsDetailState = allNotificationsDetailState,
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = Modifier.weight(1f),
        )
        AdaptiveStageStageSpine(
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
 * [AdaptiveStageSupportingPane] verbatim -- it already renders the expanded card/empty-app detail
 * surface, or a summary, without any phone-vs-wide gating inside it) sized to
 * [AdaptiveStagePaneLayout.upperRegionHeightDp], over a lower region sized to
 * [AdaptiveStagePaneLayout.lowerRegionHeightDp] hosting the exact same drag-based
 * [AdaptiveStageCompactStagePager] and synced [AdaptiveStageStageSpine] that [AdaptiveStageCompactContent]
 * uses -- same drag gesture, same settle behavior, same tap-to-select -- just constrained to the
 * remaining height instead of filling the whole column.
 */
@Composable
@Suppress("LongParameterList")
private fun AdaptiveStageSplitContent(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: AdaptiveStageCardDetailState?,
    allNotificationsDetailState: AdaptiveStageCardDetailState,
    focusedCardId: LauncherCardId?,
    selectedDetailCardId: LauncherCardId?,
    allNotificationsSelected: Boolean,
    onAllNotificationsSelectedChanged: (Boolean) -> Unit,
    paneLayout: AdaptiveStagePaneLayout,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
) {
    val stages = shellState.snapshot.stages
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val pages = remember(stages) { stages.withAllNotificationsPage() }
    val selectedPageIndex =
        adaptiveStageSelectedPageIndex(pages, selectedStage?.id, allNotificationsSelected)
    val pagerState =
        rememberAdaptiveStageStagePagerState(
            pageCount = pages.size,
            selectedIndex = selectedPageIndex,
            reducedMotion = reducedMotion,
            onSettle = { index ->
                adaptiveStageOnPageSettled(pages, index, onAction, onAllNotificationsSelectedChanged)
            },
        )
    // upperRegionHeightDp/lowerRegionHeightDp sum to exactly paneLayout.contentHeightDp -- the
    // domain layer has no notion of the header this Column also renders, so using those as fixed
    // heights directly would make the Column taller than the Box it's measured within, overflowing
    // un-clipped past the header. Use their ratio as Column weights instead: Compose reserves space
    // for the non-weighted header first, then splits whatever's left between the two regions in the
    // same proportion the domain layer intended.
    val regionHeightTotal = paneLayout.upperRegionHeightDp + paneLayout.lowerRegionHeightDp
    val upperRegionWeight =
        if (regionHeightTotal > 0) {
            paneLayout.upperRegionHeightDp.toFloat() / regionHeightTotal
        } else {
            DEFAULT_SPLIT_UPPER_REGION_WEIGHT
        }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AdaptiveStageStageHeader(
            selectedStage = selectedStage,
            allNotificationsSelected = allNotificationsSelected,
            stages = stages,
            state = state,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
        AdaptiveStageSupportingPane(
            stage = selectedStage,
            selectedCardId = selectedDetailCardId,
            state = state,
            notificationCards = shellState.notificationCards,
            emptyCard = selectedStage?.let { shellState.emptyAppCards[it.id] },
            detailState = detailState,
            onAction = onAction,
            modifier = Modifier.fillMaxWidth().weight(upperRegionWeight),
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f - upperRegionWeight),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveStageCompactStagePager(
                pages = pages,
                selectedPageIndex = selectedPageIndex,
                pagerState = pagerState,
                state = state,
                shellState = shellState,
                detailRecoveryMessage = detailRecoveryMessage,
                detailState = detailState,
                allNotificationsDetailState = allNotificationsDetailState,
                focusedCardId = focusedCardId,
                onDetailVisibilityChanged = onDetailVisibilityChanged,
                onFocusedCardChanged = onFocusedCardChanged,
                onAction = onAction,
                appIconLoader = appIconLoader,
                modifier = Modifier.weight(1f),
                // The upper AdaptiveStageSupportingPane already renders the expanded card's detail;
                // showing it inline here too would duplicate it.
                showDetailInline = false,
            )
            AdaptiveStageStageSpine(
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
 * Matches [AdaptiveStagePaneLayoutPolicy]'s own SPLIT_UPPER_REGION_RATIO default, for the case where
 * paneLayout reports a zero-height content area (nothing to weight against yet).
 */
private const val DEFAULT_SPLIT_UPPER_REGION_WEIGHT = 0.6f

/**
 * Lays out every stage's content side by side, offsetting each via `graphicsLayer` translation
 * driven by [AdaptiveStageStagePagerState.pagePosition] -- mirroring [ImmediateWorkspacePager]'s
 * approach for Standard Home's own pages -- and attaches [adaptiveStageStagePagerDrag] to claim
 * horizontal drags. With zero or one stage there is nothing to page between, so this falls back to
 * rendering [AdaptiveStageStageBody] directly without a drag gesture.
 */
@Composable
@Suppress("LongParameterList")
private fun AdaptiveStageCompactStagePager(
    pages: List<AdaptiveStagePage>,
    selectedPageIndex: Int,
    pagerState: AdaptiveStageStagePagerState,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: AdaptiveStageCardDetailState?,
    allNotificationsDetailState: AdaptiveStageCardDetailState,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
    // false in SPLIT mode, where AdaptiveStageSplitContent already renders the expanded card's detail
    // in its upper AdaptiveStageSupportingPane -- rendering it inline here too would duplicate it.
    // Always true for the All-notifications page regardless -- SPLIT mode's upper pane doesn't (yet)
    // preview all-notifications cards, so its own inline overlay is the only detail surface it has.
    showDetailInline: Boolean = true,
) {
    val selectedPage = pages.getOrNull(selectedPageIndex)
    if (selectedPage == null || pages.size <= 1) {
        AdaptiveStagePageBody(
            page = selectedPage,
            state = state,
            shellState = shellState,
            detailRecoveryMessage = detailRecoveryMessage,
            detailState = detailState,
            allNotificationsDetailState = allNotificationsDetailState,
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            showDetailInline = true,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = modifier,
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = state.launcherSettings.motion.reducedMotion
    val navigationKey = remember(pages) { pages.joinToString(separator = "|", transform = ::adaptiveStagePageKey) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val stageWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .adaptiveStageStagePagerDrag(
                        enabled = true,
                        stageWidthPx = stageWidthPx,
                        pageCount = pages.size,
                        selectedIndex = selectedPageIndex,
                        navigationKey = navigationKey,
                        pagerState = pagerState,
                        reducedMotion = reducedMotion,
                        launchStageMotion = { action ->
                            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { action() }
                        },
                    ),
        ) {
            pages.forEachIndexed { index, page ->
                val stageModifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (index - pagerState.pagePosition) * stageWidthPx
                        }
                if (index == selectedPageIndex) {
                    AdaptiveStagePageBody(
                        page = page,
                        state = state,
                        shellState = shellState,
                        detailRecoveryMessage = detailRecoveryMessage,
                        detailState = detailState,
                        allNotificationsDetailState = allNotificationsDetailState,
                        focusedCardId = focusedCardId,
                        onDetailVisibilityChanged = onDetailVisibilityChanged,
                        onFocusedCardChanged = onFocusedCardChanged,
                        showDetailInline = showDetailInline,
                        onAction = onAction,
                        appIconLoader = appIconLoader,
                        modifier = stageModifier,
                    )
                } else {
                    AdaptiveStageNeighborPage(
                        page = page,
                        selectedPageIsAllNotifications = selectedPage is AdaptiveStagePage.AllNotifications,
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

internal fun adaptiveStagePageKey(page: AdaptiveStagePage): String =
    when (page) {
        is AdaptiveStagePage.Stage -> adaptiveStageStageKey(page.stage.id)
        AdaptiveStagePage.AllNotifications -> "all-notifications"
    }

/**
 * Renders whichever page is selected -- a real stage's body, the merged All-notifications body,
 * or the unavailable state when there's no stage and All notifications isn't selected either.
 */
@Composable
@Suppress("LongParameterList")
private fun AdaptiveStagePageBody(
    page: AdaptiveStagePage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: AdaptiveStageCardDetailState?,
    allNotificationsDetailState: AdaptiveStageCardDetailState,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    showDetailInline: Boolean = true,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    when (page) {
        is AdaptiveStagePage.Stage ->
            AdaptiveStageStageBody(
                selectedStage = page.stage,
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

        AdaptiveStagePage.AllNotifications ->
            AdaptiveStageAllNotificationsStack(
                stages = shellState.snapshot.stages,
                state = state,
                notificationCards = shellState.notificationCards,
                detailState = allNotificationsDetailState,
                onAction = onAction,
                appIconLoader = appIconLoader,
                modifier = modifier,
            )

        null ->
            AdaptiveStageUnavailableState(
                access = state.notificationAccessStatus,
                recoveryMessage = detailRecoveryMessage,
                installedApps = state.installedApps,
                onAction = onAction,
                modifier = modifier,
            )
    }
}

/**
 * A page rendered only because it is (or was just) adjacent during a pager drag, not selected.
 *
 * The All-notifications page's content is a strict merge of every real stage's own content, so
 * co-composing it in full alongside a real stage -- whether it's this neighbor itself, or it's
 * currently the *selected* page and a real stage is the neighbor -- would duplicate that stage's
 * cards in the composition (two semantics nodes for the same notification), not just in some rare
 * coincidental-text edge case. Either direction renders a blank placeholder instead: a deliberate
 * scope cut that still occupies the right slot for the slide transition and swaps to the full
 * stack the moment a settle actually selects that page. Real-stage-to-real-stage neighbors are
 * unaffected and still render in full, preserving the existing swipe preview.
 */
@Composable
private fun AdaptiveStageNeighborPage(
    page: AdaptiveStagePage,
    selectedPageIsAllNotifications: Boolean,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    if (page is AdaptiveStagePage.AllNotifications || selectedPageIsAllNotifications) {
        Box(modifier = modifier.fillMaxSize())
        return
    }
    AdaptiveStageNeighborStagePage(
        stage = (page as AdaptiveStagePage.Stage).stage,
        state = state,
        shellState = shellState,
        onAction = onAction,
        appIconLoader = appIconLoader,
        modifier = modifier,
    )
}

/**
 * A non-selected stage rendered only because it is (or was just) adjacent during a pager drag.
 * It owns its own ephemeral detail/focus state rather than the durable, context-restorable state
 * the actually-selected stage uses -- that state is only meaningful once a settle commits this
 * stage as selected, at which point [AdaptiveStageCompactStagePager] switches it to the durable path.
 */
@Composable
private fun AdaptiveStageNeighborStagePage(
    stage: AppStage,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    val detailState =
        rememberAdaptiveStageCardDetailState(
            scopeKey = stage.id,
            motion = state.launcherSettings.cards.adaptiveStageAppearance.motion,
            globalReducedMotion = state.launcherSettings.motion.reducedMotion,
        )
    var focusedCardId by remember(stage.id) { mutableStateOf<LauncherCardId?>(null) }
    AdaptiveStageStageBody(
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
private fun AdaptiveStageTemplateStaticCanvas(
    elements: List<AdaptiveStageStaticElement>,
    dynamicSlots: List<AdaptiveStageDynamicSlot>,
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
        adaptiveStageTemplatePaneIntervals(
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
        adaptiveStageTemplateFragments(x, width, paneIntervals).forEachIndexed { index, fragment ->
            // Deliberately no visible content here: this canvas exists to give
            // AdaptiveStageAdaptiveLayoutInteractionTest stable, positioned geometry to assert
            // against (see adaptiveStageTemplateElementTestTag usages there), not to render UI --
            // the real per-element widgets (clock, search, app carousel, dock) are the
            // Standard Home surface underneath and the pane content composed right after this.
            Box(
                modifier =
                    Modifier
                        .offset(x = fragment.startDp.dp, y = y.dp)
                        .width(fragment.widthDp.dp)
                        .height(height.dp)
                        .testTag(adaptiveStageTemplateFragmentTestTag(element.id.value, index, isSlot = false)),
            )
        }
    }
    dynamicSlots.forEach { slot ->
        val placement = slot.placement
        val x = cellWidthDp * placement.cell.column
        val width = cellWidthDp * placement.span.columns
        adaptiveStageTemplateFragments(x, width, paneIntervals).forEachIndexed { index, fragment ->
            Box(
                modifier =
                    Modifier
                        .offset(
                            x = fragment.startDp.dp,
                            y = (cellHeightDp * placement.cell.row).dp,
                        ).width(fragment.widthDp.dp)
                        .height((cellHeightDp * placement.span.rows).dp)
                        .testTag(adaptiveStageTemplateFragmentTestTag(slot.id.value, index, isSlot = true)),
            )
        }
    }
}

internal fun adaptiveStageTemplateElementTestTag(id: String): String = "adaptive-stage-template-element-$id"

internal fun adaptiveStageTemplateSlotTestTag(id: String): String = "adaptive-stage-template-slot-$id"

internal fun adaptiveStageTemplatePaneFragmentTestTag(
    baseTag: String,
    paneIndex: Int,
): String = "$baseTag-pane-$paneIndex"

private fun adaptiveStageTemplateFragmentTestTag(
    id: String,
    fragmentIndex: Int,
    isSlot: Boolean,
): String {
    val baseTag =
        if (isSlot) {
            adaptiveStageTemplateSlotTestTag(id)
        } else {
            adaptiveStageTemplateElementTestTag(id)
        }
    return if (fragmentIndex == 0) baseTag else adaptiveStageTemplatePaneFragmentTestTag(baseTag, fragmentIndex)
}

private data class AdaptiveStageTemplateHorizontalInterval(
    val startDp: Float,
    val endDp: Float,
) {
    val widthDp: Float
        get() = endDp - startDp
}

private fun adaptiveStageTemplatePaneIntervals(
    canvasWidthDp: Int,
    leadingPaneWidthDp: Int,
    hingeGapDp: Int,
    trailingPaneWidthDp: Int,
): List<AdaptiveStageTemplateHorizontalInterval> {
    if (hingeGapDp <= 0) {
        return listOf(AdaptiveStageTemplateHorizontalInterval(0f, canvasWidthDp.toFloat()))
    }
    val trailingStartDp = leadingPaneWidthDp + hingeGapDp
    return buildList {
        if (leadingPaneWidthDp > 0) {
            add(AdaptiveStageTemplateHorizontalInterval(0f, leadingPaneWidthDp.toFloat()))
        }
        if (trailingPaneWidthDp > 0) {
            add(
                AdaptiveStageTemplateHorizontalInterval(
                    startDp = trailingStartDp.toFloat(),
                    endDp = (trailingStartDp + trailingPaneWidthDp).coerceAtMost(canvasWidthDp).toFloat(),
                ),
            )
        }
    }
}

private fun adaptiveStageTemplateFragments(
    placementStartDp: Float,
    placementWidthDp: Float,
    paneIntervals: List<AdaptiveStageTemplateHorizontalInterval>,
): List<AdaptiveStageTemplateHorizontalInterval> {
    val placementEndDp = placementStartDp + placementWidthDp
    return paneIntervals.mapNotNull { pane ->
        val startDp = maxOf(placementStartDp, pane.startDp)
        val endDp = minOf(placementEndDp, pane.endDp)
        if (endDp > startDp) AdaptiveStageTemplateHorizontalInterval(startDp, endDp) else null
    }
}

@Composable
private fun AdaptiveStageStageBody(
    selectedStage: AppStage?,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailRecoveryMessage: String?,
    detailState: AdaptiveStageCardDetailState?,
    focusedCardId: LauncherCardId?,
    onDetailVisibilityChanged: (LauncherCardId?) -> Unit,
    onFocusedCardChanged: (LauncherCardId?) -> Unit = {},
    showDetailInline: Boolean = true,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    if (selectedStage == null) {
        AdaptiveStageUnavailableState(
            access = state.notificationAccessStatus,
            recoveryMessage = detailRecoveryMessage,
            installedApps = state.installedApps,
            onAction = onAction,
            modifier = modifier,
        )
    } else {
        AdaptiveStageStageContent(
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
private fun AdaptiveStageStageRail(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    allNotificationsSelected: Boolean,
    onAllNotificationsSelectedChanged: (Boolean) -> Unit,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
    horizontal: Boolean = false,
) {
    // Deliberately does not early-return on an empty stages list: the container (testTag,
    // background) still composes with an empty CardStack -- CardStackLayoutPolicy.entries()
    // already returns an empty list for cardCount = 0 -- so the rail's presence stays a stable
    // signal of "this pane mode shows a rail" independent of whether any stage exists yet. The
    // trailing "All notifications" page (see #1057) keeps at least one tile even then.
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)
    val pages = remember(stages) { stages.withAllNotificationsPage() }
    // #1059's rail audit calls for "live mini-previews (icon + latest snippet), not just an
    // icon+label chip" -- one lookup per stage's most-recent card (already privacy/redaction-
    // resolved by appStageNotificationCards), reused by every tile below.
    val latestCardByStage =
        remember(notificationCards) {
            notificationCards
                .groupBy { card -> card.content.stageId }
                .mapValues { (_, cards) -> cards.maxBy { card -> card.content.meaningfulActivityAtEpochMillis } }
        }
    val activeIndex =
        adaptiveStageSelectedPageIndex(pages, selectedStageId, allNotificationsSelected).coerceAtLeast(0)
    var settleTransitionId by rememberSaveable { mutableIntStateOf(0) }

    fun navigateToIndex(targetIndex: Int): Boolean {
        if (pages.getOrNull(targetIndex) == null) return false
        settleTransitionId++
        adaptiveStageOnPageSettled(pages, targetIndex, onAction, onAllNotificationsSelectedChanged)
        return true
    }

    fun navigate(direction: CardStackNavigationDirection): Boolean {
        val delta = if (direction == CardStackNavigationDirection.NEXT) 1 else -1
        return navigateToIndex(activeIndex + delta)
    }

    val railBackground = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
    BoxWithConstraints(
        // Explicit clip: the fan/stack visual is allowed to layer within this container, but must
        // never bleed into neighboring UI the way earlier AdaptiveStage overflow bugs did.
        // fillMaxHeight() matters for LEADING/TRAILING: the caller only pins width there (unlike
        // TOP/BOTTOM, which pins height explicitly), so without it this Box would wrap-size to its
        // content -- collapsing to zero height whenever there are no stages yet, since nothing
        // here is unconditionally present the way the old Row/Column's "Stages"/Previous/Next
        // chrome always was.
        modifier =
            modifier.testTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG)
                .then(railBackground)
                .fillMaxHeight()
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // This Box's own real bounds -- not the whole window -- are what
        // AdaptiveStageCardStackRole.RAIL's resolution sizes tiles and travel against; see
        // resolveAdaptiveStageRailCardStack's doc for why that's the right viewport to pass here.
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveAdaptiveStageRailCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                )
            }
        CardStack(
            // CardStack's own root has no size of its own -- give it the same bounded size as
            // this Box (rather than leaving it to size from its graphicsLayer-positioned,
            // layout-wise-tiny children), so BoxWithConstraints inside each entry measures
            // against real, finite constraints instead of whatever this Box's ambient
            // constraints happen to resolve to.
            modifier = Modifier.matchParentSize(),
            entries =
                resolution.layoutPolicy.entries(
                    cardCount = pages.size,
                    activeIndex = activeIndex,
                    reducedMotion = resolution.reducedMotion,
                ),
            animationSpec = resolution.animation,
            reducedMotion = resolution.reducedMotion,
            orientation = if (horizontal) CardStackOrientation.HORIZONTAL else CardStackOrientation.VERTICAL,
            itemKey = { entry -> adaptiveStagePageKey(pages[entry.cardIndex]) },
            interaction =
                CardStackInteraction(
                    focusedItemKey = pages.getOrNull(activeIndex)?.let(::adaptiveStagePageKey),
                    settleTransitionId = settleTransitionId,
                    onFocusRequest = { entry ->
                        if (entry.cardIndex != activeIndex) navigateToIndex(entry.cardIndex)
                    },
                    onSettle = { dragPx, velocityPxPerSecond ->
                        val motion =
                            if (abs(velocityPxPerSecond) >= ADAPTIVE_STAGE_STAGE_RAIL_FLING_VELOCITY_THRESHOLD_PX) {
                                velocityPxPerSecond
                            } else {
                                dragPx
                            }
                        if (abs(motion) >= ADAPTIVE_STAGE_STAGE_RAIL_SETTLE_DISTANCE_THRESHOLD_PX) {
                            val direction =
                                if (motion < 0f) {
                                    CardStackNavigationDirection.NEXT
                                } else {
                                    CardStackNavigationDirection.PREVIOUS
                                }
                            navigate(direction)
                        }
                    },
                    onSettleHaptic = {
                        haptics.adaptiveStageSettle(
                            state.launcherSettings.cards.unfoldedAppearance.motion.hapticStrength,
                        )
                    },
                    onNavigate = ::navigate,
                ),
        ) { entry, cardModifier ->
            when (val page = pages[entry.cardIndex]) {
                is AdaptiveStagePage.Stage ->
                    AdaptiveStageStageRailTile(
                        stageId = page.stage.id,
                        isSelected = !allNotificationsSelected && page.stage.id == selectedStageId,
                        label = stageLabel(page.stage.id, state),
                        snippet = latestCardByStage[page.stage.id]?.railSnippet(),
                        identity = stageAppIdentity(page.stage.id, state),
                        appearance = state.launcherSettings.cards.unfoldedAppearance,
                        appIconLoader = appIconLoader,
                        modifier = cardModifier,
                    )

                AdaptiveStagePage.AllNotifications ->
                    AdaptiveStageAllNotificationsRailTile(
                        isSelected = allNotificationsSelected,
                        appearance = state.launcherSettings.cards.unfoldedAppearance,
                        modifier = cardModifier,
                    )
            }
        }
    }
}

/** Only one [AdaptiveStageStageRail] is ever composed at a time, so a single fixed tag is unambiguous. */
internal const val ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG = "adaptive-stage-stage-rail"

/** Same settle thresholds as [AdaptiveStageNotificationStack]'s card-to-card settle, for a consistent feel. */
private const val ADAPTIVE_STAGE_STAGE_RAIL_SETTLE_DISTANCE_THRESHOLD_PX = 64f
private const val ADAPTIVE_STAGE_STAGE_RAIL_FLING_VELOCITY_THRESHOLD_PX = 500f

/**
 * A single stage tile in the rail: a small deterministically-tinted icon slot (reusing the same
 * per-app seed color mechanism as populated [AdaptiveStageCardSurface] cards, via
 * [resolveAdaptiveStageCardColors]) with a short caption and, when the stage has live content, a
 * one-line snippet of its most recent card below -- a live mini-preview rather than a bare
 * icon+label identity chip (#1059). A clear ring/elevation treatment marks the currently selected
 * stage. Non-interactive on its own -- [modifier] (supplied by the enclosing [CardStack]) already
 * carries tap-to-select/settle-drag handling, mirroring how [AdaptiveStageCardSurface] relies on
 * its own given modifier rather than an internal onClick.
 */
@Composable
private fun AdaptiveStageStageRailTile(
    stageId: AppStageId,
    isSelected: Boolean,
    label: String,
    snippet: String?,
    identity: AppIdentity?,
    appearance: AdaptiveStageAppearanceSettings,
    appIconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
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
            resolveAdaptiveStageCardColors(
                appearance = appearance,
                background = AdaptiveStageCardBackground(appSeed = stageId.packageName.value, appColor = appColor),
                materialBackground = materialBackground,
                materialAccent = materialAccent,
            )
        }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = colors.background,
        contentColor = colors.foreground,
        tonalElevation = if (isSelected) 6.dp else 0.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        border = if (isSelected) BorderStroke(2.dp, colors.accent) else null,
        modifier =
            modifier.width(64.dp).semantics {
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
            if (snippet != null) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The rail's tile for the virtual "All notifications" page (#1057) -- same shape as
 * [AdaptiveStageStageRailTile], but with no single app identity to key its color/icon off, so it
 * uses a fixed seed and a generic "All" glyph instead of a per-app [LauncherAppIcon].
 */
@Composable
private fun AdaptiveStageAllNotificationsRailTile(
    isSelected: Boolean,
    appearance: AdaptiveStageAppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val materialBackground = MaterialTheme.colorScheme.onSurface
    val materialAccent = MaterialTheme.colorScheme.primary
    val colors =
        remember(appearance, materialBackground, materialAccent) {
            resolveAdaptiveStageCardColors(
                appearance = appearance,
                background = AdaptiveStageCardBackground(appSeed = "all-notifications", appColor = null),
                materialBackground = materialBackground,
                materialAccent = materialAccent,
            )
        }
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = colors.background,
        contentColor = colors.foreground,
        tonalElevation = if (isSelected) 6.dp else 0.dp,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        border = if (isSelected) BorderStroke(2.dp, colors.accent) else null,
        modifier =
            modifier.width(64.dp).semantics {
                contentDescription =
                    if (isSelected) "All notifications, selected. Open" else "All notifications. Open"
            },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .launcherIconSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "All", color = colors.foreground, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = "All notifications",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AdaptiveStageSupportingPane(
    stage: AppStage?,
    selectedCardId: LauncherCardId?,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    emptyCard: AppStageEmptyAppCard?,
    detailState: AdaptiveStageCardDetailState?,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val card = notificationCards.firstOrNull { it.content.id == selectedCardId }
    val paneModifier = modifier.testTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG)
    if (
        emptyCard != null &&
        selectedCardId == stage?.id?.let(::adaptiveStageEmptyDetailCardId) &&
        detailState?.expansionState?.isVisible == true
    ) {
        AdaptiveStageEmptyAppDetailSurface(emptyCard, detailState, onAction, modifier = paneModifier)
        return
    }
    if (card != null && detailState?.expansionState?.isVisible == true) {
        AdaptiveStageCardDetailSurface(card, detailState, onAction, modifier = paneModifier)
        return
    }
    Surface(
        modifier = paneModifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier =
                Modifier
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
                AdaptiveStageCardMessageBody(card)
                AdaptiveStageContextActionsGrid(
                    card = card,
                    onAction = onAction,
                    onDetailRequested = { detailState?.expand(card.content.id) },
                )
            }
        }
    }
}

@Composable
private fun AdaptiveStageStageHeader(
    selectedStage: AppStage?,
    allNotificationsSelected: Boolean,
    stages: List<AppStage>,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val label =
        when {
            allNotificationsSelected -> "All notifications"
            selectedStage != null -> stageLabel(selectedStage.id, state)
            else -> "Cards"
        }
    // Per-stage actions (Add stage/Pin/overflow) only make sense for a real selected stage, and
    // must stay hidden while the virtual All-notifications page is selected -- selectedStage
    // itself isn't cleared in that state (it stays the last real selection so leaving All
    // notifications returns to it), so this needs its own explicit gate.
    val showStageActions = selectedStage != null && !allNotificationsSelected
    var overflowExpanded by rememberSaveable(selectedStage?.let(::adaptiveStageStageSelectorItemKey)) {
        mutableStateOf(false)
    }
    var addStageExpanded by rememberSaveable(selectedStage?.let(::adaptiveStageStageSelectorItemKey)) {
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
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedApp != null && !allNotificationsSelected) {
            LauncherAppIcon(
                identity = selectedApp.identity,
                label = label,
                iconLoader = appIconLoader,
                modifier = Modifier.launcherIconSize().padding(end = 12.dp),
            )
        }
        Column(
            modifier =
                Modifier.weight(1f).testTag(ADAPTIVE_STAGE_STAGE_HEADER_TEST_TAG).semantics {
                    contentDescription = "Cards stage: $label"
                    stateDescription =
                        when {
                            allNotificationsSelected -> "Showing every stage's notifications"
                            selectedStage != null -> selectedStage.adaptiveStageStageStateDescription()
                            else -> "No stage selected"
                        }
                    liveRegion = LiveRegionMode.Polite
                    // The rail/spine's visible Previous/Next buttons were removed as redundant
                    // with tapping a stage directly (or swiping, in compact/split layouts) --
                    // this keeps stage-to-stage navigation reachable for TalkBack/switch users,
                    // mirroring the "Previous card"/"Next card" CustomAccessibilityAction
                    // precedent used for intra-stack card navigation elsewhere in this file.
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Previous stage") {
                                onAction(LauncherShellAction.SelectPreviousAppStage)
                                true
                            },
                            CustomAccessibilityAction("Next stage") {
                                onAction(LauncherShellAction.SelectNextAppStage)
                                true
                            },
                        )
                },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only show the "Cards" eyebrow when it wouldn't just repeat the title above --
            // label already falls back to "Cards" itself when no stage is selected.
            if (label != "Cards") {
                Text(text = "Cards", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (showStageActions && selectedStage != null) {
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

/** Only one [AdaptiveStageStageHeader] is ever composed at a time, so a single fixed tag is unambiguous. */
internal const val ADAPTIVE_STAGE_STAGE_HEADER_TEST_TAG = "adaptive-stage-stage-header"

@Composable
private fun AdaptiveStageStageContent(
    stage: AppStage,
    state: LauncherShellState,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailState: AdaptiveStageCardDetailState,
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
            AdaptiveStageEmptyStage(
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
            AdaptiveStageNotificationStack(
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
private fun AdaptiveStageNotificationStack(
    stage: AppStage,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    detailState: AdaptiveStageCardDetailState,
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
            AdaptiveStageArtworkCache<ImageBitmap>(decode = ::decodeAdaptiveStageArtwork)
        }
    val stackKey =
        remember(stage.id) {
            CardStackKey("stage:${stage.id.profileId.value}:${stage.id.packageName.value}")
        }
    var settleTransitionId by rememberSaveable(stage.id.profileId.value, stage.id.packageName.value) {
        mutableIntStateOf(0)
    }
    val reconciledFocusedCardId =
        rememberReconciledFocusedCardId(controller, stackKey, cardIds, focusedCardId, onFocusedCardChanged)
    val focusState = CardStackFocusState(stackKey, reconciledFocusedCardId)
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

    fun jumpTo(cardId: LauncherCardId) {
        val result = controller.jumpTo(focusState, cardIds, cardId)
        if (result is CardStackFocusResult.Applied) {
            if (result.focusChanged) settleTransitionId++
            onFocusedCardChanged(result.state.focusedCardId)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveAdaptiveStageCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                )
            }
        val isDetailVisible = detailState.expansionState.isVisible && showDetailInline
        // Siblings stay composed (and thus discoverable/re-focusable) but dimmed while a card's
        // detail is expanded, instead of being torn down entirely.
        val stackDimFactor = if (isDetailVisible) ADAPTIVE_STAGE_SIBLING_DIM_FACTOR else 1f
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CardStack(
                            // CardStack's own root has no size of its own -- give it the same bounded
                            // size as this Box (rather than leaving it to size from its
                            // graphicsLayer-positioned, layout-wise-tiny children), so its
                            // .clipToBounds() clips against the real allotted area instead of a
                            // single card's footprint. Mirrors AdaptiveStageStageRail's fix for the
                            // same failure mode.
                            modifier = Modifier.matchParentSize(),
                            entries =
                                adaptiveStageNotificationStackEntries(
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
                                        haptics.adaptiveStageSettle(
                                            state.launcherSettings.cards.adaptiveStageAppearance.motion.hapticStrength,
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
                                            "Focused ${adaptiveStageCardKindLabel(card)} card: " +
                                            "${card.title}. ${card.text}"
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
                            AdaptiveStageCardSurface(
                                appearance = state.launcherSettings.cards.adaptiveStageAppearance,
                                background =
                                    AdaptiveStageCardBackground(
                                        artwork = artwork,
                                        appSeed = stage.id.packageName.value,
                                        appColor = stageAppColor,
                                    ),
                                modifier =
                                    cardModifier.size(
                                        width = resolution.cardWidthDp.dp,
                                        height = resolution.cardHeightDp.dp,
                                    ).then(focusedCardSemantics),
                                contentPadding = adaptiveStageResolvedContentPadding(resolution),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                                    AdaptiveStageCardMessageBody(card)
                                }
                            }
                        }
                    }
                    AdaptiveStageCardTimelineRail(
                        cards = cards,
                        activeCardIndex = activeCardIndex,
                        onCardSelected = ::jumpTo,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
                AdaptiveStageCardPositionIndicator(position = activeCardIndex + 1, count = cards.size)
                AdaptiveStageContextShelf(
                    card = activeCard,
                    onAction = onAction,
                    onDetailRequested = { detailState.expand(activeCard.content.id) },
                    detailFocusRequester = detailFocusRequester,
                    restoreDetailFocus = restoreDetailFocusForCardId == activeCard.content.id,
                    onDetailFocusRestored = { restoreDetailFocusForCardId = null },
                )
                AdaptiveStageDetailRecoveryMessage(detailState.sourceRemovalMessage)
            }
            if (isDetailVisible) {
                cards
                    .firstOrNull { card -> card.content.id == detailState.expansionState.cardId }
                    ?.let { card ->
                        AdaptiveStageCardDetailSurface(
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

internal const val ADAPTIVE_STAGE_SIBLING_DIM_FACTOR = 0.18f
internal const val ADAPTIVE_STAGE_BACKDROP_SCRIM_ALPHA = 0.16f

/**
 * The "All notifications" page (#1057): every stage's content merged into one recency-ordered
 * stream via [mergedContentByRecency], rendered through the same [CardStack] visual as a single
 * stage's own [AdaptiveStageNotificationStack]. Unlike that stack, per-card app identity/color is
 * resolved *inside* the [CardStack] content lambda rather than hoisted once, since consecutive
 * cards here can come from entirely different apps. Owns its own focus state locally rather than
 * lifting it to a caller -- SPLIT mode's upper AdaptiveStageSupportingPane doesn't preview
 * All-notifications cards (see AdaptiveStageCompactStagePager's showDetailInline doc), so there's
 * no sibling surface that needs to stay in sync with which card is focused here.
 */
@Composable
private fun AdaptiveStageAllNotificationsStack(
    stages: List<AppStage>,
    state: LauncherShellState,
    notificationCards: List<AppStageNotificationCard>,
    detailState: AdaptiveStageCardDetailState,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    val haptics = rememberLauncherHaptics(state.launcherSettings.haptics.feedbackStrength)
    val mergedEntries =
        remember(stages, notificationCards) {
            val cardsById = notificationCards.associateBy { card -> card.content.id }
            stages.mergedContentByRecency().mapNotNull { entry ->
                cardsById[entry.content.id]?.let { card -> card to entry.stage }
            }
        }
    val cards = mergedEntries.map { (card, _) -> card }
    val cardStages = mergedEntries.map { (_, stage) -> stage }
    val cardIds = cards.map { card -> card.content.id }
    val controller = remember { CardStackController() }
    val artworkCache =
        remember { AdaptiveStageArtworkCache<ImageBitmap>(decode = ::decodeAdaptiveStageArtwork) }
    val stackKey = remember { CardStackKey("all-notifications") }
    var settleTransitionId by rememberSaveable { mutableIntStateOf(0) }
    var focusedCardId by remember { mutableStateOf<LauncherCardId?>(null) }
    val reconciledFocusedCardId =
        rememberReconciledFocusedCardId(controller, stackKey, cardIds, focusedCardId) { id -> focusedCardId = id }
    val focusState = CardStackFocusState(stackKey, reconciledFocusedCardId)
    val activeCardIndex = cardIds.indexOf(focusState.focusedCardId).takeIf { index -> index >= 0 } ?: 0
    val focusedCard = cards.getOrNull(activeCardIndex)
    LaunchedEffect(focusedCard?.content?.id) {
        focusedCardId = focusedCard?.content?.id
    }
    val detailFocusRequester = remember { FocusRequester() }
    var restoreDetailFocusForCardId by remember { mutableStateOf<LauncherCardId?>(null) }
    LaunchedEffect(cardIds) {
        if (restoreDetailFocusForCardId !in cardIds) restoreDetailFocusForCardId = null
    }

    fun navigate(direction: CardStackNavigationDirection): Boolean {
        val result = controller.navigate(focusState, cardIds, direction)
        if (result is CardStackFocusResult.Applied) {
            if (result.focusChanged) settleTransitionId++
            focusedCardId = result.state.focusedCardId
            return !result.boundaryReached
        }
        return false
    }

    if (focusedCard == null) {
        AdaptiveStageAllNotificationsEmptyState(modifier = modifier)
        return
    }
    val activeCard = focusedCard

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveAdaptiveStageCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                )
            }
        val isDetailVisible = detailState.expansionState.isVisible
        // Siblings stay composed (and thus discoverable/re-focusable) but dimmed while a card's
        // detail is expanded, instead of being torn down entirely -- same treatment as
        // AdaptiveStageNotificationStack.
        val stackDimFactor = if (isDetailVisible) ADAPTIVE_STAGE_SIBLING_DIM_FACTOR else 1f
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CardStack(
                        // CardStack's own root has no size of its own -- give it the same bounded
                        // size as this Box (rather than leaving it to size from its
                        // graphicsLayer-positioned, layout-wise-tiny children), so its
                        // .clipToBounds() clips against the real allotted area instead of a
                        // single card's footprint. Mirrors AdaptiveStageStageRail's fix for the
                        // same failure mode.
                        modifier = Modifier.matchParentSize(),
                        entries =
                            adaptiveStageNotificationStackEntries(
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
                                                focusedCardId = result.state.focusedCardId
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
                                                focusedCardId = result.state.focusedCardId
                                            }
                                        }
                                },
                                onSettleHaptic = {
                                    haptics.adaptiveStageSettle(
                                        state.launcherSettings.cards.adaptiveStageAppearance.motion.hapticStrength,
                                    )
                                },
                                onNavigate = ::navigate,
                                onExpand = { detailState.expand(activeCard.content.id) },
                            ),
                    ) { entry, cardModifier ->
                        val card = cards[entry.cardIndex]
                        val cardStageId = cardStages[entry.cardIndex].id
                        val identity = remember(cardStageId, state) { stageAppIdentity(cardStageId, state) }
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
                                        "Focused ${adaptiveStageCardKindLabel(card)} card: ${card.title}. ${card.text}"
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
                        AdaptiveStageCardSurface(
                            appearance = state.launcherSettings.cards.adaptiveStageAppearance,
                            background =
                                AdaptiveStageCardBackground(
                                    artwork = artwork,
                                    appSeed = cardStageId.packageName.value,
                                    appColor = appColor,
                                ),
                            modifier =
                                cardModifier.size(
                                    width = resolution.cardWidthDp.dp,
                                    height = resolution.cardHeightDp.dp,
                                ).then(focusedCardSemantics),
                            contentPadding = adaptiveStageResolvedContentPadding(resolution),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stageLabel(cardStageId, state), style = MaterialTheme.typography.labelMedium)
                                Text(card.title, style = MaterialTheme.typography.titleMedium)
                                Text(card.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                AdaptiveStageCardPositionIndicator(position = activeCardIndex + 1, count = cards.size)
                AdaptiveStageContextShelf(
                    card = activeCard,
                    onAction = onAction,
                    onDetailRequested = { detailState.expand(activeCard.content.id) },
                    detailFocusRequester = detailFocusRequester,
                    restoreDetailFocus = restoreDetailFocusForCardId == activeCard.content.id,
                    onDetailFocusRestored = { restoreDetailFocusForCardId = null },
                )
                AdaptiveStageDetailRecoveryMessage(detailState.sourceRemovalMessage)
            }
            if (isDetailVisible) {
                cards
                    .firstOrNull { card -> card.content.id == detailState.expansionState.cardId }
                    ?.let { card ->
                        AdaptiveStageCardDetailSurface(
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

@Composable
private fun AdaptiveStageAllNotificationsEmptyState(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No notifications yet",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Keeps every active notification reachable even when the visual stack depth is smaller. */
internal fun adaptiveStageNotificationStackEntries(
    resolution: AdaptiveStageCardStackResolution,
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
private fun AdaptiveStageCardPositionIndicator(
    position: Int,
    count: Int,
) {
    // Drag/fling on the stack itself is the only navigation surface now -- see
    // AdaptiveStageCardNavigationControls' removal -- this just reports where the focused
    // card sits, matching the "Previous card"/"Next card" TalkBack custom actions attached
    // to the focused card, which remain the accessible navigation path.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Card $position of $count",
            modifier =
                Modifier.semantics {
                    contentDescription = "Focused card position"
                    stateDescription = "Card $position of $count"
                },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * A minimal chronological rail beside the card stack: one dot per card, positioned by relative
 * recency (not just index order) between the oldest and newest visible card, tappable to jump
 * focus directly to that card. Deliberately simple for a first version -- no cross-app
 * aggregation and no drag-to-scrub gesture yet, both left as later follow-ups.
 */
@Composable
private fun AdaptiveStageCardTimelineRail(
    cards: List<AppStageNotificationCard>,
    activeCardIndex: Int,
    onCardSelected: (LauncherCardId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.size <= 1) return
    val timestamps = cards.map { card -> card.content.meaningfulActivityAtEpochMillis }
    val newest = timestamps.max()
    val span = (newest - timestamps.min()).coerceAtLeast(1L)
    BoxWithConstraints(modifier = modifier.width(ADAPTIVE_STAGE_TIMELINE_RAIL_WIDTH_DP.dp).fillMaxHeight()) {
        val dotSize = ADAPTIVE_STAGE_TIMELINE_DOT_DP.dp
        val focusedDotSize = ADAPTIVE_STAGE_TIMELINE_FOCUSED_DOT_DP.dp
        cards.forEachIndexed { index, card ->
            val recencyFraction = (newest - card.content.meaningfulActivityAtEpochMillis).toFloat() / span.toFloat()
            val isFocused = index == activeCardIndex
            val size = if (isFocused) focusedDotSize else dotSize
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (maxHeight - size) * recencyFraction)
                        .size(size)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                        )
                        .clickable(onClickLabel = "Jump to ${card.title}") { onCardSelected(card.content.id) }
                        .testTag("adaptive-stage-timeline-dot-${card.content.id.value}"),
            )
        }
    }
}

private const val ADAPTIVE_STAGE_TIMELINE_RAIL_WIDTH_DP = 24
private const val ADAPTIVE_STAGE_TIMELINE_DOT_DP = 6
private const val ADAPTIVE_STAGE_TIMELINE_FOCUSED_DOT_DP = 10

/**
 * Renders a notification's individual messages (sender + snippet, most recent last) when the
 * source notification carried per-message history, falling back to the plain text summary
 * otherwise -- most notifications (and every group-summary notification) have no per-message
 * history to show.
 */
@Composable
internal fun AdaptiveStageCardMessageBody(
    card: AppStageNotificationCard,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (card.messages.isEmpty()) {
        Text(card.text, style = style)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        card.messages.takeLast(ADAPTIVE_STAGE_CARD_VISIBLE_MESSAGE_COUNT).forEach { message ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveStageMessageAvatar(sender = message.sender)
                Column {
                    Text(message.sender, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(message.text, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AdaptiveStageMessageAvatar(sender: String) {
    val initial = sender.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(adaptiveStageMessageAvatarColor(sender)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

private fun adaptiveStageMessageAvatarColor(seed: String): Color {
    val hue = (seed.hashCode().toUInt().toLong() % 360L).toFloat()
    return Color.hsv(hue, 0.46f, 0.72f)
}

private const val ADAPTIVE_STAGE_CARD_VISIBLE_MESSAGE_COUNT = 2

@Composable
internal fun AdaptiveStageContextShelf(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
    onDetailRequested: (() -> Unit)? = null,
    detailFocusRequester: FocusRequester? = null,
    restoreDetailFocus: Boolean = false,
    onDetailFocusRestored: (() -> Unit)? = null,
) {
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
            AdaptiveStageContextActionButton(
                label = action.label(),
                onClick = {
                    onAction(LauncherShellAction.PerformNotificationStageAction(card.notificationKey, action))
                },
            )
        }
        onDetailRequested?.let { requestDetail ->
            AdaptiveStageContextActionButton(
                label = "Details",
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
            )
        }
        NotificationHideMenuButton(card = card, onAction = onAction)
    }
}

/**
 * A per-card overflow menu offering durable "hide notifications like this" rules, built from the
 * card's own content rather than freeform authoring -- mirrors the "Calm" reference app's
 * contextual rule-creation UX.
 */
@Composable
private fun NotificationHideMenuButton(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val stageId = card.content.stageId

    fun addRule(
        kind: NotificationHideRule.Kind,
        value: String = "",
        matchMode: NotificationHideRule.MatchMode = NotificationHideRule.MatchMode.EXACT,
    ) {
        expanded = false
        onAction(
            LauncherShellAction.AddNotificationHideRule(
                packageName = stageId.packageName,
                profileId = stageId.profileId,
                kind = kind,
                value = value,
                matchMode = matchMode,
            ),
        )
    }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Hide notifications like this" },
        ) {
            Text(text = "⋮")
        }
        RiffleContextMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Hide all notifications from this app") },
                onClick = { addRule(NotificationHideRule.Kind.APP) },
            )
            if (card.title.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Hide notifications with this title") },
                    onClick = { addRule(NotificationHideRule.Kind.TITLE, card.title) },
                )
                NotificationHideRule.generalizeNumbers(card.title)?.let { pattern ->
                    DropdownMenuItem(
                        text = { Text("Hide similar notifications (title)") },
                        onClick = {
                            addRule(NotificationHideRule.Kind.TITLE, pattern, NotificationHideRule.MatchMode.WILDCARD)
                        },
                    )
                }
            }
            if (card.text.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text("Hide notifications with this content") },
                    onClick = { addRule(NotificationHideRule.Kind.BODY, card.text) },
                )
                NotificationHideRule.generalizeNumbers(card.text)?.let { pattern ->
                    DropdownMenuItem(
                        text = { Text("Hide similar notifications (content)") },
                        onClick = {
                            addRule(NotificationHideRule.Kind.BODY, pattern, NotificationHideRule.MatchMode.WILDCARD)
                        },
                    )
                }
            }
            if (card.title.isBlank() && card.text.isBlank()) {
                DropdownMenuItem(
                    text = { Text("Hide empty notifications from this app") },
                    onClick = { addRule(NotificationHideRule.Kind.EMPTY_CONTENT) },
                )
            }
        }
    }
}

/**
 * A pill-shaped, translucent "glass" action button -- the shared building block for
 * [AdaptiveStageContextShelf]'s inline row and [AdaptiveStageContextActionsGrid]'s two-column layout,
 * styled to sit closer to Calm's `contextActionButton()` glass-pill treatment than a bare
 * [TextButton].
 */
@Composable
private fun AdaptiveStageContextActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Built on TextButton (not a raw clickable Surface) so click routing keeps the same proven
    // semantics/hit-testing behavior as every other action button in this file. The container is
    // near-opaque with a real shadow -- at the previous 0.55f alpha and no elevation, these pills
    // were nearly unreadable over a busy wallpaper.
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Two-column action grid mirroring Calm's `FocusOverlayController.actionsGrid()`: actions are
 * chunked by 2, and a lone trailing action spans the full row width instead of being stranded
 * beside empty space.
 */
@Composable
internal fun AdaptiveStageContextActionsGrid(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
    onDetailRequested: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val actions = card.supportedActions.sortedBy { action -> action.label() }
    if (actions.isEmpty() && onDetailRequested == null) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { rowActions ->
            if (rowActions.size == 1) {
                AdaptiveStageContextActionButton(
                    label = rowActions[0].label(),
                    onClick = {
                        onAction(
                            LauncherShellAction.PerformNotificationStageAction(card.notificationKey, rowActions[0]),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowActions.forEach { action ->
                        AdaptiveStageContextActionButton(
                            label = action.label(),
                            onClick = {
                                onAction(
                                    LauncherShellAction.PerformNotificationStageAction(card.notificationKey, action),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        onDetailRequested?.let { requestDetail ->
            AdaptiveStageContextActionButton(
                label = "Details",
                onClick = requestDetail,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun AdaptiveStageEmptyStage(
    stage: AppStage,
    shellState: com.riffle.app.launcher.notifications.AppStageShellState,
    detailState: AdaptiveStageCardDetailState,
    showDetailInline: Boolean,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    val notificationAccessStatus = state.notificationAccessStatus
    val emptyCard = shellState.emptyAppCards[stage.id]
    val detailCardId = adaptiveStageEmptyDetailCardId(stage.id)
    val availableCardIds = if (emptyCard == null) emptySet() else setOf(detailCardId)
    val detailFocusRequester = remember { FocusRequester() }
    var restoreDetailFocusForCardId by remember { mutableStateOf<LauncherCardId?>(null) }
    var detailControlLaidOut by remember { mutableStateOf(false) }
    LaunchedEffect(availableCardIds) {
        detailState.reconcile(availableCardIds)
        if (restoreDetailFocusForCardId !in availableCardIds) restoreDetailFocusForCardId = null
    }
    if (detailState.expansionState.isVisible && showDetailInline && emptyCard != null) {
        AdaptiveStageEmptyAppDetailSurface(
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
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport) {
                state.launcherSettings.resolveAdaptiveStageCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                )
            }
        AdaptiveStageCardSurface(
            appearance = state.launcherSettings.cards.adaptiveStageAppearance,
            background =
                AdaptiveStageCardBackground(appSeed = stage.id.packageName.value, appColor = emptyStageAppColor),
            modifier =
                Modifier
                    .size(width = resolution.cardWidthDp.dp, height = resolution.cardHeightDp.dp)
                    .testTag(ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG),
            contentPadding = adaptiveStageResolvedContentPadding(resolution),
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
                        text = notificationAccessStatus.adaptiveStageAccessMessage,
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
                AdaptiveStageDetailRecoveryMessage(detailState.sourceRemovalMessage)
            }
        }
    }
}

internal const val ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG = "adaptive-stage-empty-stage-card"

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
private fun AdaptiveStageUnavailableState(
    access: NotificationAccessStatus,
    recoveryMessage: String?,
    installedApps: List<com.riffle.core.domain.launcher.apps.InstalledApp>,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = access.adaptiveStageAccessMessage,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
        )
        AdaptiveStageDetailRecoveryMessage(recoveryMessage)
        if (access == NotificationAccessStatus.NOT_GRANTED || access == NotificationAccessStatus.REVOKED) {
            AdaptiveStageContextActionButton(
                label = "Allow access",
                onClick = { onAction(LauncherShellAction.RequestNotificationAccess) },
            )
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
                    AdaptiveStageContextActionButton(
                        label = "Pin ${app.label}",
                        onClick = {
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
}

private val NotificationAccessStatus.adaptiveStageAccessMessage: String
    get() =
        when (this) {
            NotificationAccessStatus.GRANTED -> "No active stages yet. New notifications will appear here."
            NotificationAccessStatus.NOT_GRANTED -> "Allow notification access to show your app stages."
            NotificationAccessStatus.REVOKED -> "Notification access was revoked. Restore access to update stages."
            NotificationAccessStatus.UNKNOWN -> "Checking notification access."
        }

private data class AdaptiveStageDetailOrigin(
    val stageKey: String?,
    val cardId: LauncherCardId,
)

private fun adaptiveStageEmptyDetailCardId(stageId: AppStageId): LauncherCardId =
    LauncherCardId("stage-empty:${stageId.profileId.value}:${stageId.packageName.value}")

internal fun adaptiveStageStageKey(stageId: AppStageId): String {
    return "${stageId.profileId.value}:${stageId.packageName.value}"
}

internal const val ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG = "adaptive-stage-supporting-pane"

private data class AdaptiveStageSafeInsetsDp(
    val start: Int,
    val top: Int,
    val end: Int,
    val bottom: Int,
)

/** Converts full-window hinge coordinates into the inset content coordinates used by the surface. */
private fun AdaptiveStageWindowLayout.insetLocal(insets: AdaptiveStageSafeInsetsDp): AdaptiveStageWindowLayout =
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

private fun AdaptiveStageWindowLayout.hasUsableBounds(): Boolean = widthDp > 0 && heightDp > 0

private val AdaptiveStagePostureTransitionStateSaver =
    Saver<AdaptiveStagePostureTransitionState, List<String>>(
        save = { state -> listOf(state.settledPosture.name, state.pendingPosture?.name.orEmpty()) },
        restore = { saved ->
            val settled = saved.getOrNull(0)?.let(::adaptiveStagePostureOrNull) ?: AdaptiveStagePosture.UNKNOWN
            val pending = saved.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::adaptiveStagePostureOrNull)
            AdaptiveStagePostureTransitionState(settled, pending)
        },
    )

private fun adaptiveStagePostureOrNull(value: String): AdaptiveStagePosture? {
    return runCatching { AdaptiveStagePosture.valueOf(value) }.getOrNull()
}

/**
 * A slim, always-centered-on-selection horizontal strip synced to the pager's fractional
 * [pagePosition] -- mirroring the reference app's chapter-carousel concept with Compose idioms:
 * each item's alpha/scale is a function of its distance from [pagePosition], so it visually tracks
 * an in-progress drag rather than jumping only when a page fully settles. Evolves the previous
 * static [AdaptiveStageStageSelector] in place rather than adding a redundant second control; its
 * Previous/Next buttons remain for non-drag (keyboard, switch, accessibility) navigation.
 */
@Composable
private fun AdaptiveStageStageSpine(
    stages: List<AppStage>,
    selectedStageId: AppStageId?,
    pagePosition: Float,
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    if (stages.isEmpty()) return
    // Previous/Next were removed as redundant with tapping a chip directly (or swiping, via
    // AdaptiveStageStagePager) -- AdaptiveStageStageHeader's customActions cover non-touch navigation.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stages, key = ::adaptiveStageStageSelectorItemKey) { stage ->
                val index = stages.indexOf(stage)
                val proximity = 1f - abs(index - pagePosition).coerceIn(0f, 1f)
                val itemAlpha = ADAPTIVE_STAGE_SPINE_MIN_ALPHA + (1f - ADAPTIVE_STAGE_SPINE_MIN_ALPHA) * proximity
                val itemScale = ADAPTIVE_STAGE_SPINE_MIN_SCALE + (1f - ADAPTIVE_STAGE_SPINE_MIN_SCALE) * proximity
                val identity = stageAppIdentity(stage.id, state)
                val label = stageLabel(stage.id, state)
                TextButton(
                    onClick = { onAction(LauncherShellAction.SelectAppStage(stage.id)) },
                    shape = RoundedCornerShape(percent = 50),
                    colors =
                        ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private const val ADAPTIVE_STAGE_SPINE_MIN_ALPHA = 0.45f
private const val ADAPTIVE_STAGE_SPINE_MIN_SCALE = 0.85f

/** Lazy layouts require item keys that Android can store in a Bundle across recreation. */
internal fun adaptiveStageStageSelectorItemKey(stage: AppStage): String {
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

/**
 * A one-line preview of a stage's most recent card for the rail tile (#1059). [text]/[title] are
 * already redaction-resolved by [appStageNotificationCards] ("Content hidden for this profile" /
 * "Hidden notification" when the profile is locked/quiet), so this never needs its own privacy
 * check -- it just picks whichever field actually has content, preferring the message body.
 */
private fun AppStageNotificationCard.railSnippet(): String? =
    text.takeIf(String::isNotBlank) ?: title.takeIf(String::isNotBlank)

private fun AppStage.adaptiveStageStageStateDescription(): String =
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

private fun adaptiveStageCardKindLabel(card: AppStageNotificationCard): String =
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
