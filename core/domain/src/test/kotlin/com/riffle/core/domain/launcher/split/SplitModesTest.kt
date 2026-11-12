package com.riffle.core.domain.launcher.split

import com.riffle.core.domain.launcher.split.SplitModes.asShownMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitModesTest {
    @Test
    fun theStreamButtonTogglesHomeOnlyAndTheLastUsedShownMode() {
        assertEquals(SplitMode.SPLIT, SplitModes.afterStreamButton(SplitMode.HOME_ONLY, SplitMode.SPLIT, true))
        assertEquals(
            SplitMode.STREAM_ONLY,
            SplitModes.afterStreamButton(SplitMode.HOME_ONLY, SplitMode.STREAM_ONLY, true),
        )
        assertEquals(SplitMode.HOME_ONLY, SplitModes.afterStreamButton(SplitMode.SPLIT, SplitMode.SPLIT, true))
        assertEquals(
            SplitMode.HOME_ONLY,
            SplitModes.afterStreamButton(SplitMode.STREAM_ONLY, SplitMode.STREAM_ONLY, true),
        )
    }

    @Test
    fun withoutRoomForASplitTheStreamIsShownWhole() {
        assertEquals(SplitMode.STREAM_ONLY, SplitModes.afterStreamButton(SplitMode.HOME_ONLY, SplitMode.SPLIT, false))
        assertEquals(SplitMode.STREAM_ONLY, SplitModes.modeOf(true, SplitMode.SPLIT, false))
    }

    @Test
    fun theShellsAnswerMapsToAMode() {
        assertEquals(SplitMode.HOME_ONLY, SplitModes.modeOf(false, SplitMode.STREAM_ONLY, true))
        assertEquals(SplitMode.SPLIT, SplitModes.modeOf(true, SplitMode.SPLIT, true))
        assertEquals(SplitMode.STREAM_ONLY, SplitModes.modeOf(true, SplitMode.STREAM_ONLY, true))
    }

    @Test
    fun homePressAlwaysReturnsToHomeOnly() {
        SplitMode.entries.forEach { assertEquals(SplitMode.HOME_ONLY, SplitModes.afterHomePress(it)) }
    }

    @Test
    fun endingInHomeOnlyKeepsTheLastShownMode() {
        assertEquals(SplitMode.STREAM_ONLY, SplitModes.lastShownAfter(SplitMode.HOME_ONLY, SplitMode.STREAM_ONLY))
        assertEquals(SplitMode.SPLIT, SplitModes.lastShownAfter(SplitMode.SPLIT, SplitMode.STREAM_ONLY))
        assertEquals(SplitMode.SPLIT, SplitMode.HOME_ONLY.asShownMode())
    }

    @Test
    fun whatEachModeShows() {
        assertEquals(listOf(false, true, true), SplitMode.entries.map { it.showsStream })
        assertEquals(listOf(true, true, false), SplitMode.entries.map { it.showsHome })
    }
}

class SplitPostureTest {
    @Test
    fun onlyExpandedPosturesSplit() {
        val expected =
            mapOf(
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.PHONE to false,
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.PHONE_LANDSCAPE to false,
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.FOLDABLE to true,
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.TABLET to true,
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.DESKTOP to true,
            )
        expected.forEach { (posture, splits) -> assertEquals(splits, posture.supportsSplit(), posture.name) }
    }
}
