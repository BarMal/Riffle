package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pure-logic coverage for the folded/unfolded editor target (#1058) -- which default a reset
 * returns to, and that each target has a distinct label. [AdaptiveStageAppearancePageContent]
 * itself needs a real Compose tree to exercise (see [AdaptiveStageAppearanceEditorTest]).
 */
class AdaptiveStageAppearancePageContentTest {
    @Test
    fun foldedTargetResetsToTheModernDefault() {
        assertEquals(
            AdaptiveStageAppearanceSettings.modern(),
            AdaptiveStageAppearanceEditorTarget.FOLDED.defaultAppearance(),
        )
    }

    @Test
    fun unfoldedTargetResetsToTheUnfoldedDefault() {
        assertEquals(
            AdaptiveStageAppearanceSettings.unfolded(),
            AdaptiveStageAppearanceEditorTarget.UNFOLDED.defaultAppearance(),
        )
    }

    @Test
    fun theTwoTargetsHaveDistinctDefaultsAndLabels() {
        assertNotEquals(
            AdaptiveStageAppearanceEditorTarget.FOLDED.defaultAppearance(),
            AdaptiveStageAppearanceEditorTarget.UNFOLDED.defaultAppearance(),
        )
        assertNotEquals(
            AdaptiveStageAppearanceEditorTarget.FOLDED.label(),
            AdaptiveStageAppearanceEditorTarget.UNFOLDED.label(),
        )
    }
}
