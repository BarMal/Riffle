package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.summon.SummonCollection
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonKeepGesture
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonTimings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SummonSettingsJsonCodecTest {
    private val mail = AppIdentity(AppPackageName("com.example.mail"), AppActivityName("com.example.mail.Main"))

    private val custom =
        SummonSettings(
            slotCount = 6,
            slots =
                listOf(
                    SummonSlot.App(mail),
                    SummonSlot.Folder(LauncherItemId("folder-1")),
                    SummonSlot.Glance(GlanceWidgetKind.NOW_PLAYING),
                    SummonSlot.Favourites,
                    SummonSlot.Collection(SummonCollectionId("work")),
                ),
            collections =
                listOf(
                    SummonCollection(
                        SummonCollectionId("work"),
                        "Work",
                        listOf(SummonSlot.App(mail), SummonSlot.Collection(SummonCollectionId("tools"))),
                        icon = IconPackDrawableRef("pack.icons", "folder_work"),
                    ),
                    SummonCollection(SummonCollectionId("tools"), "Tools"),
                ),
            header = SummonHeader.HomeWidget(LauncherItemId("widget-3")),
            timings = SummonTimings(armMillis = 250, previewMillis = 400, acceptMillis = 500, slideMillis = 700),
            showHints = false,
            iconSizeDp = 52,
            keepGesture = SummonKeepGesture.REST_TO_LOCK,
        )

    @Test
    fun summonSettingsAndTheSwitchSurviveARoundTrip() {
        val settings = LauncherSettings(features = FeatureSettings(summon = true), summon = custom)
        val decoded = decodeLauncherSettings(encodeLauncherSettings(settings))
        assertEquals(custom, decoded.summon)
        assertEquals(true, decoded.features.summon)
    }

    @Test
    fun hintsAreShownUnlessTurnedOff() {
        assertEquals(true, SummonSettings().showHints)
        val saved = JSONObject(encodeLauncherSettings(LauncherSettings(summon = SummonSettings())))
        saved.getJSONObject("summon").remove("showHints")
        assertEquals(true, decodeLauncherSettings(saved.toString()).summon.showHints)
    }

    @Test
    fun everyHeaderSurvivesARoundTrip() {
        listOf(
            SummonHeader.None,
            SummonHeader.Clock,
            SummonHeader.ClockAndDate,
            SummonHeader.Glance(GlanceWidgetKind.NEXT_EVENT),
            SummonHeader.HomeWidget(LauncherItemId("w")),
        ).forEach { header ->
            val settings = LauncherSettings(summon = SummonSettings(header = header))
            assertEquals(header, decodeLauncherSettings(encodeLauncherSettings(settings)).summon.header)
        }
    }

    @Test
    fun settingsSavedBeforeSummonDecodeWithItOffAndDefaults() {
        val old = JSONObject(encodeLauncherSettings(LauncherSettings()))
        old.remove("summon")
        old.getJSONObject("features").remove("summonMode")
        val decoded = decodeLauncherSettings(old.toString())
        assertFalse(decoded.features.summon)
        assertEquals(SummonSettings(), decoded.summon)
    }

    @Test
    fun unknownSlotsAreSkippedAndBadValuesFallBackOrClamp() {
        val json = JSONObject(encodeLauncherSettings(LauncherSettings(summon = custom)))
        val summon = json.getJSONObject("summon")
        summon.getJSONArray("slots").put(JSONObject().put("kind", "teleporter"))
        summon.getJSONArray("slots").put(JSONObject().put("kind", "glance").put("widget", "no-such-widget"))
        summon.put("slotCount", 99)
        summon.put("header", JSONObject().put("kind", "hologram"))
        summon.getJSONObject("timings").put("armMillis", 1)
        val decoded = decodeLauncherSettings(json.toString()).summon
        assertEquals(custom.slots, decoded.slots)
        assertEquals(8, decoded.slotCount)
        assertEquals(SummonHeader.DEFAULT, decoded.header)
        assertEquals(100, decoded.timings.armMillis)
    }

    @Test
    fun aBrokenSummonBlockFallsBackToDefaultsWithoutLosingTheRest() {
        val json = JSONObject(encodeLauncherSettings(LauncherSettings(features = FeatureSettings(summon = true))))
        json.put("summon", JSONObject().put("slots", "not an array"))
        val decoded = decodeLauncherSettings(json.toString())
        assertEquals(SummonSettings().slots, decoded.summon.slots)
        assertEquals(true, decoded.features.summon)
    }
}
