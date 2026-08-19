@file:Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.notifications.AndroidNotificationStageActionGateway
import com.riffle.app.launcher.notifications.AppStageEmptyAppCard
import com.riffle.app.launcher.notifications.AppStageNotificationCard
import com.riffle.app.launcher.notifications.AppStageShellState
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
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackResolution
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp
import com.riffle.core.domain.launcher.settings.ThreadMessageOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Cards mode reuses the persisted home-gesture bindings, but only lets a subset of actions
 * through: stage navigation, leaving Cards, and reaching the app drawer/search so Cards mode stays
 * a normal, discoverable overlay rather than an isolated static surface.
 */
internal fun adaptiveStageAppStageActionFilter(action: LauncherShellAction): Boolean =
    when (action) {
        LauncherShellAction.SelectNextAppStage,
        LauncherShellAction.SelectPreviousAppStage,
        LauncherShellAction.ExitAdaptiveStage,
        LauncherShellAction.OpenAppDrawer,
        LauncherShellAction.OpenSearch,
        -> true

        else -> false
    }

/**
 * `null` [configuredDockPosition] means the user has never chosen a dock edge for this layout, so
 * the active template's [templateDockPosition] applies; once the user picks an edge in settings it
 * always wins, matching how every other explicit user preference in this file overrides its
 * template default.
 */
internal fun resolveDockPosition(
    configuredDockPosition: DockPosition?,
    templateDockPosition: DockPosition?,
): DockPosition = configuredDockPosition ?: templateDockPosition ?: DockPosition.LEADING

/**
 * Mirrors [resolveDockPosition]'s shape: the pane arrangement is a plain configured user
 * preference today, with no template or device override to reconcile against yet.
 */
@Suppress("MaxLineLength")
internal fun resolveAdaptiveStagePaneArrangement(value: AdaptiveStagePaneArrangement): AdaptiveStagePaneArrangement =
    value

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
    shellState: AppStageShellState = rememberAppStageShellState(state),
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
    // The "All notifications" page merges content across every real stage (see #1056/#1057), so it
    // deliberately isn't a real AppStageId and never touches AppStagePlanner, LauncherShellAction's
    // stage-selection reducer, or persisted preferences the way a real stage selection does.
    // Selecting a real stage through any of the existing paths (SelectAppStage/prev/next/tap)
    // implicitly leaves this page, since those all flow through the callback below that clears it.
    //
    // It lives in the hoisted interaction context rather than in a rememberSaveable here: this
    // surface is torn down whenever the launcher shows something else, so held locally the page was
    // the one thing about "what you were looking at" that a trip to settings threw away.
    // Mirrored from the context rather than read straight off it, the way every other piece of
    // interaction state in this surface already is. [context] and [onContextChanged] are both
    // defaulted, so a caller is free not to hoist -- and read directly, a caller that did not would
    // have a page it could never reach, because its writes would go to a callback that does
    // nothing. The effect below is what a caller that *does* hoist buys: the dock's merged-page
    // entry sets the context from outside this surface, and this has to notice.
    var allNotificationsSelectedValue by rememberSaveable { mutableStateOf(context.allNotificationsSelected) }
    LaunchedEffect(context.allNotificationsSelected) {
        allNotificationsSelectedValue = context.allNotificationsSelected
    }
    val allNotificationsSelected = allNotificationsSelectedValue
    val onAllNotificationsSelectedChanged: (Boolean) -> Unit = { selected ->
        allNotificationsSelectedValue = selected
        onContextChanged(context.copy(allNotificationsSelected = selected))
    }
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
            val paneArrangement =
                resolveAdaptiveStagePaneArrangement(value = state.launcherSettings.cards.adaptiveStagePaneArrangement)
            val paneLayout =
                remember(adaptiveWindow, postureTransition.effectivePosture, paneArrangement) {
                    AdaptiveStagePaneLayoutPolicy().layoutFor(
                        window = adaptiveWindow.copy(posture = postureTransition.effectivePosture),
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
                            onAllNotificationsSelectedChanged = onAllNotificationsSelectedChanged,
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
                            onAllNotificationsSelectedChanged = onAllNotificationsSelectedChanged,
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
                        Row(modifier = Modifier.fillMaxSize()) {
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
                                    // TWO_PANE/THREE_PANE is the unfolded presentation, so the
                                    // stack uses the unfolded appearance profile.
                                    useUnfoldedAppearance = true,
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

internal fun List<AppStage>.withAllNotificationsPage(include: Boolean = true): List<AdaptiveStagePage> =
    map(AdaptiveStagePage::Stage) + listOfNotNull(AdaptiveStagePage.AllNotifications.takeIf { include })

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
    val showAllNotifications = state.launcherSettings.cards.foldedShowAllNotifications
    val pages = remember(stages, showAllNotifications) { stages.withAllNotificationsPage(showAllNotifications) }
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
    val showAllNotifications = state.launcherSettings.cards.foldedShowAllNotifications
    val pages = remember(stages, showAllNotifications) { stages.withAllNotificationsPage(showAllNotifications) }
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
 * Pages between stages with Compose Foundation's `HorizontalPager`, mirroring
 * [ImmediateWorkspacePager]'s approach for Standard Home's own pages. The selected page always
 * renders the full interactive [AdaptiveStagePageBody]; every other page -- including one mid-slide
 * during a drag, before the gesture settles and [selectedPageIndex] catches up -- renders the
 * lighter [AdaptiveStageNeighborPage] instead, so only one stage ever mounts the expensive
 * interactive detail/card-stack surface at a time. With zero or one stage there is nothing to page
 * between, so this falls back to rendering [AdaptiveStageStageBody] directly without a pager.
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
            // This pager is always the compact (folded) presentation -- see its own doc.
            useUnfoldedAppearance = false,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = modifier,
        )
        return
    }

    val reducedMotion = state.launcherSettings.motion.reducedMotion
    HorizontalPager(
        state = pagerState.foundationPagerState,
        modifier = modifier.fillMaxSize(),
        flingBehavior =
            PagerDefaults.flingBehavior(
                state = pagerState.foundationPagerState,
                snapAnimationSpec = adaptiveStageStageSettleAnimation(homePageSettleMotionPolicy(reducedMotion)),
                snapPositionalThreshold = STAGE_CHANGE_DISTANCE_THRESHOLD,
            ),
        key = { index -> adaptiveStagePageKey(pages[index]) },
    ) { index ->
        val page = pages[index]
        val stageModifier = Modifier.fillMaxSize()
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
                // This pager is always the compact (folded) presentation -- see its own doc.
                useUnfoldedAppearance = false,
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
    // Which of CardsSettings' two independently configurable appearance profiles the rendered
    // card stack itself should use -- true in the TWO_PANE/THREE_PANE (docked-rail) branch, false
    // everywhere else (the compact drag pager, used by both COMPACT and SPLIT). No default: every
    // caller must decide deliberately rather than silently inherit one profile everywhere, which
    // is exactly the bug this parameter fixes (every card stack rendering the folded profile
    // regardless of pane mode, so the "Unfolded" appearance editor target had no visible effect
    // outside the rail's own tiles).
    useUnfoldedAppearance: Boolean,
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
                useUnfoldedAppearance = useUnfoldedAppearance,
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
                useUnfoldedAppearance = useUnfoldedAppearance,
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
        // Neighbor pages only ever exist within the compact drag pager -- see its own doc.
        useUnfoldedAppearance = false,
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
    useUnfoldedAppearance: Boolean,
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
            stage = selectedStage,
            state = state,
            shellState = shellState,
            detailState = requireNotNull(detailState),
            focusedCardId = focusedCardId,
            onDetailVisibilityChanged = onDetailVisibilityChanged,
            onFocusedCardChanged = onFocusedCardChanged,
            showDetailInline = showDetailInline,
            useUnfoldedAppearance = useUnfoldedAppearance,
            onAction = onAction,
            appIconLoader = appIconLoader,
            modifier = modifier,
        )
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
                    // Cards mode's own card stack fills the whole touch area with its own
                    // drag/tap gesture handling (see CardStack.kt's Modifier.scrollable +
                    // cardStackTapToFocus), unlike Standard grid mode's HomeBackgroundContextMenu,
                    // which sits behind genuinely empty
                    // grid cells and is always reachable by a long-press there. Cards mode never
                    // wires up an equivalent background handler, so without this entry the only
                    // way to reach Settings from Cards mode is whatever empty space happens to
                    // exist around the dock -- absent entirely on some layouts (reported: a full
                    // folded-phone dock left no empty space to long-press, forcing a switch to
                    // the roomier unfolded layout just to reach Settings). This overflow menu is
                    // already reliably reachable whenever a stage is selected, so it's the
                    // lowest-risk place to guarantee a path to Settings without touching
                    // CardStack's own gesture handling.
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            overflowExpanded = false
                            onAction(LauncherShellAction.OpenSettings)
                        },
                    )
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
    useUnfoldedAppearance: Boolean,
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
                useUnfoldedAppearance = useUnfoldedAppearance,
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
    useUnfoldedAppearance: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
    /**
     * True for the real, top-level stack: a card that's one message among several sharing the
     * same conversation hides its own Dismiss/Reply/etc. in favor of a "View thread" action that
     * groups them. False for the thread view itself (an [AdaptiveStageNotificationStack] recursively
     * composed over just one conversation's own cards) -- there every card's actions show normally,
     * and there's nothing further to group into.
     */
    groupActionsByThread: Boolean = true,
) {
    val cardAppearance =
        if (useUnfoldedAppearance) {
            state.launcherSettings.cards.unfoldedAppearance
        } else {
            state.launcherSettings.cards.adaptiveStageAppearance
        }
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
    // How many cards the most recent settle-triggered focus change moved by -- see
    // CardStackInteraction.settleStepCount's own doc for why this drives the settle animation's
    // duration. Not worth persisting across process death (an in-flight settle animation doesn't
    // survive that anyway), unlike settleTransitionId above.
    var settleStepCount by remember(stage.id) { mutableIntStateOf(1) }
    val reconciledFocusedCardId =
        rememberReconciledFocusedCardId(controller, stackKey, cardIds, focusedCardId, onFocusedCardChanged)
    val focusState = CardStackFocusState(stackKey, reconciledFocusedCardId)
    val activeCardIndex = cardIds.indexOf(focusState.focusedCardId).takeIf { index -> index >= 0 } ?: 0
    val focusedCard = cards.getOrNull(activeCardIndex)
    val activeCard = focusedCard ?: return
    val detailFocusRequester = remember { FocusRequester() }
    var restoreDetailFocusForCardId by remember { mutableStateOf<LauncherCardId?>(null) }

    // Ephemeral, not part of durable LauncherShellState -- browsing intent only, never persisted
    // or restored, the same treatment as the settings-preview overlay's own local state.
    var threadFocusNotificationKey by remember(stage.id) { mutableStateOf<LauncherNotificationKey?>(null) }
    val threadCards =
        remember(cards, threadFocusNotificationKey) {
            threadFocusNotificationKey?.let { key -> cards.filter { card -> card.notificationKey == key } }
                .orEmpty()
        }
    // A conversation's cards can shrink to one (or zero) as notifications update -- close the
    // thread view rather than leaving it open over a group that no longer justifies grouping.
    val isThreadVisible = threadCards.size > 1

    LaunchedEffect(activeCard.content.id) {
        onFocusedCardChanged(activeCard.content.id)
    }
    LaunchedEffect(cardIds) {
        if (restoreDetailFocusForCardId !in cardIds) restoreDetailFocusForCardId = null
        if (threadCards.size <= 1) threadFocusNotificationKey = null
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

    // Non-null only while a live drag is in progress on this stack's own settle axis (see
    // CardStack's CardStackInteraction.onLiveDrag doc) -- the raw signed pixel delta reported this
    // frame. Converted below into a fractional activeIndex so every entry's own pose continuously
    // reflows toward its neighbor as the drag progresses, the same way the reference "Calm"
    // launcher's card stack works, instead of only updating once the drag settles.
    var liveDragPx by remember(stage.id) { mutableStateOf<Float?>(null) }
    val liveActiveCardIndex =
        adaptiveStageLiveActiveCardIndex(activeCardIndex, cards.size, liveDragPx)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport, useUnfoldedAppearance) {
                cardAppearance.resolveCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                    globalReducedMotion = state.launcherSettings.motion.reducedMotion,
                )
            }
        val isDetailVisible = detailState.expansionState.isVisible && showDetailInline
        // Siblings stay composed (and thus discoverable/re-focusable) but dimmed while a card's
        // detail is expanded, instead of being torn down entirely.
        val stackDimFactor = if (isDetailVisible) ADAPTIVE_STAGE_SIBLING_DIM_FACTOR else 1f
        // The whole stage -- stack, shelf, position indicator -- recedes as one unit behind the
        // thread view rather than just dimming per-card, so it reads as pulled back, not merely
        // faded: the same idea as [stackDimFactor] one level up, applied with a real scale instead
        // of alpha alone.
        val threadRecedeScale by
            animateFloatAsState(
                targetValue = if (isThreadVisible) ADAPTIVE_STAGE_THREAD_RECEDE_SCALE else 1f,
                label = "adaptive-stage-thread-recede-scale",
            )
        val threadRecedeAlpha by
            animateFloatAsState(
                targetValue = if (isThreadVisible) ADAPTIVE_STAGE_THREAD_RECEDE_ALPHA else 1f,
                label = "adaptive-stage-thread-recede-alpha",
            )
        Box(
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = threadRecedeScale
                    scaleY = threadRecedeScale
                    alpha = threadRecedeAlpha
                },
        ) {
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
                            // single card's footprint.
                            modifier = Modifier.matchParentSize(),
                            entries =
                                adaptiveStageNotificationStackEntries(
                                    resolution = resolution,
                                    cardCount = cards.size,
                                    activeCardIndex = liveActiveCardIndex,
                                ),
                            animationSpec = resolution.animation,
                            reducedMotion = resolution.reducedMotion,
                            stackPeakFraction = resolution.stackPeakFraction,
                            itemKey = { entry -> cards[entry.cardIndex].content.id },
                            dimFactor = stackDimFactor,
                            interaction =
                                CardStackInteraction(
                                    focusedItemKey = activeCard.content.id,
                                    settleTransitionId = settleTransitionId,
                                    settleStepCount = settleStepCount,
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
                                        val previousIndex = activeCardIndex
                                        controller
                                            .settle(
                                                focusState,
                                                cardIds,
                                                CardStackSettleRequest(
                                                    focusedCardId = activeCard.content.id,
                                                    verticalDragPx = drag,
                                                    verticalVelocityPxPerSecond = velocity,
                                                    distanceThresholdPx =
                                                    ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                                    flingVelocityThresholdPxPerSecond = 500f,
                                                ),
                                            ).let { result ->
                                                if (result is CardStackFocusResult.Applied) {
                                                    if (result.state.focusedCardId != focusState.focusedCardId) {
                                                        settleTransitionId++
                                                        val newIndex = cardIds.indexOf(result.state.focusedCardId)
                                                        if (newIndex >= 0) {
                                                            settleStepCount =
                                                                abs(newIndex - previousIndex).coerceAtLeast(1)
                                                        }
                                                    }
                                                    onFocusedCardChanged(result.state.focusedCardId)
                                                }
                                            }
                                    },
                                    onSettleHaptic = {
                                        haptics.adaptiveStageSettle(cardAppearance.motion.hapticStrength)
                                    },
                                    onNavigate = ::navigate,
                                    onExpand = { detailState.expand(activeCard.content.id) },
                                    onLiveDrag = { dragPx -> liveDragPx = dragPx },
                                    // Carries the release velocity onward as real momentum instead
                                    // of stopping dead and animating to a freshly-picked card; the
                                    // per-card distance matches the settle threshold above so the
                                    // magnetized position onSettle receives lands on exact card
                                    // boundaries. See CardStackScroll.
                                    scroll =
                                        CardStackScroll(
                                            cardCount = cards.size,
                                            activeCardIndex = activeCardIndex,
                                            distancePerCardPx =
                                            ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                            magnet = resolution.magnet,
                                        ),
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
                                appearance = cardAppearance,
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
                }
                AdaptiveStageCardPositionIndicator(position = activeCardIndex + 1, count = cards.size)
                val isActiveCardThreaded =
                    groupActionsByThread &&
                        cards.count { card -> card.notificationKey == activeCard.notificationKey } > 1
                AdaptiveStageContextShelf(
                    card = activeCard,
                    onAction = onAction,
                    onDetailRequested = { detailState.expand(activeCard.content.id) },
                    detailFocusRequester = detailFocusRequester,
                    restoreDetailFocus = restoreDetailFocusForCardId == activeCard.content.id,
                    onDetailFocusRestored = { restoreDetailFocusForCardId = null },
                    showNotificationActions = !isActiveCardThreaded,
                    onViewThread =
                        if (isActiveCardThreaded) {
                            { threadFocusNotificationKey = activeCard.notificationKey }
                        } else {
                            null
                        },
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
            if (isThreadVisible) {
                AdaptiveStageThreadSurface(
                    stage = stage,
                    state = state,
                    threadCards = threadCards,
                    showDetailInline = showDetailInline,
                    useUnfoldedAppearance = useUnfoldedAppearance,
                    onAction = onAction,
                    appIconLoader = appIconLoader,
                    onClose = { threadFocusNotificationKey = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

internal const val ADAPTIVE_STAGE_SIBLING_DIM_FACTOR = 0.18f
internal const val ADAPTIVE_STAGE_BACKDROP_SCRIM_ALPHA = 0.16f
internal const val ADAPTIVE_STAGE_THREAD_RECEDE_SCALE = 0.92f
internal const val ADAPTIVE_STAGE_THREAD_RECEDE_ALPHA = 0.55f

/**
 * A conversation's message cards, pulled forward as their own small stack in front of the main
 * one (which recedes behind it via [ADAPTIVE_STAGE_THREAD_RECEDE_SCALE]/[ADAPTIVE_STAGE_THREAD_RECEDE_ALPHA]),
 * instead of a full-bleed takeover. Reuses [AdaptiveStageNotificationStack] itself rather than a
 * second hand-rolled rendering path: a synthetic [AppStage] scoped to just [threadCards] gets the
 * exact same drag/fling/settle/focus/detail/artwork machinery the real per-app stack uses, with
 * its own independent [CardStackController] and detail state (see [CardStackController]'s doc --
 * two instances sharing a key string never collide, each is scoped to its own local `remember`).
 */
@Composable
private fun AdaptiveStageThreadSurface(
    stage: AppStage,
    state: LauncherShellState,
    threadCards: List<AppStageNotificationCard>,
    showDetailInline: Boolean,
    useUnfoldedAppearance: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val threadKey = threadCards.first().notificationKey
    val orderedCards =
        remember(threadCards, state.launcherSettings.cards.threadMessageOrder) {
            when (state.launcherSettings.cards.threadMessageOrder) {
                ThreadMessageOrder.CHRONOLOGICAL ->
                    threadCards.sortedBy { card -> card.content.meaningfulActivityAtEpochMillis }
                ThreadMessageOrder.RECENT_FIRST ->
                    threadCards.sortedByDescending { card -> card.content.meaningfulActivityAtEpochMillis }
            }
        }
    val threadStage = remember(stage, orderedCards) { stage.copy(content = orderedCards.map { card -> card.content }) }
    var threadFocusedCardId by remember(threadKey) { mutableStateOf<LauncherCardId?>(null) }
    val threadDetailState =
        rememberAdaptiveStageCardDetailState(
            scopeKey = "thread:${threadKey.value}",
            motion = state.launcherSettings.cards.adaptiveStageAppearance.motion,
            globalReducedMotion = state.launcherSettings.motion.reducedMotion,
        )

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = ADAPTIVE_STAGE_BACKDROP_SCRIM_ALPHA),
        ) {}
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Thread", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) {
                    SettingsButtonText(text = "Done")
                }
            }
            AdaptiveStageNotificationStack(
                stage = threadStage,
                state = state,
                notificationCards = orderedCards,
                detailState = threadDetailState,
                focusedCardId = threadFocusedCardId,
                onFocusedCardChanged = { cardId -> threadFocusedCardId = cardId },
                showDetailInline = showDetailInline,
                useUnfoldedAppearance = useUnfoldedAppearance,
                onAction = onAction,
                appIconLoader = appIconLoader,
                groupActionsByThread = false,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

/**
 * Shared by every notification card stack's own [CardStackSettleRequest.distanceThresholdPx] and,
 * separately, its live-drag-to-fractional-index conversion (dragPx / this) -- deliberately the
 * same number for both, so the point at which the stack visually reaches "the next card is now
 * fully focused" during a live drag is exactly the point at which releasing the finger would
 * actually commit that same move.
 */
internal const val ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX = 64f

/**
 * Converts this frame's live scroll position into the fractional index [CardStack] renders from,
 * uncapped past [activeCardIndex] except by the stack's own bounds.
 *
 * This is not only a drag preview: with [CardStackScroll] wired up (as both notification stacks
 * do), the position reported here keeps moving through the momentum fling and the magnetize that
 * follows it, so this same conversion is what renders the whole continuous motion -- one
 * [ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX] of travel per card, whether the finger
 * or the fling physics produced it. [CardStackController.settle] then commits the card that
 * travel came to rest on, because the distance it is handed is the magnetized one: an exact
 * multiple of that same threshold. Only the stack's own boundary caps how far this can reach,
 * matching both the scroll's own clamp and the one [CardStackController.settle] applies.
 */
internal fun adaptiveStageLiveActiveCardIndex(
    activeCardIndex: Int,
    cardCount: Int,
    liveDragPx: Float?,
): Float =
    cardStackLiveActiveCardIndex(
        activeCardIndex = activeCardIndex,
        cardCount = cardCount,
        liveDragPx = liveDragPx,
        distancePerCardPx = ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
    )

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
    useUnfoldedAppearance: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    appIconLoader: AppIconLoader,
    modifier: Modifier,
) {
    val cardAppearance =
        if (useUnfoldedAppearance) {
            state.launcherSettings.cards.unfoldedAppearance
        } else {
            state.launcherSettings.cards.adaptiveStageAppearance
        }
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
    // See AdaptiveStageNotificationStack's identical settleStepCount for why this exists.
    var settleStepCount by remember { mutableIntStateOf(1) }
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

    // See AdaptiveStageNotificationStack's identical mechanism for why this exists and how it's
    // converted to a fractional activeIndex below.
    var liveDragPx by remember { mutableStateOf<Float?>(null) }
    val liveActiveCardIndex =
        adaptiveStageLiveActiveCardIndex(activeCardIndex, cards.size, liveDragPx)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt())
        val resolution =
            remember(state.launcherSettings, viewport, useUnfoldedAppearance) {
                cardAppearance.resolveCardStack(
                    viewport = viewport,
                    capabilities = adaptiveStageRendererCapabilities(),
                    globalReducedMotion = state.launcherSettings.motion.reducedMotion,
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
                        // single card's footprint.
                        modifier = Modifier.matchParentSize(),
                        entries =
                            adaptiveStageNotificationStackEntries(
                                resolution = resolution,
                                cardCount = cards.size,
                                activeCardIndex = liveActiveCardIndex,
                            ),
                        animationSpec = resolution.animation,
                        reducedMotion = resolution.reducedMotion,
                        stackPeakFraction = resolution.stackPeakFraction,
                        itemKey = { entry -> cards[entry.cardIndex].content.id },
                        dimFactor = stackDimFactor,
                        interaction =
                            CardStackInteraction(
                                focusedItemKey = activeCard.content.id,
                                settleTransitionId = settleTransitionId,
                                settleStepCount = settleStepCount,
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
                                    val previousIndex = activeCardIndex
                                    controller
                                        .settle(
                                            focusState,
                                            cardIds,
                                            CardStackSettleRequest(
                                                focusedCardId = activeCard.content.id,
                                                verticalDragPx = drag,
                                                verticalVelocityPxPerSecond = velocity,
                                                distanceThresholdPx =
                                                ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                                flingVelocityThresholdPxPerSecond = 500f,
                                            ),
                                        ).let { result ->
                                            if (result is CardStackFocusResult.Applied) {
                                                if (result.state.focusedCardId != focusState.focusedCardId) {
                                                    settleTransitionId++
                                                    val newIndex = cardIds.indexOf(result.state.focusedCardId)
                                                    if (newIndex >= 0) {
                                                        settleStepCount =
                                                            abs(newIndex - previousIndex).coerceAtLeast(1)
                                                    }
                                                }
                                                focusedCardId = result.state.focusedCardId
                                            }
                                        }
                                },
                                onSettleHaptic = {
                                    haptics.adaptiveStageSettle(cardAppearance.motion.hapticStrength)
                                },
                                onNavigate = ::navigate,
                                onExpand = { detailState.expand(activeCard.content.id) },
                                onLiveDrag = { dragPx -> liveDragPx = dragPx },
                                // See AdaptiveStageNotificationStack's identical scroll model.
                                scroll =
                                    CardStackScroll(
                                        cardCount = cards.size,
                                        activeCardIndex = activeCardIndex,
                                        distancePerCardPx =
                                        ADAPTIVE_STAGE_CARD_STACK_SETTLE_DISTANCE_THRESHOLD_PX,
                                        magnet = resolution.magnet,
                                    ),
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
                            appearance = cardAppearance,
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
    // Float, not Int: a caller tracking a live drag (see CardStack's own onLiveDrag doc) passes a
    // fractional position so every entry's pose continuously interpolates toward its neighbor
    // frame by frame, instead of only updating once the drag settles on a new integer index. An
    // exact integer value (the common, non-dragging case) behaves identically to before.
    activeCardIndex: Float,
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

private const val ADAPTIVE_STAGE_CARD_VISIBLE_MESSAGE_COUNT = 3

@Composable
internal fun AdaptiveStageContextShelf(
    card: AppStageNotificationCard,
    onAction: (LauncherShellAction) -> Unit,
    onDetailRequested: (() -> Unit)? = null,
    detailFocusRequester: FocusRequester? = null,
    restoreDetailFocus: Boolean = false,
    onDetailFocusRestored: (() -> Unit)? = null,
    /**
     * False while browsing a card that's one message among several sharing the same conversation
     * -- Dismiss/Reply/etc. all act on the whole conversation, not the one message currently
     * focused, so those stay hidden here in favor of [onViewThread]'s grouped view instead of
     * risking a surprising, conversation-wide action from what reads as a single message card.
     */
    showNotificationActions: Boolean = true,
    onViewThread: (() -> Unit)? = null,
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
        if (showNotificationActions) {
            card.supportedActions.sortedBy { action -> action.label() }.forEach { action ->
                AdaptiveStageContextActionButton(
                    label = action.label(),
                    onClick = {
                        onAction(LauncherShellAction.PerformNotificationStageAction(card.notificationKey, action))
                    },
                )
            }
        }
        onViewThread?.let { viewThread ->
            AdaptiveStageContextActionButton(label = "View thread", onClick = viewThread)
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
@OptIn(ExperimentalLayoutApi::class)
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
    // No card surface here -- an empty stage has no notification content to frame in one, only
    // status text and its contextual actions (open the app, its shortcuts, notification access).
    // A full card chrome (background/border/blur/shadow) for zero content read as Riffle having
    // lost track of real notifications rather than genuinely having none to show.
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // fillMaxSize() + verticalScroll (not just wrap-content) so a stage with many shortcuts
        // still fits within the stage's own bounds by scrolling, the same as the removed card
        // surface's content used to -- centered via Arrangement's CenterVertically instead of the
        // outer Box's contentAlignment, since a fillMaxSize() child always fills that regardless.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag(ADAPTIVE_STAGE_EMPTY_STAGE_CARD_TEST_TAG)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
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
                    AdaptiveStageContextActionButton(
                        label = "Allow access",
                        onClick = { onAction(LauncherShellAction.RequestNotificationAccess) },
                    )
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveStageContextActionButton(
                        label = "Open ${card.app.label}",
                        onClick = { onAction(LauncherShellAction.LaunchApp(card.app.identity)) },
                    )
                    card.shortcuts.forEach { shortcut ->
                        AdaptiveStageContextActionButton(
                            label = shortcut.shortLabel,
                            onClick = { onAction(LauncherShellAction.LaunchAppShortcut(shortcut)) },
                        )
                    }
                    AdaptiveStageContextActionButton(
                        label = "Details",
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
                    )
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

/**
 * The reconciled stage snapshot for this composition.
 *
 * The reconciler carries the previous snapshot -- that is how an empty dynamic stage survives the
 * notification that made it going away -- so there must only ever be one per surface. Anything that
 * needs the stages alongside [AdaptiveStageAppStageSurface] takes this and hands it in, rather than
 * reconciling a second time and getting a snapshot with a history of its own.
 */
@Composable
internal fun rememberAppStageShellState(state: LauncherShellState): AppStageShellState {
    val reconciler = remember { AppStageShellStateReconciler(AndroidNotificationStageActionGateway) }
    return reconciler.reconcile(state)
}

/** An app's name for a stage, shared with the dock so both name a stage the same way. */
internal fun stageLabel(
    id: AppStageId,
    state: LauncherShellState,
): String =
    state.installedApps.firstOrNull { app ->
        app.identity.packageName == id.packageName && app.identity.profile.id == id.profileId
    }?.let { app ->
        app.identity.profile.profileDisplayLabel(app.label)
    } ?: "${id.packageName.value} (${id.profileId.value})"

/** The installed app behind a stage, or null once the launcher has lost track of it. */
internal fun stageAppIdentity(
    id: AppStageId,
    state: LauncherShellState,
): AppIdentity? =
    state.installedApps.firstOrNull { app ->
        app.identity.packageName == id.packageName && app.identity.profile.id == id.profileId
    }?.identity

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
        is NotificationStageAction.ProviderAction -> title
    }
