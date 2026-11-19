package com.riffle.app.launcher.stream

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.currentSplitFacts
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.expressions.LocalItemHider
import com.riffle.app.launcher.pairs.AppPairsRepository
import com.riffle.app.launcher.stream.todoist.TasksSettingsController
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.PairAvailability
import com.riffle.core.domain.launcher.dock.PairLauncher
import com.riffle.core.domain.launcher.home.DEFAULT_DOCK_LIVE_APP_SLOTS
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.PairsSection
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamColumnRect
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamHideItems
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.stream.TaskActionOutcome
import com.riffle.core.domain.launcher.stream.TaskActions
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the shell needs to show the Stream: the content provider (one shared subscription per source, nothing read until
 * a section is observed), the layouts, the one action gateway and the image loader. Built once per process by the
 * activity. Public only so the shell can take it; everything it holds is internal.
 */
@Suppress("LongParameterList")
class StreamHost internal constructor(
    internal val provider: StreamContentProvider,
    internal val layouts: CachedStreamLayoutRepository,
    internal val actions: StreamActionGateway,
    internal val imageLoader: ExpressionImageLoader,
    internal val formats: StreamFormats = SystemStreamFormats(),
    /** The existing explicit access flow for a source (notification access, calendar, usage access). */
    internal val requestAccess: (SourceId) -> Unit = {},
    /** Turns the sources of a section back on: only ever called from the section's own "Turn on" button. */
    internal val enableSources: (List<SourceId>) -> Unit = {},
    /** The posture the Stream is shown on, which the provider reads the options of; the UI keeps it current. */
    internal val posture: StreamPostureHolder = StreamPostureHolder(),
    /** Where the Stream should land when the Dock opens it on a live app or on now playing. */
    internal val focus: StreamFocusRequests = StreamFocusRequests(),
    /** Adding and refreshing tasks; null where there is no task source (tests, previews). */
    internal val tasks: TaskActions? = null,
    /** Settings > Stream > Tasks (the token); null where there is none. */
    internal val taskSettings: TasksSettingsController? = null,
    /** Settings > Stream > Recent players: clearing them; null where there are none. */
    internal val recentPlayers: RecentPlayersControl? = null,
    /** The page the Stream was last left on, per posture, for this session. */
    internal val pages: StreamPageMemory = StreamPageMemory(),
    /** What the Stream and the Dock's contextual mode tell each other: the pages, the open one, page options. */
    internal val dock: StreamDockLink = StreamDockLink(),
    /** The pairs of the Pairs stack; null where there is none (tests, previews: the page then has no pairs). */
    internal val appPairs: AppPairsRepository? = null,
    /** Opens a layout's two apps side by side; null where nothing can (tests that do not launch). */
    internal val pairLauncher: PairLauncher? = null,
)

/**
 * The last page of the Stream per posture, kept for the session (ADR 0003, Views): opening the Stream with no target
 * returns to it. Never stored: it is a convenience, not a setting.
 */
internal class StreamPageMemory {
    private val keys = HashMap<HomeLayoutDeviceClass, String>()

    fun last(deviceClass: HomeLayoutDeviceClass): String? = keys[deviceClass.workspaceLayoutClass()]

    fun remember(
        deviceClass: HomeLayoutDeviceClass,
        key: String,
    ) {
        keys[deviceClass.workspaceLayoutClass()] = key
    }
}

/** A request to land the Stream on [target]; [id] tells a repeated request for the same target apart. */
internal class StreamFocusRequest(val id: Long, val target: StreamFocusTarget)

/** The pending landing of the Stream, set by the Dock and consumed by the surface once it has scrolled there. */
internal class StreamFocusRequests {
    var pending: StreamFocusRequest? by mutableStateOf(null)
        private set
    private var lastId = 0L

    fun request(target: StreamFocusTarget) {
        pending = StreamFocusRequest(++lastId, target)
    }

    fun handled(id: Long) {
        if (pending?.id == id) pending = null
    }

    /** Drops a landing nothing has consumed: the pull that asked for it sprang back. */
    fun cancel() {
        pending = null
    }
}

/** The device class the Stream is drawn for, readable from the provider's threads. */
internal class StreamPostureHolder {
    @Volatile var deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE
}

/**
 * The stateful Stream: reads the layout of [deviceClass], observes each enabled section (results come off the main
 * thread and are posted back to it), runs actions off the main thread and shows a snackbar when one fails.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun StreamDestination(
    host: StreamHost,
    deviceClass: HomeLayoutDeviceClass,
    installedApps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
    reducedMotion: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** How many live apps the Dock shows: the pages of the first of them come first, as the Dock's icons do. */
    liveAppSlots: Int = DEFAULT_DOCK_LIVE_APP_SLOTS,
    onConnectTasks: () -> Unit = {},
    /** The apps pinned in the Dock, in its order: each one with nothing live has a page of its shortcuts. */
    pinnedApps: List<PinnedApp> = emptyList(),
    /** The launcher shortcuts of each app, as the launcher already read them for its long-press menus. */
    appShortcuts: AppShortcutsByApp = emptyMap(),
    /** Whether Riffle is the Home app: only the Home app can read shortcuts. */
    launcherRole: Boolean = true,
    /** How cards are tinted and where an open card and its actions sit (Settings > Stream, a later slice). */
    streamSettings: StreamSettings = StreamSettings(),
    /** Whether the Dock can hold an open card's actions row (a Dock along the top or bottom edge). */
    dockCanHoldActions: Boolean = false,
    /** Whether the Dock's row can hold the page's quick actions (a Dock along the top or bottom edge). */
    quickActionsInDock: Boolean = false,
    /** See [StreamSurface]: where the stack's column stands inside this surface (unfolded), null folded. */
    pageBounds: (() -> StreamColumnRect?)? = null,
    /** A tap on the Stream's empty space: closes the Stream. */
    onEmptyTap: (() -> Unit)? = null,
) {
    LaunchedEffect(host) { host.layouts.initialize() }
    SideEffect { host.posture.deviceClass = deviceClass }
    val layouts by host.layouts.layouts.collectAsState()
    val layout = layouts.forPosture(deviceClass)
    // The sections the provider reads: the Pairs stack is the launcher's own list, not a source (see below).
    val enabledSections = layout.enabledSections.map { it.section }.filter { it != StreamSection.PAIRS }
    val attempts = remember(host) { mutableStateMapOf<StreamSection, Int>() }
    val sourceResults = rememberStreamSectionResults(host.provider, enabledSections, attempts)
    val splitAvailable =
        PairAvailability.available(streamSettings.showPairs, currentSplitFacts().canSplit)
    val pairsController = remember(host) { PairsController(host.appPairs, host.pairLauncher) }
    val results =
        rememberPairsResults(sourceResults, host.appPairs, pairsController, installedApps, splitAvailable)
    val scope = rememberCoroutineScope()
    val failures =
        remember(
            host,
        ) { MutableSharedFlow<StreamFailure>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    val hider = LocalItemHider.current
    val callbacks =
        StreamCallbacks(
            onPairEdit = pairsController::edit,
            onAction = { content, action ->
                // A pair is the launcher's own: it is opened and removed here, not by the gateway.
                if (content is AppPairContent) {
                    pairsController.perform(content, action)
                } else {
                    scope.launch(Dispatchers.Default) {
                        val outcome =
                            runCatching { host.actions.perform(content, action) }
                                .getOrDefault(StreamActionResult.Failed)
                        if (outcome == StreamActionResult.Failed || outcome == StreamActionResult.Unavailable) {
                            failures.tryEmit(StreamFailure.of(content, action))
                        }
                    }
                }
            },
            onLayoutAction = { action: StreamLayoutAction -> host.layouts.update(deviceClass, action) },
            onAddTask = { text ->
                scope.launch(Dispatchers.Default) {
                    val outcome = runCatching { host.tasks?.add(text) }.getOrNull()
                    if (outcome != TaskActionOutcome.DONE) failures.tryEmit(StreamFailure())
                }
            },
            onRefreshTasks = { scope.launch(Dispatchers.Default) { host.tasks?.refresh() } },
            onConnectTasks = onConnectTasks,
            onRetrySection = { section -> attempts[section] = (attempts[section] ?: 0) + 1 },
            onRequestAccess = { sources ->
                sources.firstOrNull()?.let { host.requestAccess(StreamSourceIds.userSourceOf(it)) }
            },
            onEnableSection = {
                    section ->
                host.enableSources(section.itemSources.map(StreamSourceIds::userSourceOf).distinct())
            },
            onHide =
                hider?.let { itemHider ->
                    {
                            content,
                            groupKey,
                            kind,
                        ->
                        StreamHideItems.itemFor(content, groupKey)?.let { itemHider.hide(it, kind) }
                    }
                },
        )
    val env =
        StreamEnvironment(
            imageLoader = host.imageLoader,
            appIconLoader = appIconLoader,
            installedApps = installedApps,
            reducedMotion = reducedMotion,
            formats = host.formats,
            nowEpochMillis = host.formats.now(),
            cardSettings = streamSettings,
        )
    // Built when the pinned apps, what is live or the shortcuts change, never on a recomposition of the Stream.
    val shortcutPages =
        remember(pinnedApps, appShortcuts, launcherRole) {
            // Every pinned app has one; those that are live are folded into their notifications page (see `merge`).
            StreamShortcutPages.plan(pinnedApps, emptySet(), appShortcuts, launcherRole)
        }
    val pinnedGroupKeys = remember(pinnedApps) { pinnedApps.map { StreamShortcutPages.appKeyOf(it.identity) } }
    StreamSurface(
        layout = layout,
        results = results,
        env = env,
        callbacks = callbacks,
        modifier = modifier,
        contentPadding = contentPadding,
        failures = failures,
        focusRequest = host.focus.pending,
        onFocusHandled = host.focus::handled,
        initialPageKey = remember(host, deviceClass) { host.pages.last(deviceClass) },
        onPageChanged = { key ->
            host.pages.remember(deviceClass, key)
            // A half-made pair is dropped when its page is left.
            if (key != StreamPagePlanner.keyOf(StreamSection.PAIRS)) pairsController.leave()
        },
        splitAvailable = splitAvailable,
        removedPair = pairsController.removed,
        pairChips = rememberPairChips(pairsController),
        onRestorePair = pairsController::restore,
        liveAppSlots = liveAppSlots,
        dockLink = host.dock,
        shortcutPages = shortcutPages,
        pinnedGroupKeys = pinnedGroupKeys,
        actionsInDock =
            dockCanHoldActions && streamSettings.cardActionsPlacement == CardActionsPlacement.IN_DOCK,
        quickActionsInDock = quickActionsInDock,
        pageBounds = pageBounds,
        onEmptyTap = onEmptyTap,
    )
    PairAppPickerHost(pairsController, installedApps, appIconLoader)
}

/** The app picker of the Pairs page while it is asked for (the draft's half, a saved pair's side). */
@Composable
private fun PairAppPickerHost(
    controller: PairsController,
    installedApps: List<InstalledApp>,
    appIconLoader: AppIconLoader,
) {
    val pick = controller.picker ?: return
    PairAppPicker(
        title =
            stringResource(
                if (pick.secondHalf) R.string.stream_pair_choose_second else R.string.stream_pair_choose_first,
            ),
        exclude = pick.exclude,
        apps = installedApps,
        iconLoader = appIconLoader,
        onPick = { app -> controller.pick(app.identity) },
        onDismiss = controller::dismissPicker,
    )
}

/**
 * The results the Stream draws: what the sources gave, and the Pairs page from the stored pairs and the draft
 * ([PairsSection]; a pair whose apps are no longer installed is removed first). Nothing is read for it from a source.
 */
@Composable
private fun rememberPairsResults(
    sourceResults: Map<StreamSection, StreamSectionResult>,
    repository: AppPairsRepository?,
    controller: PairsController,
    installedApps: List<InstalledApp>,
    splitAvailable: Boolean,
): Map<StreamSection, StreamSectionResult> {
    val stored = repository?.pairs?.collectAsState()?.value.orEmpty()
    LaunchedEffect(repository) { withContext(Dispatchers.IO) { repository?.ensureLoaded() } }
    // An app that is uninstalled takes its layouts away. Until the apps are known nothing is removed.
    LaunchedEffect(repository, installedApps) {
        if (installedApps.isNotEmpty()) repository?.pruneUninstalled(installedApps.mapTo(HashSet()) { it.identity })
    }
    // The page is not built while a split is not possible (the planner hides it too): nothing to read, nothing drawn.
    val draft = controller.draft
    return remember(sourceResults, stored, draft, installedApps, splitAvailable) {
        if (!splitAvailable) {
            sourceResults
        } else {
            val byIdentity = installedApps.associateBy { it.identity }
            val resolve: (AppIdentity) -> PairSide? = { identity ->
                byIdentity[identity]?.let { PairSide(identity, it.label, ItemImageKeys.appIcon(identity)) }
            }
            sourceResults + (StreamSection.PAIRS to PairsSection.result(stored, draft, resolve))
        }
    }
}

/**
 * The latest result of each of [sections], observed while the Stream is composed. A section's subscription lives only
 * as long as it is shown; callbacks arrive on the provider's threads and are posted to the main thread.
 *
 * The map handed out is a NEW immutable map for every change: the surface keys what it derives from it (the pages)
 * on its identity, and a single mutable map instance would never invalidate them (the Stream sat on "Loading" for
 * good on a device for exactly that reason). A change in [attempts] for a section (its Retry) drops the section's
 * subscription and result and observes it afresh.
 */
@Composable
internal fun rememberStreamSectionResults(
    provider: StreamContentProvider,
    sections: List<StreamSection>,
    attempts: Map<StreamSection, Int> = emptyMap(),
): Map<StreamSection, StreamSectionResult> {
    val results = remember(provider) { mutableStateOf<Map<StreamSection, StreamSectionResult>>(emptyMap()) }
    sections.forEach { section ->
        key(section) {
            val attempt = attempts[section] ?: 0
            DisposableEffect(provider, section, attempt) {
                val main = Handler(Looper.getMainLooper())
                val live = AtomicBoolean(true)
                if (section in results.value) results.value = results.value - section
                val subscription =
                    provider.observe(section) { result ->
                        main.post { if (live.get()) results.value = results.value + (section to result) }
                    }
                onDispose {
                    live.set(false)
                    subscription.cancel()
                }
            }
        }
    }
    return results.value
}

/** "New pair", or "Cancel" while a draft is in front (it replaces New pair): the Pairs page's quick actions. */
@Composable
private fun rememberPairChips(controller: PairsController): List<QuickChip> {
    val draft = controller.draft
    val new = stringResource(R.string.stream_pair_new)
    val cancel = stringResource(R.string.stream_pair_cancel)
    return remember(draft != null, new, cancel) {
        if (draft == null) {
            listOf(QuickChip("pair-new", new, Icons.Filled.Add) { controller.edit(PairEdit.NewPair) })
        } else {
            listOf(QuickChip("pair-cancel", cancel, Icons.Filled.Close) { controller.edit(PairEdit.DiscardDraft) })
        }
    }
}
