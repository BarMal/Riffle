package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySettingsTextTest {
    @Test
    fun everyLevelHasALabelAndSaysWhatItShows() {
        assertEquals(
            listOf("Show everything", "Titles only", "App icons only"),
            SourceContentLevel.entries.map(PrivacySettingsText::label),
        )
        SourceContentLevel.entries.forEach { level -> assertTrue(PrivacySettingsText.summary(level).isNotBlank()) }
    }

    @Test
    fun optionsAreSpokenWithTheirSource() {
        assertEquals(
            "Media, App icons only. \"Hidden content\" with the app icon and count; no text at all",
            PrivacySettingsText.optionSpoken("Media", SourceContentLevel.HIDE_TEXT),
        )
    }

    @Test
    fun theScreenSwitchSaysPlainlyThatItBlocksYourOwnScreenshots() {
        assertTrue(PrivacySettingsText.SCREEN_OFF.contains("blocks your own screenshots"))
        assertTrue(PrivacySettingsText.SCREEN_ON.contains("Settings can still be captured"))
    }
}
