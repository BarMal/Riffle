package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.riffle.app.R
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamPageOption
import com.riffle.app.launcher.stream.dockLiveEntries
import com.riffle.app.launcher.stream.rememberStreamSectionResults
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.DockChoice
import com.riffle.core.domain.launcher.dock.DockDynamicSide
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.dock.DockRing
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamShortcutPages

/**
 * The Dock's dynamic side as the dock draws it (ADR 0003, Amendment 3): the entries of [DockDynamicSide] over what the
 * Stream's Notifications and Now sections deliver -- so Hide rules and content levels have already applied and there is
 * no second notification pipeline -- as neutral [DockDynamicEntry] values, and the reading of a tap on one.
 *
 * In the Home mode ([shown] false) the side is the live apps and now playing; in the contextual mode ([shown], the
 * Stream
 * showing) the same entries are followed by a tile for each Stream page that is not an app, the entry of the open
 * page is
 * ringed, and a tap switches the Stream's page. Returns [modeInterpreter] with its dynamic entries replaced. Without a
 * Stream the side is empty (there is nothing to open).
 */
@Composable
@Suppress("LongParameterList")
internal fun rememberDockDynamicSide(
    stream: StreamHost?,
    shown: Boolean,
    liveAppSlots: Int,
    installedApps: List<InstalledApp>,
    deviceClass: HomeLayoutDeviceClass,
    onOpenStream: (StreamFocusTarget?) -> Unit,
    modeInterpreter: HomeDockInterpreter,
    appShortcuts: AppShortcutsByApp = emptyMap(),
    /**
     * Whether a press on the Dock is choosing (hold to choose): from Home the Dock then extends early, drawing the
     * contextual row and its tiles before the cards are out, and the open page's ring gives way to the selection's.
     */
    choosing: Boolean = false,
    /** The apps pinned in the Dock, in its order: each has a page, so each can be ringed and chosen. */
    pinnedApps: List<PinnedApp> = emptyList(),
): HomeDockInterpreter {
    if (stream == null) {
        return modeInterpreter.copy(dynamicEntries = emptyList(), pinnedStream = null, cardActions = null)
    }
    // The provider reads the options of the posture on screen; keep it current even before the Stream has opened.
    SideEffect { stream.posture.deviceClass = deviceClass }
    val results = rememberStreamSectionResults(stream.provider, listOf(StreamSection.NOTIFICATIONS, StreamSection.NOW))
    val live = remember(results, liveAppSlots) { results.dockLiveEntries(liveAppSlots) }
    val link = stream.dock
    val layouts by stream.layouts.layouts.collectAsState()
    val layout = layouts.forPosture(deviceClass)
    // The contextual tiles are the Stream's pages that are not apps, in the Dock's order; only the contextual mode
    // has them.
    val extended = shown || choosing
    val tileSections =
        if (extended) {
            DockDynamicSide.contextSections(link.pages, nowPlaying = live.any { it is DockDynamicSideEntry.NowPlaying })
        } else {
            emptyList()
        }
    val side =
        remember(live, tileSections) { live + tileSections.map(DockDynamicSideEntry::ContextTile) }
    val pinnedKeys = remember(pinnedApps) { pinnedApps.map { app -> pinnedEntryKey(app.identity) } }
    val ringed =
        if (shown && !choosing) {
            DockRing.entryKeyFor(
                link.pages.firstOrNull { page -> page.key == link.currentKey },
                side.map { it.key } + pinnedKeys,
            )
        } else {
            null
        }
    val resources = LocalContext.current.resources
    val pageOptions = remember(link) { { option: StreamPageOption -> link.requestOption(option) } }
    val entries =
        remember(side, installedApps, shown, resources, appShortcuts, ringed, layout, tileSections) {
            side.map { entry ->
                entry.toDockEntry(
                    installedApps = installedApps,
                    streamShown = shown,
                    resources = resources,
                    appShortcuts = appShortcuts,
                    selected = entry.key == ringed,
                    pageMenu =
                        if (shown) pageOptionMenu(entry, layout, tileSections, resources, pageOptions) else emptyList(),
                )
            }
        }
    val latestOpen = rememberUpdatedState(onOpenStream)
    val pinnedLabels =
        remember(pinnedApps) {
            pinnedApps.associate {
                    app ->
                pinnedEntryKey(app.identity) to app.label
            }
        }
    val pinnedStream =
        DockPinnedStream(
            contextual = shown,
            selectedKey = ringed?.takeIf { key -> key.startsWith(DockDynamicSideEntry.PINNED_APP_KEY_PREFIX) },
            labels = pinnedLabels,
            onShow = { identity ->
                latestOpen.value(StreamFocusTarget.AppShortcuts(StreamShortcutPages.appKeyOf(identity)))
            },
        )
    return modeInterpreter.copy(
        dynamicEntries = entries,
        contextual = extended,
        onDynamicEntryDelegated = { key -> dockDynamicEntryTarget(key)?.let { target -> latestOpen.value(target) } },
        pinnedStream = pinnedStream,
        // An open card's actions, while the Stream shows it: the Dock's row (the Dock reads, the Stream writes).
        cardActions = link.openCards.shown.takeIf { shown },
    )
}

/**
 * What a key means to the Stream, by its key: a live app, now playing or a contextual tile shows its page. A tap on an
 * app entry in the Home mode launches the app instead (see [toDockEntry]); landing on the page is the press that starts
 * on the icon, the long-press item, the accessibility action and, in the contextual mode, the tap.
 */
internal fun dockDynamicEntryTarget(key: String): StreamFocusTarget? = DockChoice.pageFor(key)

@Suppress("LongParameterList")
internal fun DockDynamicSideEntry.toDockEntry(
    installedApps: List<InstalledApp>,
    streamShown: Boolean,
    resources: android.content.res.Resources,
    appShortcuts: AppShortcutsByApp = emptyMap(),
    /** Whether this entry is the one of the open page (contextual mode): it is ringed. */
    selected: Boolean = false,
    /** The page options a long-press adds (contextual mode): Show as, Move, Hide. */
    pageMenu: List<ShortcutContextMenuItem> = emptyList(),
): DockDynamicEntry =
    when (this) {
        is DockDynamicSideEntry.LiveApp ->
            liveAppEntry(installedApps.appFor(packageName, profileId), resources, appShortcuts)
        is DockDynamicSideEntry.NowPlaying ->
            nowPlayingEntry(installedApps.appFor(packageName, profileId), resources, appShortcuts)
        is DockDynamicSideEntry.ContextTile -> contextTileEntry(resources)
    }.inMode(streamShown, selected, pageMenu, resources)

private fun List<InstalledApp>.appFor(
    packageName: String,
    profileId: String?,
): InstalledApp? {
    val ofPackage = filter { app -> app.identity.packageName.value == packageName }
    return ofPackage.firstOrNull { app -> profileId == null || app.identity.profile.id.value == profileId }
        ?: ofPackage.firstOrNull()
}

/** A live app: a tap opens the app (one the launcher cannot resolve still opens its page), the swipe up its page. */
private fun DockDynamicSideEntry.LiveApp.liveAppEntry(
    app: InstalledApp?,
    resources: android.content.res.Resources,
    appShortcuts: AppShortcutsByApp,
): DockDynamicEntry {
    val name = app?.label ?: label
    return DockDynamicEntry(
        key = key,
        label = name ?: packageName,
        identity = app?.identity,
        badgeCount = count,
        isSelected = false,
        contentDescription =
            if (name != null) {
                resources.getString(R.string.dock_live_app_label, name, count)
            } else {
                resources.getString(R.string.dock_live_app_unnamed_label, count)
            },
        intent = app.tapIntent(),
    ).withAppActions(name, app, resources, appShortcuts)
}

/** Now playing: a tap opens the player app, the swipe up shows the Now page. */
private fun DockDynamicSideEntry.NowPlaying.nowPlayingEntry(
    app: InstalledApp?,
    resources: android.content.res.Resources,
    appShortcuts: AppShortcutsByApp,
): DockDynamicEntry {
    val playerName = app?.label ?: appLabel
    return DockDynamicEntry(
        key = key,
        label = playerName ?: resources.getString(R.string.dock_now_playing_label),
        identity = app?.identity,
        badgeCount = 0,
        isSelected = false,
        contentDescription =
            title?.let { resources.getString(R.string.dock_now_playing_titled_label, it) }
                ?: resources.getString(R.string.dock_now_playing_label),
        intent = app.tapIntent(),
        symbol = DockDynamicSymbol.NowPlaying,
    ).withAppActions(playerName, app, resources, appShortcuts)
}

/** What an app entry adds: the spoken click and "Show in Stream" labels, and the long-press menu. */
private fun DockDynamicEntry.withAppActions(
    name: String?,
    app: InstalledApp?,
    resources: android.content.res.Resources,
    appShortcuts: AppShortcutsByApp,
): DockDynamicEntry =
    copy(
        openLabel = name?.let { resources.getString(R.string.dock_live_app_open_action, it) },
        showInStreamLabel = name?.let { resources.getString(R.string.dock_live_app_show_in_stream_action, it) },
        appMenu = app?.let { liveAppMenuItems(it, appShortcuts) }.orEmpty(),
        openInStreamMenuLabel = resources.getString(R.string.dock_live_app_open_in_stream_menu),
    )

/**
 * The long-press menu of a live app (or the playing app), under "Open in Stream": the app's own shortcuts, App info and
 * Hide, the items every other place an app is listed offers (no Uninstall or dock edits: the entry is not pinned).
 */
internal fun liveAppMenuItems(
    app: InstalledApp,
    appShortcuts: AppShortcutsByApp,
): List<ShortcutContextMenuItem> {
    val shortcuts =
        appShortcuts[app.identity].orEmpty().map { shortcut ->
            ShortcutContextMenuItem(
                label = shortcut.contextMenuLabel,
                action = LauncherShellAction.LaunchAppShortcut(shortcut),
                enabled = shortcut.enabled,
            )
        }
    return listOfNotNull(
        shortcuts.takeIf { it.isNotEmpty() }?.let {
            ShortcutContextMenuItem(label = "App shortcuts (${it.size})", submenuItems = it)
        },
        ShortcutContextMenuItem(label = "App info", action = LauncherShellAction.OpenAppInfo(app.identity)),
        ShortcutContextMenuItem(label = "Hide app", action = LauncherShellAction.HideApp(app.identity)),
    )
}

/** A tap on an app entry opens the app, in its own profile; one the launcher cannot resolve opens its page instead. */
private fun InstalledApp?.tapIntent(): DockDynamicEntryIntent =
    this?.let { DockDynamicEntryIntent.Dispatch(LauncherShellAction.LaunchApp(it.identity)) }
        ?: DockDynamicEntryIntent.Delegate
