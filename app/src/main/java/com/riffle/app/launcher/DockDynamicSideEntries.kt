package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.riffle.app.R
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.rememberStreamSectionResults
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.DockDynamicSide
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult

/**
 * The Dock's dynamic side as the dock draws it (ADR 0003): the entries of [DockDynamicSide] over what the Stream's
 * Notifications and Now sections deliver -- so Hide rules and content levels have already applied and there is no
 * second notification pipeline -- as neutral [DockDynamicEntry] values, and the reading of a tap on one.
 *
 * Returns [modeInterpreter] with its dynamic entries replaced: whichever mode is showing, the dynamic side is the same.
 * Without a Stream the side is empty (there is nothing to open).
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
    onCloseStream: () -> Unit,
    modeInterpreter: HomeDockInterpreter,
    appShortcuts: AppShortcutsByApp = emptyMap(),
): HomeDockInterpreter {
    if (stream == null) return modeInterpreter.copy(dynamicEntries = emptyList())
    // The provider reads the options of the posture on screen; keep it current even before the Stream has opened.
    SideEffect { stream.posture.deviceClass = deviceClass }
    val results = rememberStreamSectionResults(stream.provider, listOf(StreamSection.NOTIFICATIONS, StreamSection.NOW))
    val notifications = results[StreamSection.NOTIFICATIONS]
    val now = results[StreamSection.NOW]
    val groups = (notifications as? StreamSectionResult.Ready)?.groups.orEmpty()
    val media = (now as? StreamSectionResult.Ready)?.items?.filterIsInstance<MediaContent>()?.firstOrNull()
    val side = remember(groups, media, liveAppSlots) { DockDynamicSide.entries(groups, media, liveAppSlots) }
    val resources = LocalContext.current.resources
    val entries =
        remember(side, installedApps, shown, resources, appShortcuts) {
            side.map { entry -> entry.toDockEntry(installedApps, shown, resources, appShortcuts) }
        }
    val latestOpen = rememberUpdatedState(onOpenStream)
    val latestClose = rememberUpdatedState(onCloseStream)
    val latestShown = rememberUpdatedState(shown)
    return modeInterpreter.copy(
        dynamicEntries = entries,
        onDynamicEntryDelegated = { key ->
            when (val target = dockDynamicEntryTarget(key)) {
                DockDynamicTarget.StreamButton ->
                    if (latestShown.value) latestClose.value() else latestOpen.value(null)
                is DockDynamicTarget.Focus -> latestOpen.value(target.focus)
                null -> Unit
            }
        },
    )
}

/**
 * What a key means to the Stream, by its key: the Stream button toggles Home and the Stream; a live app or now playing
 * shows its page ([Focus]). A tap on an app entry launches the app (see [toDockEntry]); [Focus] is the swipe up, the
 * long-press item and the accessibility action.
 */
internal sealed interface DockDynamicTarget {
    /** The Stream button: toggles Home and the Stream. */
    data object StreamButton : DockDynamicTarget

    /** A live app or now playing: the Stream, landed on [focus]. */
    data class Focus(val focus: StreamFocusTarget) : DockDynamicTarget
}

internal fun dockDynamicEntryTarget(key: String): DockDynamicTarget? =
    when {
        key == DockDynamicSideEntry.StreamButton.key -> DockDynamicTarget.StreamButton
        key == NOW_PLAYING_KEY -> DockDynamicTarget.Focus(StreamFocusTarget.Now)
        key.startsWith(LIVE_APP_KEY_PREFIX) ->
            DockDynamicTarget.Focus(StreamFocusTarget.AppStack(key.removePrefix(LIVE_APP_KEY_PREFIX)))
        else -> null
    }

private const val NOW_PLAYING_KEY = "now"
private const val LIVE_APP_KEY_PREFIX = "live:"

internal fun DockDynamicSideEntry.toDockEntry(
    installedApps: List<InstalledApp>,
    streamShown: Boolean,
    resources: android.content.res.Resources,
    appShortcuts: AppShortcutsByApp = emptyMap(),
): DockDynamicEntry =
    when (this) {
        is DockDynamicSideEntry.LiveApp ->
            liveAppEntry(installedApps.appFor(packageName, profileId), resources, appShortcuts)
        is DockDynamicSideEntry.NowPlaying ->
            nowPlayingEntry(installedApps.appFor(packageName, profileId), resources, appShortcuts)
        DockDynamicSideEntry.StreamButton -> DockDynamicSideEntry.StreamButton.streamButtonEntry(streamShown, resources)
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

/** The Stream button, unchanged: a tap toggles Home and the Stream. */
private fun DockDynamicSideEntry.StreamButton.streamButtonEntry(
    streamShown: Boolean,
    resources: android.content.res.Resources,
): DockDynamicEntry =
    DockDynamicEntry(
        key = key,
        label = resources.getString(R.string.dock_stream_button_label),
        identity = null,
        badgeCount = 0,
        isSelected = streamShown,
        contentDescription =
            resources.getString(
                if (streamShown) R.string.dock_stream_button_close_label else R.string.dock_stream_button_label,
            ),
        intent = DockDynamicEntryIntent.Delegate,
        symbol = DockDynamicSymbol.Stream,
    )

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

private fun List<InstalledApp>.appFor(
    packageName: String,
    profileId: String?,
): InstalledApp? {
    val ofPackage = filter { app -> app.identity.packageName.value == packageName }
    return ofPackage.firstOrNull { app -> profileId == null || app.identity.profile.id.value == profileId }
        ?: ofPackage.firstOrNull()
}

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
