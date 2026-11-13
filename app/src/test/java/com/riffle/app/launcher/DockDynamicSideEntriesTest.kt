package com.riffle.app.launcher

import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dock's dynamic entries (live apps, now playing, the Stream button): what each says and what a tap means. */
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
        // A tap opens the app (the same launch a pinned icon does, in its own profile); the swipe up shows its page.
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

        // A tap opens the player app; the swipe up shows the Now page.
        assertEquals(DockDynamicEntryIntent.Dispatch(LauncherShellAction.LaunchApp(chat.identity)), titled.intent)
        assertEquals("Show Messages in Stream", titled.showInStreamLabel)
        assertEquals("Now playing: Midnight City", titled.contentDescription)
        assertEquals("Now playing", withheld.contentDescription)
        assertEquals(DockDynamicSymbol.NowPlaying, titled.symbol)
        assertEquals(0, titled.badgeCount)
    }

    @Test
    fun theStreamButtonIsSelectedWhileTheStreamShowsAndSaysWhereATapGoes() {
        val closed =
            DockDynamicSideEntry.StreamButton.toDockEntry(
                emptyList(),
                streamShown = false,
                resources = resources,
            )
        val open = DockDynamicSideEntry.StreamButton.toDockEntry(emptyList(), streamShown = true, resources = resources)

        assertEquals("Stream", closed.label)
        assertEquals("Stream", closed.contentDescription)
        // The Stream button is unchanged: its tap toggles, and it has no "show in Stream" of its own.
        assertEquals(DockDynamicEntryIntent.Delegate, closed.intent)
        assertNull(closed.showInStreamLabel)
        assertNull(closed.openInStreamMenuLabel)
        assertFalse(closed.isSelected)
        assertEquals("Stream, back to Home", open.contentDescription)
        assertTrue(open.isSelected)
        assertEquals(DockDynamicSymbol.Stream, closed.symbol)
    }

    @Test
    fun aTapIsReadFromTheEntrysKey() {
        assertEquals(DockDynamicTarget.StreamButton, dockDynamicEntryTarget("stream"))
        assertEquals(DockDynamicTarget.Focus(StreamFocusTarget.Now), dockDynamicEntryTarget("now"))
        assertEquals(
            DockDynamicTarget.Focus(StreamFocusTarget.AppStack("chat:personal")),
            dockDynamicEntryTarget("live:chat:personal"),
        )
        assertNull(dockDynamicEntryTarget("nothing"))
    }

    @Test
    fun everyEntryTheDomainMakesIsReadBackToTheSameTarget() {
        val live = DockDynamicSideEntry.LiveApp("chat:personal", "chat", "personal", null, 1)
        assertEquals(
            DockDynamicTarget.Focus(StreamFocusTarget.AppStack(live.groupKey)),
            dockDynamicEntryTarget(live.key),
        )
        assertEquals(
            DockDynamicTarget.Focus(StreamFocusTarget.Now),
            dockDynamicEntryTarget(DockDynamicSideEntry.NowPlaying("m", null, null, null).key),
        )
        assertEquals(DockDynamicTarget.StreamButton, dockDynamicEntryTarget(DockDynamicSideEntry.StreamButton.key))
    }
}
