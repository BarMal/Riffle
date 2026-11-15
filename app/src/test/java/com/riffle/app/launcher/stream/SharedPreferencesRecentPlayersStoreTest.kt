package com.riffle.app.launcher.stream

import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.stream.RecentPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class SharedPreferencesRecentPlayersStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun store() = SharedPreferencesRecentPlayersStore(context)

    @Test
    fun writesAndReadsBackOnlyPackageProfileAndTime() {
        val players = listOf(RecentPlayer("music", "personal", 30L), RecentPlayer("radio", null, 20L))
        store().write(players)

        assertEquals(players, store().read())
        val raw = context.getSharedPreferences("riffle_recent_players", 0).getString("players", "").orEmpty()
        assertFalse("title" in raw || "artist" in raw)
    }

    @Test
    fun anUnreadableValueReadsAsEmpty() {
        context.getSharedPreferences("riffle_recent_players", 0).edit().putString("players", "{not json").commit()
        assertTrue(store().read().isEmpty())
    }
}
