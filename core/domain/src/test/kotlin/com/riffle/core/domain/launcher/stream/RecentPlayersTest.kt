package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentPlayersTest {
    private class MemoryStore(var stored: List<RecentPlayer> = emptyList()) : RecentPlayersStore {
        var writes = 0
        var failRead = false

        override fun read(): List<RecentPlayer> {
            if (failRead) error("unreadable")
            return stored
        }

        override fun write(players: List<RecentPlayer>) {
            writes++
            stored = players
        }
    }

    private var now = 1_000L
    private val store = MemoryStore()
    private val tracker = RecentPlayersTracker(store) { now }

    private fun playing(
        pkg: String,
        title: String? = null,
        artist: String? = null,
    ) = PlayerSighting(pkg, "personal", playing = true, title = title, artist = artist)

    private fun paused(pkg: String) = PlayerSighting(pkg, "personal", playing = false)

    private val allInstalled = setOf("music", "podcasts", "radio", "audiobooks", "videos", "streams", "tunes")

    private fun at(
        millis: Long,
        block: () -> Unit,
    ) {
        now = millis
        block()
    }

    @Test
    fun aPlayingSessionIsRecordedAndComesBackAsASourceOnceItsSessionEnds() {
        val active = tracker.observe(listOf(playing("music", "Opening theme", "Band")), allInstalled)
        // It has a session: its player card shows, not a source card.
        assertTrue(active.isEmpty())
        assertEquals(listOf(RecentPlayer("music", "personal", 1_000L)), store.stored)

        at(2_000L) {
            val sources = tracker.observe(emptyList(), allInstalled)
            assertEquals(1, sources.size)
            assertEquals("music", sources.single().player.packageName)
            assertEquals("Opening theme", sources.single().title)
            assertEquals("Band", sources.single().artist)
        }
    }

    @Test
    fun aPausedSessionIsNotRecordedUntilItPlays() {
        tracker.observe(listOf(paused("music")), allInstalled)
        assertTrue(store.stored.isEmpty())
        assertEquals(0, store.writes)
    }

    @Test
    fun mostRecentFirstWithOneEntryPerAppAndTheTimeMovesOnReplay() {
        at(1_000L) { tracker.observe(listOf(playing("music")), allInstalled) }
        at(2_000L) { tracker.observe(listOf(playing("podcasts")), allInstalled) }
        at(3_000L) { tracker.observe(listOf(playing("radio")), allInstalled) }
        at(4_000L) { tracker.observe(listOf(playing("music")), allInstalled) }

        assertEquals(listOf("music", "radio", "podcasts"), store.stored.map { it.packageName })
        assertEquals(4_000L, store.stored.first().lastPlayedEpochMillis)
        assertEquals(3, store.stored.size)
    }

    @Test
    fun theListIsCappedAtFiveAndTheOldestFallsOff() {
        listOf("music", "podcasts", "radio", "audiobooks", "videos", "streams").forEachIndexed { index, pkg ->
            at(1_000L + index) { tracker.observe(listOf(playing(pkg)), allInstalled) }
        }
        assertEquals(RecentPlayers.CAP, store.stored.size)
        assertEquals(
            listOf("streams", "videos", "audiobooks", "radio", "podcasts"),
            store.stored.map { it.packageName },
        )
    }

    @Test
    fun anAppWithAnActiveSessionIsNotAlsoASourceEvenWhenPaused() {
        at(1_000L) { tracker.observe(listOf(playing("music"), playing("podcasts")), allInstalled) }
        at(2_000L) {
            val sources = tracker.observe(listOf(paused("music")), allInstalled)
            assertEquals(listOf("podcasts"), sources.map { it.player.packageName })
        }
    }

    @Test
    fun anUninstalledAppDropsOutAndIsWrittenOut() {
        at(1_000L) { tracker.observe(listOf(playing("music")), allInstalled) }
        at(2_000L) { tracker.observe(listOf(playing("podcasts")), allInstalled) }
        at(3_000L) {
            val sources = tracker.observe(emptyList(), allInstalled - "music")
            assertEquals(listOf("podcasts"), sources.map { it.player.packageName })
            assertEquals(listOf("podcasts"), store.stored.map { it.packageName })
        }
    }

    @Test
    fun unknownInstalledAppsKeepEverything() {
        at(1_000L) { tracker.observe(listOf(playing("music")), allInstalled) }
        at(2_000L) {
            assertEquals(listOf("music"), tracker.observe(emptyList(), null).map { it.player.packageName })
        }
    }

    @Test
    fun aDismissedSourceIsGoneUntilItPlaysAgain() {
        at(1_000L) { tracker.observe(listOf(playing("music", "Song")), allInstalled) }
        at(2_000L) { tracker.dismiss(RecentPlayer.keyOf("music", "personal")) }
        at(3_000L) {
            assertTrue(tracker.observe(emptyList(), allInstalled).isEmpty())
            assertTrue(store.stored.isEmpty())
        }
        at(4_000L) { tracker.observe(listOf(playing("music")), allInstalled) }
        at(5_000L) {
            val back = tracker.observe(emptyList(), allInstalled).single()
            assertEquals(4_000L, back.player.lastPlayedEpochMillis)
            // What it played before it was dismissed is forgotten with it.
            assertNull(back.title)
        }
    }

    @Test
    fun clearForgetsEverythingIncludingTheText() {
        at(1_000L) { tracker.observe(listOf(playing("music", "Song"), playing("radio", "Show")), allInstalled) }
        tracker.clear()
        assertTrue(store.stored.isEmpty())
        assertTrue(tracker.observe(emptyList(), allInstalled).isEmpty())
    }

    @Test
    fun theTextIsKeptInMemoryOnlyAndAFreshProcessSaysNothingOfIt() {
        at(1_000L) { tracker.observe(listOf(playing("music", "Song", "Band")), allInstalled) }
        val restarted = RecentPlayersTracker(store) { 9_000L }
        val source = restarted.observe(emptyList(), allInstalled).single()
        assertEquals("music", source.player.packageName)
        assertNull(source.title)
        assertNull(source.artist)
    }

    @Test
    fun nothingIsWrittenWhenNothingChanged() {
        at(1_000L) { tracker.observe(listOf(playing("music")), allInstalled) }
        val writes = store.writes
        // Paused, or an observation with nothing new, rewrites nothing.
        at(1_000L) { tracker.observe(emptyList(), allInstalled) }
        at(1_000L) { tracker.observe(listOf(paused("music")), allInstalled) }
        assertEquals(writes, store.writes)
    }

    @Test
    fun aStoreThatCannotBeReadIsNeverOverwritten() {
        store.failRead = true
        store.stored = listOf(RecentPlayer("radio", null, 5L))
        tracker.observe(listOf(playing("music")), allInstalled)
        assertEquals(listOf(RecentPlayer("radio", null, 5L)), store.stored)
        assertEquals(0, store.writes)
    }

    @Test
    fun theCodecKeepsOnlyPackageProfileAndTime() {
        val players = listOf(RecentPlayer("music", "personal", 30L), RecentPlayer("radio", null, 20L))
        val encoded = RecentPlayersCodec.encode(players)
        assertEquals(players, RecentPlayersCodec.decode(encoded))
        val text = encoded.toString()
        assertTrue("title" !in text && "artist" !in text)
    }

    @Test
    fun decodingIsSafeOnBadData() {
        assertTrue(RecentPlayersCodec.decode(null).isEmpty())
        assertTrue(RecentPlayersCodec.decode(StoredValue.Str("nope")).isEmpty())
        val bad =
            StoredValue.Obj(
                mapOf(
                    "players" to
                        StoredValue.Arr(
                            listOf(
                                StoredValue.Str("x"),
                                StoredValue.Obj(mapOf("package" to StoredValue.Str(""))),
                                StoredValue.Obj(mapOf("package" to StoredValue.Str("music"))),
                                StoredValue.Obj(
                                    mapOf("package" to StoredValue.Str("music"), "playedAt" to StoredValue.Num(7)),
                                ),
                            ),
                        ),
                ),
            )
        val decoded = RecentPlayersCodec.decode(bad)
        // The duplicate collapses to the newer one, a negative or missing time reads as zero.
        assertEquals(listOf(RecentPlayer("music", null, 7L)), decoded)
    }

    @Test
    fun decodingAppliesTheCapAndTheOrder() {
        val many = (1..8).map { RecentPlayer("app$it", null, it.toLong()) }
        val decoded = RecentPlayersCodec.decode(RecentPlayersCodec.encode(many))
        assertEquals(listOf("app8", "app7", "app6", "app5", "app4"), decoded.map { it.packageName })
    }
}
