package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLabelSettingsJsonCodecTest {
    @Test
    fun roundTripsTheBackgroundChoice() {
        val settings = HomeLabelSettings(backgroundAlphaPercent = 75, showBackground = true)

        assertEquals(settings, encodeLabels(settings).toLabelSettings(HomeLabelSettings()))
    }

    @Test
    fun labelSettingsStoredBeforeTheBackgroundWasAChoiceKeepTheirOpacityAndShowNoBackground() {
        // The old format: no "showBackground" key. Every other stored value is carried through as it was.
        val stored =
            encodeLabels(
                HomeLabelSettings(
                    iconSizeDp = 56,
                    backgroundAlphaPercent = 30,
                    textSizeSp = 13,
                    showText = true,
                    maxWidthDp = 96,
                    maxLines = 2,
                    sizing = HomeLabelSizing.DYNAMIC,
                ),
            ).apply { remove("showBackground") }

        val decoded = stored.toLabelSettings(HomeLabelSettings())

        assertEquals(30, decoded.backgroundAlphaPercent)
        assertEquals(false, decoded.showBackground)
        assertEquals(56, decoded.iconSizeDp)
        assertEquals(13, decoded.textSizeSp)
        assertEquals(96, decoded.maxWidthDp)
        assertEquals(2, decoded.maxLines)
        assertEquals(HomeLabelSizing.DYNAMIC, decoded.sizing)
        // Turning the background on gives back the user's own opacity, and it then survives a round trip.
        val turnedOn = encodeLabels(decoded.copy(showBackground = true)).toLabelSettings(HomeLabelSettings())
        assertEquals(30, turnedOn.backgroundAlphaPercent)
        assertEquals(true, turnedOn.showBackground)
    }

    @Test
    fun labelsHaveNoBackgroundByDefault() {
        assertEquals(false, HomeLabelSettings().showBackground)
    }
}
