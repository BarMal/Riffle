package com.riffle.app.launcher

import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.StreamShortcutPages

/**
 * What the Dock's pinned apps know of the Stream (ADR 0003, Amendment 3, "Every icon has a stack"): each pinned app has
 * a page (its live page, else its shortcuts), so it can be chosen by a press and, in the contextual mode, a tap shows
 * it. In the Home mode a tap still launches the app.
 */
internal data class DockPinnedStream(
    /** The contextual mode: a tap shows the app's page instead of launching the app. */
    val contextual: Boolean,
    /** The entry key ([pinnedEntryKey]) of the pinned app whose page is open (ringed), or null. */
    val selectedKey: String?,
    /** Each pinned app's name by its entry key, for the name label while choosing. */
    val labels: Map<String, String>,
    /** Shows the page of the pinned app [AppIdentity] (the Stream lands on it). */
    val onShow: (AppIdentity) -> Unit,
)

/** The pinned apps' stream link; null outside the launcher's Dock, where a pinned app only launches. */
internal val LocalDockPinnedStream = staticCompositionLocalOf<DockPinnedStream?> { null }

/** The key of the pinned app [identity] among the Dock's entries (what the press and the choice know it by). */
internal fun pinnedEntryKey(identity: AppIdentity): String =
    DockDynamicSideEntry.pinnedAppKey(StreamShortcutPages.appKeyOf(identity))

/** The apps pinned in the Dock, in its order. */
internal fun HomeLayout.pinnedApps(): List<PinnedApp> =
    dock.items.filterIsInstance<AppShortcutItem>().map { PinnedApp(it.appIdentity, it.label) }
