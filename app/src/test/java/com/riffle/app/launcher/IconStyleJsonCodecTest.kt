package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.icons.AppIconOverride
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconStyleJsonCodecTest {
    private val notes = AppIdentity(AppPackageName("com.example.notes"), AppActivityName("com.example.notes.Main"))
    private val workNotes = notes.copy(profile = AppProfile.work())

    @Test
    fun blobsWrittenBeforeIconStylesReadAsTheDefaults() {
        val old = """{"version":11,"appearance":{"themeMode":"DARK"},"home":{"addNewApps":false}}"""

        val settings = decodeLauncherSettings(old)

        assertEquals(IconStyleSettings(), settings.icons)
        assertTrue(settings.icons.isDefault)
        assertFalse(settings.home.addNewApps)
    }

    @Test
    fun everyChoiceAndOverrideSurvivesARoundTrip() {
        val style =
            IconStyleSettings(
                shape = IconShape.TEARDROP,
                legacyIcons = LegacyIconHandling.AS_IS,
                themedIcons = true,
                themeAllIcons = true,
                iconPack = "com.example.pack.one",
            ).withIconOverride(notes, IconPackDrawableRef("com.example.pack.two", "notes_alt"))
                .withLabelOverride(notes, "Jot")
                .withIconOverride(workNotes, IconPackDrawableRef("com.example.pack.gone", "x"))

        val decoded = decodeLauncherSettings(encodeLauncherSettings(LauncherSettings(icons = style)))

        assertEquals(style, decoded.icons)
    }

    @Test
    fun anOverrideIsStoredByReferenceNeverAsABitmap() {
        val style = IconStyleSettings().withIconOverride(notes, IconPackDrawableRef("com.example.pack.two", "a"))

        val json = JSONObject(encodeLauncherSettings(LauncherSettings(icons = style))).getJSONObject("icons")

        val entry = json.getJSONArray("overrides").getJSONObject(0)
        assertEquals("com.example.pack.two", entry.getString("iconPack"))
        assertEquals("a", entry.getString("drawable"))
        assertEquals(
            setOf("package", "activity", "profileId", "profileType", "iconPack", "drawable"),
            entry.keys().asSequence().toSet(),
        )
    }

    @Test
    fun unknownValuesAndBrokenEntriesFallBackWithoutThrowing() {
        val blob =
            """{"version":11,"icons":{"shape":"hexagon","legacyIcons":7,"themedIcons":"yes","iconPack":null,
            "overrides":[1,{"package":"p"},{"package":"com.example.a","activity":"A","label":"Mine"},
            {"package":"com.example.b","activity":"B","iconPack":"com.example.pack","drawable":"d",
            "profileType":"???"}]}}"""

        val icons = decodeLauncherSettings(blob).icons

        assertEquals(IconShape.SYSTEM, icons.shape)
        assertEquals(LegacyIconHandling.PLATE, icons.legacyIcons)
        assertFalse(icons.themedIcons)
        assertNull(icons.iconPack)
        assertEquals(2, icons.overrides.size)
        val first = AppIdentity(AppPackageName("com.example.a"), AppActivityName("A"))
        assertEquals(AppIconOverride(label = "Mine"), icons.overrides[first])
    }

    @Test
    fun aSectionThatIsNotAnObjectReadsAsTheDefaults() {
        assertEquals(IconStyleSettings(), decodeLauncherSettings("""{"version":11,"icons":"nope"}""").icons)
    }
}
