package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.onboarding.OnboardingOrigin
import com.riffle.core.domain.launcher.onboarding.OnboardingProgress
import com.riffle.core.domain.launcher.onboarding.OnboardingState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The stored text of the onboarding state: a round trip, safe reads, and its place outside every backup. */
class OnboardingStoreTest {
    @Test
    fun aStateSurvivesTheRoundTrip() {
        val state =
            OnboardingState(
                origin = OnboardingOrigin.EXISTING_USER,
                progress = OnboardingProgress.COMPLETED,
                completedRuns = 3,
            )
        assertEquals(state, decodeOnboardingState(encodeOnboardingState(state)))
        assertEquals(OnboardingState(), decodeOnboardingState(encodeOnboardingState(OnboardingState())))
    }

    @Test
    fun nothingOrGarbageReadsAsUndecided() {
        listOf(null, "", "not json", "[1,2]", "42", "{\"origin\":\"MARS\"}").forEach { text ->
            assertEquals(text, OnboardingState(), decodeOnboardingState(text))
        }
    }

    @Test
    fun aStateWrittenByTheWalkthroughBuildStillReads() {
        val old =
            """{"version":1,"origin":"EXISTING_USER","walkthrough":"SKIPPED","offer":"DISMISSED","completedRuns":2}"""
        assertEquals(
            OnboardingState(OnboardingOrigin.EXISTING_USER, OnboardingProgress.SKIPPED, completedRuns = 2),
            decodeOnboardingState(old),
        )
    }

    @Test
    fun itHoldsNoItemContent() {
        val text = encodeOnboardingState(OnboardingState(origin = OnboardingOrigin.FRESH_INSTALL, completedRuns = 1))
        assertEquals(setOf("version", "origin", "walkthrough", "completedRuns"), keysOf(text))
    }

    @Test
    fun theInMemoryStoreCountsWrites() {
        val store = InMemoryOnboardingStore()
        assertEquals(OnboardingState(), store.read())
        store.write(OnboardingState(origin = OnboardingOrigin.FRESH_INSTALL))
        assertEquals(1, store.writes)
        assertEquals(OnboardingOrigin.FRESH_INSTALL, store.read().origin)
    }

    @Test
    fun thePreferencesFileIsExcludedFromAutoBackupAndDeviceTransfer() {
        val rule = """<exclude domain="sharedpref" path="riffle_onboarding.xml" />"""
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains(rule))
        assertTrue(modern.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>").contains(rule))
        assertTrue(modern.substringAfter("<device-transfer>").substringBefore("</device-transfer>").contains(rule))
    }

    private fun keysOf(text: String): Set<String> = JSONObject(text).keySet()
}
