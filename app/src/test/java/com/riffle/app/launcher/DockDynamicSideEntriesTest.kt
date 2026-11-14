package com.riffle.app.launcher

import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.stream.StreamPageOption
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dock's dynamic entries (live apps, now playing, the contextual tiles): what each says and what a tap means. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DockDynamicSideEntriesTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val chat =
        InstalledApp(
            identity = AppIdentity(AppPackageName("chat"), AppActivityName("chat.Main")),
            label = "Messages",
        )

    @Test
    fun aLiveAppSaysHowManyAreNewAndCarriesTheBadge() {
        val entry =
            DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", "Chat", count = 3)
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)

        assertEquals("Messages, 3 new", entry.contentDescription)
        assertEquals("Messages", entry.label)
        assertEquals(3, entry.badgeCount)
        assertEquals(chat.identity, entry.identity)
        // A tap opens the app (the same launch a pinned icon does, in its own profile); a press that starts on it
        // shows its page.
        assertEquals(DockDynamicEntryIntent.Dispatch(LauncherShellAction.LaunchApp(chat.identity)), entry.intent)
        assertEquals("Open Messages", entry.openLabel)
        assertEquals("Show Messages in Stream", entry.showInStreamLabel)
        assertNull(entry.symbol)
    }

    @Test
    fun aLiveAppTheLauncherCannotResolveFallsBackToTheGroupsOwnLabel() {
        val entry =
            DockDynamicSideEntry.LiveApp("gone:personal", "gone", "personal", "Gone", count = 2)
                .toDockEntry(emptyList(), streamShown = false, resources = resources)

        assertEquals("Gone, 2 new", entry.contentDescription)
        assertNull(entry.identity)
        // Nothing to launch: a tap keeps opening its page in the Stream.
        assertEquals(DockDynamicEntryIntent.Delegate, entry.intent)
    }

    @Test
    fun aLiveAppsLongPressMenuIsItsShortcutsAppInfoAndHide() {
        val shortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = chat.identity,
                shortLabel = "Compose",
                longLabel = "New message",
            )
        val entry =
            DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", "Chat", count = 3)
                .toDockEntry(
                    listOf(chat),
                    streamShown = false,
                    resources = resources,
                    appShortcuts = mapOf(chat.identity to listOf(shortcut)),
                )

        assertEquals("Open in Stream", entry.openInStreamMenuLabel)
        assertEquals(
            listOf("App shortcuts (1)", "App info", "Hide app"),
            entry.appMenu.map { it.label },
        )
        assertEquals(
            LauncherShellAction.OpenAppInfo(chat.identity),
            entry.appMenu.single { it.label == "App info" }.action,
        )
        assertEquals(
            LauncherShellAction.HideApp(chat.identity),
            entry.appMenu.single { it.label == "Hide app" }.action,
        )
    }

    @Test
    fun nowPlayingNamesTheTitleWhenItIsShownAndNotWhenItIsWithheld() {
        val titled =
            DockDynamicSideEntry.NowPlaying("chat", "personal", "Music", "Midnight City")
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)
        val withheld =
            DockDynamicSideEntry.NowPlaying("chat", "personal", "Music", null)
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)

        // A tap opens the player app; a press that starts on it shows the Now page.
        assertEquals(DockDynamicEntryIntent.Dispatch(LauncherShellAction.LaunchApp(chat.identity)), titled.intent)
        assertEquals("Show Messages in Stream", titled.showInStreamLabel)
        assertEquals("Now playing: Midnight City", titled.contentDescription)
        assertEquals("Now playing", withheld.contentDescription)
        assertEquals(DockDynamicSymbol.NowPlaying, titled.symbol)
        assertEquals(0, titled.badgeCount)
    }

    @Test
    fun inTheContextualModeATapOnALiveAppShowsItsPageAndItsLongPressLeadsWithThePageOptions() {
        val live = DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", "Chat", count = 3)
        val option = ShortcutContextMenuItem(label = "Show as List", onSelect = {})
        val entry =
            live.toDockEntry(
                listOf(chat),
                streamShown = true,
                resources = resources,
                selected = true,
                pageMenu = listOf(option),
            )

        assertEquals(DockDynamicEntryIntent.Delegate, entry.intent)
        // The ringed icon's page is already showing: a second tap opens the app, and its label says so.
        assertEquals("Open Messages", entry.openLabel)
        val other = live.toDockEntry(listOf(chat), streamShown = true, resources = resources, selected = false)
        assertEquals("Show Messages", other.openLabel)
        // The tap already shows the page, so there is no separate "Show in Stream" or "Open in Stream".
        assertNull(entry.showInStreamLabel)
        assertNull(entry.openInStreamMenuLabel)
        assertTrue(entry.isSelected)
        assertEquals(listOf("Show as List", "App info", "Hide app"), entry.appMenu.map { it.label })
    }

    @Test
    fun theHomeModeKeepsTheLaunchTapAndTheOpenInStreamMenu() {
        val entry =
            DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", "Chat", count = 3)
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)
        assertFalse(entry.isSelected)
        assertEquals("Open in Stream", entry.openInStreamMenuLabel)
        assertEquals("Open Messages", entry.openLabel)
    }

    @Test
    fun aContextualTileNamesItsSectionAndShowsItsPageOnATap() {
        val tile = DockDynamicSideEntry.ContextTile(StreamSection.TODAY)
        val open = tile.toDockEntry(emptyList(), streamShown = true, resources = resources, selected = true)
        val other = tile.toDockEntry(emptyList(), streamShown = true, resources = resources)

        assertEquals("ctx:today", open.key)
        assertEquals("Today", open.label)
        assertEquals("Today", open.contentDescription)
        assertEquals("Show Today", open.openLabel)
        assertEquals(DockDynamicEntryIntent.Delegate, open.intent)
        assertNull(open.identity)
        assertTrue(open.isContextTile)
        assertTrue(open.isSelected)
        assertFalse(other.isSelected)
    }

    @Test
    fun aTapIsReadFromTheEntrysKey() {
        assertEquals(StreamFocusTarget.Now, dockDynamicEntryTarget("now"))
        assertEquals(StreamFocusTarget.AppStack("chat:personal"), dockDynamicEntryTarget("live:chat:personal"))
        assertEquals(StreamFocusTarget.Section(StreamSection.RECENTS), dockDynamicEntryTarget("ctx:recents"))
        assertNull(dockDynamicEntryTarget("ctx:nothing"))
        assertNull(dockDynamicEntryTarget("nothing"))
        // A pinned app has a page too: its live page, else its shortcuts page.
        assertEquals(StreamFocusTarget.AppShortcuts("files:personal"), dockDynamicEntryTarget("pin:files:personal"))
        // The Stream button is gone: its old key means nothing.
        assertNull(dockDynamicEntryTarget("stream"))
    }

    @Test
    fun everyEntryTheDomainMakesIsReadBackToTheSameTarget() {
        val live = DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", null, 1)
        assertEquals(StreamFocusTarget.AppStack(live.groupKey), dockDynamicEntryTarget(live.key))
        assertEquals(
            StreamFocusTarget.Now,
            dockDynamicEntryTarget(DockDynamicSideEntry.NowPlaying("m", null, null, null).key),
        )
        StreamSection.layoutSections.forEach { section ->
            assertEquals(
                StreamFocusTarget.Section(section),
                dockDynamicEntryTarget(DockDynamicSideEntry.ContextTile(section).key),
            )
        }
    }

    @Test
    fun aTilesPageOptionsOfferTheOtherViewMoveAndHide() {
        val requests = mutableListOf<StreamPageOption>()
        val layout = StreamLayout.DEFAULT
        val tiles = listOf(StreamSection.NOW, StreamSection.RECENTS, StreamSection.TODAY)
        val items =
            pageOptionMenu(
                DockDynamicSideEntry.ContextTile(StreamSection.RECENTS),
                layout,
                tiles,
                resources,
                requests::add,
            )

        assertEquals(
            listOf("Show as List", "Move earlier", "Move later", "Hide section"),
            items.map { it.label },
        )
        items.forEach { item -> item.onSelect?.invoke() }
        assertEquals(
            listOf(
                StreamPageOption.ShowAs(StreamSection.RECENTS, com.riffle.core.domain.launcher.stream.StreamView.LIST),
                // Recents earlier steps over Notifications, which draws no tile.
                StreamPageOption.Move(StreamSection.RECENTS, -2),
                StreamPageOption.Move(StreamSection.RECENTS, 1),
                StreamPageOption.Hide(StreamSection.RECENTS),
            ),
            requests,
        )
    }

    @Test
    fun aLiveAppOffersNoMoveAndAHiddenSectionIsOfferedBack() {
        val hidden =
            StreamLayoutReducer.reduce(StreamLayout.DEFAULT, StreamLayoutAction.SetEnabled(StreamSection.FEEDS, false))
        val items =
            pageOptionMenu(
                DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", "Chat", 1),
                hidden,
                listOf(StreamSection.NOW),
                resources,
            ) {}
        assertEquals(listOf("Show as List", "Hide section", "Show Feeds"), items.map { it.label })
    }
}
