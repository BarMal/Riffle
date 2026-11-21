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
            cold = true,
            sdkInt = 34,
            taskBaseIntent = "act=MAIN cat=[HOME]",
        )

    @Test
    fun aFreshInstallIsRecordedAsANormalStart() {
        val state = AfterUpdateLog.onStart(AfterUpdateState(), stamp(10, updated = 1_000), facts(5))

        assertEquals(10L, state.lastVersionCode)
        assertFalse(state.entries.single().afterUpdate)
    }

    @Test
    fun anUpdateAddsOneEntryMarkedAfterUpdateWithTheOldAndNewVersionAndTheFacts() {
        val before = AfterUpdateState(lastVersionCode = 10, lastUpdateTimeMillis = 1_000)

        val state = AfterUpdateLog.onStart(before, stamp(11, updated = 2_000), facts(5_000))

        val entry = state.entries.single()
        assertTrue(entry.afterUpdate)
        assertEquals(10L, entry.fromVersionCode)
        assertEquals(11L, entry.toVersionCode)
        assertEquals(5_000L, entry.atMillis)
        assertEquals(true, entry.isTaskRoot)
        assertEquals(true, entry.cold)
        assertEquals(34, entry.sdkInt)
        assertEquals("act=MAIN cat=[HOME]", entry.taskBaseIntent)
        assertNull(entry.firstFrameMillis)
        assertEquals(2_000L, state.lastUpdateTimeMillis)
    }

    @Test
    fun aBuildThatDidNotKnowTheOldVersionStillMarksTheFirstStartAfterAnUpdate() {
        val state = AfterUpdateLog.onStart(AfterUpdateState(), stamp(11, updated = 2_000), facts(5_000))

        val entry = state.entries.single()
        assertTrue(entry.afterUpdate)
        assertNull(entry.fromVersionCode)
    }

    @Test
    fun aSameVersionRestartIsANormalStartAndAReinstallOfTheSameCodeIsAnUpdateByItsUpdateTime() {
        val known = AfterUpdateState(lastVersionCode = 11, lastUpdateTimeMillis = 2_000)

        assertFalse(AfterUpdateLog.onStart(known, stamp(11, updated = 2_000), facts(9)).entries.single().afterUpdate)
        assertTrue(AfterUpdateLog.onStart(known, stamp(11, updated = 3_000), facts(9)).entries.single().afterUpdate)
    }

    @Test
    fun theListKeepsTheLastTenNewestFirst() {
        var state = AfterUpdateState(lastVersionCode = 1, lastUpdateTimeMillis = 1_000)
        (2L..13L).forEach { version ->
            state = AfterUpdateLog.onStart(state, stamp(version, updated = version * 1_000), facts(version))
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
            )

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

    @Test
    fun theTextListsOneMilestonePerLineInTimeOrderWithTheFirstFrameAmongThem() {
        val entry =
            AfterUpdateStart(
                atMillis = 5,
                fromVersionCode = 10,
                toVersionCode = 10,
                intentText = "act=MAIN",
                isTaskRoot = true,
                homeRoleHeld = true,
                homeResolvesTo = null,
                firstFrameMillis = 900,
                afterUpdate = false,
                cold = true,
                sdkInt = 34,
                taskBaseIntent = "act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]",
                milestones =
                    listOf(
                        StartMilestone("application", 120),
                        StartMilestone("MainActivity.onCreate entered", 300),
                        StartMilestone("onResume", 950),
                        StartMilestone("setContent called", 700),
                    ),
            )

        val lines = AfterUpdateLog.asText(listOf(entry)) { "t$it" }.lines()

        assertEquals("t5  10  (normal start)", lines.first())
        assertTrue("start: cold, Android SDK 34" in lines)
        assertTrue("task root intent: act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]" in lines)
        assertEquals(
            listOf(
                "application: 120 ms",
                "MainActivity.onCreate entered: 300 ms",
                "setContent called: 700 ms",
                "first frame: 900 ms",
                "onResume: 950 ms",
            ),
            lines.filter { it.endsWith(" ms") },
        )
    }

    @Test
    fun theTextMarksAnUpdateStartAndAFrameThatWasNeverDrawn() {
        val entry =
            AfterUpdateStart(
                atMillis = 5,
                fromVersionCode = 10,
                toVersionCode = 11,
                intentText = "x",
                isTaskRoot = true,
                homeRoleHeld = null,
                homeResolvesTo = null,
                firstFrameMillis = null,
                milestones = listOf(StartMilestone("application", 120)),
            )

        val lines = AfterUpdateLog.asText(listOf(entry)) { "t$it" }.lines()

        assertEquals("t5  10 -> 11  (after update)", lines.first())
        assertTrue("start: unknown, Android SDK unknown" in lines)
        assertTrue("first frame: not drawn" in lines)
    }

    @Test
    fun milestonesAreReplacedByTheNewestListAndCutToTheBound() {
        val start = 10_000L
        val started =
            AfterUpdateLog.onStart(AfterUpdateState(), stamp(2, updated = 2_000), facts(start))

        val one = AfterUpdateLog.withMilestones(started, start, listOf(StartMilestone("a", 1)))
        val two = AfterUpdateLog.withMilestones(one, start, listOf(StartMilestone("a", 1), StartMilestone("b", 2)))
        val many = (1..AfterUpdateLog.MAX_MILESTONES + 5).map { StartMilestone("m$it", it.toLong()) }

        assertEquals(listOf("a", "b"), two.entries.single().milestones.map { it.label })
        assertEquals(
            AfterUpdateLog.MAX_MILESTONES,
            AfterUpdateLog.withMilestones(two, start, many).entries.single().milestones.size,
        )
        assertEquals(two, AfterUpdateLog.withMilestones(two, start + 1, many))
    }

    @Test
    fun aColdStartIsOneWhoseActivityWasCreatedSoonAfterTheProcessStarted() {
        assertEquals(true, AfterUpdateLog.isColdStart(0))
        assertEquals(true, AfterUpdateLog.isColdStart(AfterUpdateLog.COLD_START_MAX_MILLIS))
        assertEquals(false, AfterUpdateLog.isColdStart(AfterUpdateLog.COLD_START_MAX_MILLIS + 1))
        assertNull(AfterUpdateLog.isColdStart(null))
    }

    @Test
    fun theNewFieldsRoundTripThroughTheCodec() {
        val state =
            AfterUpdateState(
                lastVersionCode = 11,
                lastUpdateTimeMillis = 2_000,
                entries =
                    listOf(
                        AfterUpdateStart(
                            atMillis = 5,
                            fromVersionCode = 11,
                            toVersionCode = 11,
                            intentText = "x",
                            isTaskRoot = true,
                            homeRoleHeld = true,
                            homeResolvesTo = null,
                            firstFrameMillis = 300,
                            afterUpdate = false,
                            cold = false,
                            sdkInt = 33,
                            taskBaseIntent = "act=MAIN cat=[HOME]",
                            milestones = listOf(StartMilestone("application", 12), StartMilestone("onResume", 250)),
                        ),
                    ),
            )

        assertEquals(state, AfterUpdateCodec.decode(AfterUpdateCodec.encode(state)))
    }

    @Test
    fun anEntryStoredByTheOldFormatStillDecodesAndCountsAsAnAfterUpdateStart() {
        // Exactly the keys the first version of the codec wrote: no afterUpdate, cold, sdk, taskBase or milestones.
        val old =
            StoredValue.Obj(
                mapOf(
                    "version" to StoredValue.Num(1),
                    "lastVersionCode" to StoredValue.Num(1785),
                    "lastUpdateTime" to StoredValue.Num(2_000),
                    "entries" to
                        StoredValue.Arr(
                            listOf(
                                StoredValue.Obj(
                                    mapOf(
                                        "at" to StoredValue.Num(5),
                                        "from" to StoredValue.Num(1783),
                                        "to" to StoredValue.Num(1785),
                                        "intent" to StoredValue.Str("act=MAIN"),
                                        "taskRoot" to StoredValue.Bool(true),
                                        "role" to StoredValue.Bool(true),
                                        "homeResolvesTo" to StoredValue.Str("a/b"),
                                        "firstFrame" to StoredValue.Num(1229),
                                        "homeIntents" to StoredValue.Arr(listOf(StoredValue.Num(9))),
                                    ),
                                ),
                            ),
                        ),
                ),
            )

        val entry = AfterUpdateCodec.decode(old).entries.single()

        assertEquals(1785L, entry.toVersionCode)
        assertEquals(1229L, entry.firstFrameMillis)
        assertEquals(listOf(9L), entry.homeIntentsAtMillis)
        assertTrue(entry.afterUpdate)
        assertNull(entry.cold)
        assertNull(entry.sdkInt)
        assertNull(entry.taskBaseIntent)
        assertEquals(emptyList(), entry.milestones)
    }

    @Test
    fun aBadMilestoneIsDroppedAndTheRestKept() {
        val entry =
            StoredValue.Obj(
                mapOf(
                    "at" to StoredValue.Num(1),
                    "to" to StoredValue.Num(2),
                    "milestones" to
                        StoredValue.Arr(
                            listOf(
                                StoredValue.Str("x"),
                                StoredValue.Obj(mapOf("label" to StoredValue.Str("no time"))),
                                StoredValue.Obj(mapOf("label" to StoredValue.Str("ok"), "ms" to StoredValue.Num(4))),
                            ),
                        ),
                ),
            )

        val decoded = AfterUpdateCodec.decode(StoredValue.Obj(mapOf("entries" to StoredValue.Arr(listOf(entry)))))

        assertEquals(listOf(StartMilestone("ok", 4)), decoded.entries.single().milestones)
    }
}
