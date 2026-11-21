package com.riffle.app.launcher.diagnostics

import com.riffle.core.domain.launcher.diagnostics.AfterUpdateLog
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateStart
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateState
import com.riffle.core.domain.launcher.diagnostics.PackageStamp
import com.riffle.core.domain.launcher.diagnostics.StartMilestone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The recorder over an in-memory store, with an executor that runs tasks inline. */
class AfterUpdateRecorderTest {
    private val inline = java.util.concurrent.Executor { it.run() }

    private fun probe(
        version: Long = 12,
        updated: Long = 5_000,
        role: Boolean? = true,
        resolves: String? = "com.riffle.app/com.riffle.app.MainActivity",
    ) = StartProbe(PackageStamp(version, updated, firstInstallTimeMillis = 1_000), role, resolves)

    private val known = AfterUpdateState(lastVersionCode = 11, lastUpdateTimeMillis = 2_000)

    @Test
    fun theFirstStartAfterAnUpdateIsMarkedWithItsFactsAndLaterStartsOfTheSameVersionAreRecordedAsNormalStarts() {
        val store = InMemoryAfterUpdateStore(known)
        val recorder = AfterUpdateRecorder(store, inline)

        recorder.onStart(
            atMillis = 10_000,
            intentText = "act=MAIN cat=[HOME]",
            isTaskRoot = true,
            cold = true,
            sdkInt = 34,
        ) { probe().copy(taskBaseIntent = "act=MAIN cat=[LAUNCHER]") }

        val entry = store.read().entries.single()
        assertTrue(entry.afterUpdate)
        assertEquals(true, entry.cold)
        assertEquals(34, entry.sdkInt)
        assertEquals("act=MAIN cat=[LAUNCHER]", entry.taskBaseIntent)
        assertEquals(11L, entry.fromVersionCode)
        assertEquals(12L, entry.toVersionCode)
        assertEquals("act=MAIN cat=[HOME]", entry.intentText)
        assertEquals(true, entry.homeRoleHeld)
        assertEquals("com.riffle.app/com.riffle.app.MainActivity", entry.homeResolvesTo)

        val next = AfterUpdateRecorder(store, inline)
        next.onStart(atMillis = 99_000, intentText = "x", isTaskRoot = true) { probe() }
        assertEquals(listOf(false, true), store.read().entries.map { it.afterUpdate })
    }

    @Test
    fun theFirstFrameAndHomeIntentsAttachToTheOpenEntryOnlyInsideFiveMinutes() {
        val store = InMemoryAfterUpdateStore(known)
        val recorder = AfterUpdateRecorder(store, inline)
        recorder.onStart(atMillis = 10_000, intentText = "x", isTaskRoot = true) { probe() }

        recorder.onFirstFrame(640)
        recorder.onHomeIntent(10_500)
        recorder.onHomeIntent(10_000 + AfterUpdateLog.WATCH_MILLIS + 1)

        val entry = store.read().entries.single()
        assertEquals(640L, entry.firstFrameMillis)
        assertEquals(listOf(10_500L), entry.homeIntentsAtMillis)
    }

    @Test
    fun aStartThatWasNoUpdateIsRecordedAndItsFrameAndHomeIntentsAttachToIt() {
        val store = InMemoryAfterUpdateStore(AfterUpdateState(lastVersionCode = 12, lastUpdateTimeMillis = 5_000))
        val recorder = AfterUpdateRecorder(store, inline)

        recorder.onStart(atMillis = 10_000, intentText = "x", isTaskRoot = true) { probe() }
        recorder.onFirstFrame(500)
        recorder.onHomeIntent(10_100)

        val entry = store.read().entries.single()
        assertFalse(entry.afterUpdate)
        assertEquals(500L, entry.firstFrameMillis)
        assertEquals(listOf(10_100L), entry.homeIntentsAtMillis)
    }

    @Test
    fun theFirstFrameAndTheMilestonesSoFarAreOneWriteAndALaterMilestoneReplacesTheList() {
        val store = InMemoryAfterUpdateStore(known)
        val recorder = AfterUpdateRecorder(store, inline)
        recorder.onStart(atMillis = 10_000, intentText = "x", isTaskRoot = true) { probe() }
        val writesAfterStart = store.writes
        val early = listOf(StartMilestone("application", 100), StartMilestone("onResume", 400))

        recorder.onFirstFrame(640, early)
        assertEquals(writesAfterStart + 1, store.writes)
        assertEquals(early, store.read().entries.single().milestones)

        recorder.onMilestones(early + StartMilestone("window focus", 700))
        assertEquals(
            listOf("application", "onResume", "window focus"),
            store.read().entries.single().milestones.map {
                it.label
            },
        )
        assertEquals(640L, store.read().entries.single().firstFrameMillis)
    }

    @Test
    fun aFailingProbeNeverEscapesTheRecorder() {
        val store = InMemoryAfterUpdateStore(known)
        val recorder = AfterUpdateRecorder(store, inline)

        recorder.onStart(atMillis = 1, intentText = "x", isTaskRoot = true) { error("package manager is gone") }
        recorder.onFirstFrame(5)

        assertEquals(known, store.read())
    }

    @Test
    fun theStoredTextRoundTripsReadsGarbageAsEmptyAndHoldsOnlyFacts() {
        val state =
            AfterUpdateState(
                lastVersionCode = 12,
                lastUpdateTimeMillis = 5_000,
                entries =
                    listOf(
                        AfterUpdateStart(
                            atMillis = 1,
                            fromVersionCode = 11,
                            toVersionCode = 12,
                            intentText = "act=MAIN",
                            isTaskRoot = true,
                            homeRoleHeld = null,
                            homeResolvesTo = null,
                            firstFrameMillis = 9,
                            homeIntentsAtMillis = listOf(4),
                            afterUpdate = false,
                            cold = true,
                            sdkInt = 34,
                            taskBaseIntent = "act=MAIN cat=[HOME]",
                            milestones = listOf(StartMilestone("application", 3)),
                        ),
                    ),
            )
        val text = encodeAfterUpdateState(state)

        assertEquals(state, decodeAfterUpdateState(text))
        listOf(null, "", "nope", "[]", "7").forEach { assertEquals(it, AfterUpdateState(), decodeAfterUpdateState(it)) }
        assertEquals(setOf("version", "lastVersionCode", "lastUpdateTime", "entries"), JSONObject(text).keySet())
    }

    @Test
    fun textStoredByTheOldFormatStillDecodes() {
        val old =
            """{"version":1,"lastVersionCode":1785,"lastUpdateTime":2000,"entries":[{"at":5,"from":1783,"to":1785,""" +
                """"intent":"act=MAIN","taskRoot":true,"role":true,"homeResolvesTo":"a/b","firstFrame":1229,""" +
                """"homeIntents":[9]}]}"""

        val entry = decodeAfterUpdateState(old).entries.single()

        assertEquals(1785L, entry.toVersionCode)
        assertEquals(1229L, entry.firstFrameMillis)
        assertTrue(entry.afterUpdate)
        assertNull(entry.cold)
        assertEquals(emptyList<StartMilestone>(), entry.milestones)
    }

    @Test
    fun theIntentTextNamesActionCategoriesFlagsAndComponent() {
        assertEquals(
            "act=android.intent.action.MAIN cat=[android.intent.category.DEFAULT,android.intent.category.HOME] " +
                "flg=0x10200000 cmp=com.riffle.app/.MainActivity",
            intentDiagnosticsText(
                action = "android.intent.action.MAIN",
                categories = setOf("android.intent.category.HOME", "android.intent.category.DEFAULT"),
                flags = 0x10200000,
                component = "com.riffle.app/.MainActivity",
            ),
        )
        assertEquals("act=none cat=[] flg=0x0 cmp=none", intentDiagnosticsText(null, null, 0, null))
    }

    @Test
    fun theStoreIsExcludedFromBackupAndHoldsNothingBeforeItIsWritten() {
        val rule = """<exclude domain="sharedpref" path="riffle_after_update.xml" />"""
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains(rule))
        assertTrue(modern.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").contains(rule))
        assertTrue(modern.substringAfter("<device-transfer>").substringBefore("</device-transfer>").contains(rule))
        assertNull(InMemoryAfterUpdateStore().read().lastVersionCode)
    }
}
