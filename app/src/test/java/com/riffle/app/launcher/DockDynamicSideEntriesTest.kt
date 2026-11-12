package com.riffle.app.launcher

import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
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
        assertEquals(DockDynamicEntryIntent.Delegate, entry.intent)
        assertNull(entry.symbol)
    }

    @Test
    fun aLiveAppTheLauncherCannotResolveFallsBackToTheGroupsOwnLabel() {
        val entry =
            DockDynamicSideEntry.LiveApp("gone:personal", "gone", "personal", "Gone", count = 2)
                .toDockEntry(emptyList(), streamShown = false, resources = resources)

        assertEquals("Gone, 2 new", entry.contentDescription)
        assertNull(entry.identity)
    }

    @Test
    fun nowPlayingNamesTheTitleWhenItIsShownAndNotWhenItIsWithheld() {
        val titled =
            DockDynamicSideEntry.NowPlaying("chat", "personal", "Music", "Midnight City")
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)
        val withheld =
            DockDynamicSideEntry.NowPlaying("chat", "personal", "Music", null)
                .toDockEntry(listOf(chat), streamShown = false, resources = resources)

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
