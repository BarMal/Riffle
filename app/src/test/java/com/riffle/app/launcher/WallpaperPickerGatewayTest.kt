package com.riffle.app.launcher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WallpaperPickerGatewayTest {
    @Test
    fun wallpaperPickerUsesAndroidWallpaperPickerAndLiveWallpaperChooserFallbackActions() {
        assertEquals(Intent.ACTION_SET_WALLPAPER, WALLPAPER_PICKER_ACTION)
        assertEquals("android.service.wallpaper.LIVE_WALLPAPER_CHOOSER", FALLBACK_WALLPAPER_PICKER_ACTION)
    }

    @Test
    fun launchesWallpaperPickerThroughChooserAfterCheckingBaseIntentAvailability() {
        val availabilityCheckedIntents = mutableListOf<Intent>()
        val launchedIntents = mutableListOf<Intent>()
        val chooserSourceIntents = mutableListOf<Intent>()
        val chooserIntent = Intent()

        val result =
            launchWallpaperPicker(
                isAvailable = { intent ->
                    availabilityCheckedIntents += intent
                    true
                },
                launch = launchedIntents::add,
                createChooser = { intent ->
                    chooserSourceIntents += intent
                    chooserIntent
                },
            )

        assertEquals(WallpaperPickerLaunchResult.Launched, result)
        assertEquals(1, availabilityCheckedIntents.size)
        assertEquals(1, chooserSourceIntents.size)
        assertSame(availabilityCheckedIntents.single(), chooserSourceIntents.single())
        assertEquals(listOf(chooserIntent), launchedIntents)
    }

    @Test
    fun reportsUnavailableWhenNoWallpaperPickerCanHandleIntent() {
        val preferredIntent = Intent()
        val fallbackIntent = Intent()
        val checkedIntents = mutableListOf<Intent>()

        val result =
            launchWallpaperPicker(
                isAvailable = { intent ->
                    checkedIntents += intent
                    false
                },
                launch = { error("Should not launch") },
                candidateIntents = listOf(preferredIntent, fallbackIntent),
            )

        assertEquals(WallpaperPickerLaunchResult.Unavailable, result)
        assertEquals(listOf(preferredIntent, fallbackIntent), checkedIntents)
    }

    @Test
    fun launchesFallbackWallpaperPickerWhenStandardPickerIsUnavailable() {
        val preferredIntent = Intent()
        val fallbackIntent = Intent()
        val checkedIntents = mutableListOf<Intent>()
        val launchedIntents = mutableListOf<Intent>()

        val result =
            launchWallpaperPicker(
                isAvailable = { intent ->
                    checkedIntents += intent
                    intent === fallbackIntent
                },
                launch = launchedIntents::add,
                createChooser = { intent -> intent },
                candidateIntents = listOf(preferredIntent, fallbackIntent),
            )

        assertEquals(WallpaperPickerLaunchResult.Launched, result)
        assertEquals(listOf(preferredIntent, fallbackIntent), checkedIntents)
        assertEquals(listOf(fallbackIntent), launchedIntents)
    }

    @Test
    fun launchesAvailableWallpaperPicker() {
        var launchCount = 0

        val result =
            launchWallpaperPicker(
                isAvailable = { true },
                launch = { launchCount += 1 },
                createChooser = { it },
            )

        assertEquals(WallpaperPickerLaunchResult.Launched, result)
        assertEquals(1, launchCount)
    }

    @Test
    fun reportsFailureWhenAvailableWallpaperPickerLaunchThrows() {
        val result =
            launchWallpaperPicker(
                isAvailable = { true },
                launch = { error("No activity") },
            )

        assertEquals(WallpaperPickerLaunchResult.Failed, result)
    }
}
