package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.settings.AppPairsMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PairAvailabilityTest {
    private data class Case(
        val sdk: Int,
        val width: Int,
        val multi: Boolean,
        val expected: Boolean,
    )

    @Test
    fun theRuleTable() {
        listOf(
            Case(sdk = 37, width = 724, multi = false, expected = true),
            Case(sdk = 37, width = 600, multi = false, expected = true),
            Case(sdk = 37, width = 599, multi = false, expected = false),
            Case(sdk = 37, width = 353, multi = false, expected = false),
            Case(sdk = 37, width = 724, multi = true, expected = false),
            Case(sdk = 36, width = 724, multi = false, expected = false),
            Case(sdk = 34, width = 1280, multi = false, expected = false),
            Case(sdk = 38, width = 900, multi = false, expected = true),
            Case(sdk = 38, width = 900, multi = true, expected = false),
            Case(sdk = 36, width = 353, multi = true, expected = false),
        ).forEach { case ->
            assertEquals(
                case.expected,
                PairAvailability.canSplit(case.sdk, case.width, case.multi),
                "sdk=${case.sdk} width=${case.width} multi=${case.multi}",
            )
        }
    }

    @Test
    fun theSettingDecidesWhetherTheRuleApplies() {
        assertEquals(true, PairAvailability.available(AppPairsMode.AUTOMATIC, canSplit = true))
        assertEquals(false, PairAvailability.available(AppPairsMode.AUTOMATIC, canSplit = false))
        assertEquals(true, PairAvailability.available(AppPairsMode.ALWAYS_SHOW, canSplit = false))
        assertEquals(true, PairAvailability.available(AppPairsMode.ALWAYS_SHOW, canSplit = true))
        assertEquals(false, PairAvailability.available(AppPairsMode.OFF, canSplit = true))
        assertEquals(false, PairAvailability.available(AppPairsMode.OFF, canSplit = false))
    }

    @Test
    fun automaticIsTheDefault() {
        assertEquals(
            AppPairsMode.AUTOMATIC,
            com.riffle.core.domain.launcher.settings.LauncherSettings().dock.appPairs,
        )
    }
}
