package com.riffle.core.domain.launcher.diagnostics

import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AfterUpdateStartTest {
    private fun stamp(
        version: Long,
        updated: Long,
        installed: Long = 1_000,
    ) = PackageStamp(version, updated, installed)

    private fun facts(at: Long) =
        StartFacts(
            atMillis = at,
            intentText = "act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000000",
            isTaskRoot = true,
            homeRoleHeld = true,
            homeResolvesTo = "com.riffle.app/com.riffle.app.MainActivity",
        )

    @Test
    fun aFreshInstallOnlyRecordsTheStampAndAddsNoEntry() {
        val (state, added) = AfterUpdateLog.onStart(AfterUpdateState(), stamp(10, updated = 1_000), facts(5))

        assertFalse(added)
        assertEquals(10L, state.lastVersionCode)
        assertEquals(emptyList(), state.entries)
    }

    @Test
    fun anUpdateAddsOneEntryWithTheOldAndNewVersionAndTheFacts() {
        val before = AfterUpdateState(lastVersionCode = 10, lastUpdateTimeMillis = 1_000)

        val (state, added) = AfterUpdateLog.onStart(before, stamp(11, updated = 2_000), facts(5_000))

        assertTrue(added)
        val entry = state.entries.single()
        assertEquals(10L, entry.fromVersionCode)
        assertEquals(11L, entry.toVersionCode)
        assertEquals(5_000L, entry.atMillis)
        assertEquals(true, entry.isTaskRoot)
        assertNull(entry.firstFrameMillis)
        assertEquals(2_000L, state.lastUpdateTimeMillis)
    }

    @Test
    fun aBuildThatDidNotKnowTheOldVersionStillRecordsTheFirstStartAfterAnUpdate() {
        val (state, added) = AfterUpdateLog.onStart(AfterUpdateState(), stamp(11, updated = 2_000), facts(5_000))

        assertTrue(added)
        assertNull(state.entries.single().fromVersionCode)
    }

    @Test
    fun aSameVersionRestartRecordsNothingAndAReinstallOfTheSameCodeIsRecordedByItsUpdateTime() {
        val known = AfterUpdateState(lastVersionCode = 11, lastUpdateTimeMillis = 2_000)

        assertFalse(AfterUpdateLog.onStart(known, stamp(11, updated = 2_000), facts(9)).second)
        assertTrue(AfterUpdateLog.onStart(known, stamp(11, updated = 3_000), facts(9)).second)
    }

    @Test
    fun theListKeepsTheLastTenNewestFirst() {
        var state = AfterUpdateState(lastVersionCode = 1, lastUpdateTimeMillis = 1_000)
        (2L..13L).forEach { version ->
            state = AfterUpdateLog.onStart(state, stamp(version, updated = version * 1_000), facts(version)).first
        }

        assertEquals(AfterUpdateLog.MAX_ENTRIES, state.entries.size)
        assertEquals(13L, state.entries.first().toVersionCode)
        assertEquals(4L, state.entries.last().toVersionCode)
    }

    @Test
    fun theFirstFrameIsSetOnceAndHomeIntentsOnlyInsideTheFiveMinuteWindow() {
        val start = 10_000L
        val started =
            AfterUpdateLog.onStart(
                AfterUpdateState(lastVersionCode = 1, lastUpdateTimeMillis = 1_000),
                stamp(2, updated = 2_000),
                facts(start),
            ).first

        val framed = AfterUpdateLog.withFirstFrame(AfterUpdateLog.withFirstFrame(started, start, 420), start, 999)
        assertEquals(420L, framed.entries.single().firstFrameMillis)

        var state = framed
        listOf(start + 1_000, start + AfterUpdateLog.WATCH_MILLIS, start + AfterUpdateLog.WATCH_MILLIS + 1)
            .forEach { state = AfterUpdateLog.withHomeIntent(state, start, it) }
        assertEquals(
            listOf(start + 1_000, start + AfterUpdateLog.WATCH_MILLIS),
            state.entries.single().homeIntentsAtMillis,
        )
        // An entry that is not the one started at that time is left alone.
        assertEquals(state, AfterUpdateLog.withHomeIntent(state, start + 7, start + 2_000))
    }

    @Test
    fun theTextNamesEveryFactAndCopesWithMissingOnes() {
        val entry =
            AfterUpdateStart(
                atMillis = 5,
                fromVersionCode = null,
                toVersionCode = 11,
                intentText = "act=MAIN",
                isTaskRoot = false,
                homeRoleHeld = null,
                homeResolvesTo = null,
                firstFrameMillis = null,
            )

        val text = AfterUpdateLog.asText(listOf(entry)) { "t$it" }

        assertTrue(text.contains("t5  ? -> 11"))
        assertTrue(text.contains("home role held: unknown"))
        assertTrue(text.contains("home resolves to: none"))
        assertTrue(text.contains("first frame: not drawn"))
        assertTrue(text.contains("home intents in the first 5 minutes: none"))
    }

    @Test
    fun theStateRoundTripsThroughTheCodecAndOldOrBadDataDecodesToDefaults() {
        val state =
            AfterUpdateState(
                lastVersionCode = 11,
                lastUpdateTimeMillis = 2_000,
                entries =
                    listOf(
                        AfterUpdateStart(
                            atMillis = 5,
                            fromVersionCode = 10,
                            toVersionCode = 11,
                            intentText = "act=MAIN cat=[HOME]",
                            isTaskRoot = true,
                            homeRoleHeld = false,
                            homeResolvesTo = "a/b",
                            firstFrameMillis = 300,
                            homeIntentsAtMillis = listOf(7, 9),
                        ),
                    ),
            )
        assertEquals(state, AfterUpdateCodec.decode(AfterUpdateCodec.encode(state)))

        assertEquals(AfterUpdateState(), AfterUpdateCodec.decode(null))
        assertEquals(AfterUpdateState(), AfterUpdateCodec.decode(StoredValue.Str("x")))
        val withoutVersion = StoredValue.Obj(mapOf("at" to StoredValue.Num(1)))
        val oneBad =
            StoredValue.Obj(mapOf("entries" to StoredValue.Arr(listOf(StoredValue.Str("x"), withoutVersion))))
        assertEquals(emptyList(), AfterUpdateCodec.decode(oneBad).entries)
    }

    @Test
    fun aStoredListLongerThanTheBoundIsCutOnRead() {
        val many =
            (1L..15L).map { StoredValue.Obj(mapOf("at" to StoredValue.Num(it), "to" to StoredValue.Num(it))) }

        val decoded = AfterUpdateCodec.decode(StoredValue.Obj(mapOf("entries" to StoredValue.Arr(many))))

        assertEquals(AfterUpdateLog.MAX_ENTRIES, decoded.entries.size)
    }
}
