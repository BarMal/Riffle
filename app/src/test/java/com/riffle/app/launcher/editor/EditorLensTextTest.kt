package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.editor.AdoptOffer
import com.riffle.core.domain.launcher.workspace.editor.ExpressionChoice
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.SavedLensBlock
import com.riffle.core.domain.launcher.workspace.editor.SavedLensChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLensTextTest {
    private val notes = Lens(listOf(SourceIds.NOTIFICATIONS), group = LensGroup.ByGroupKey)
    private val choice = SavedLensChoice(SavedLens(LensId("a"), "Notes by app", notes), usedIn = 2)

    @Test
    fun aRowSummaryNamesSourcesShapeAndUse() {
        assertEquals("Notifications · grouped · used in 2 places", EditorLensText.summary(choice))
        val flat = choice.copy(saved = choice.saved.copy(lens = Lens(listOf(SourceIds.ALL_APPS))), usedIn = 0)
        assertEquals("All apps · flat · not used", EditorLensText.summary(flat))
    }

    @Test
    fun theSpokenSummaryReadsNameSummaryReasonAndPosition() {
        assertEquals(
            "Notes by app. Notifications · grouped · used in 2 places. Saved lens 2 of 5",
            EditorLensText.spoken(choice, reason = null, position = 2, total = 5),
        )
        val spoken = EditorLensText.spoken(choice, "Uses Calendar, which is not available", 1, 3)
        assertTrue(spoken, "Not available here: Uses Calendar, which is not available" in spoken)
    }

    @Test
    fun aDisabledChoiceKeepsItsDomainReason() {
        assertEquals(
            "Uses Calendar, which is not available",
            EditorLensReasons.savedLens(SavedLensBlock.UnavailableSource(SourceIds.CALENDAR)),
        )
        assertEquals(
            "Can only be changed in Settings > Saved lenses",
            EditorLensReasons.savedLens(SavedLensBlock.Unsupported),
        )
        assertEquals("Nothing here can draw it", EditorLensReasons.savedLens(SavedLensBlock.CannotDraw(null)))
        val nearest = ExpressionChoice(ExpressionKind.CARD, enabled = false)
        assertEquals("Nothing here can draw it", EditorLensReasons.savedLens(SavedLensBlock.CannotDraw(nearest)))
    }

    @Test
    fun theOfferAndTheConfirmationsCountContainers() {
        val one = AdoptOffer(LensId("a"), "Latest", 1)
        assertEquals(
            "Use “Latest” in the 1 other container with an identical lens?",
            EditorLensMessages.offer(one),
        )
        assertEquals(
            "Use “Latest” in the 3 other containers with an identical lens?",
            EditorLensMessages.offer(one.copy(count = 3)),
        )
        assertEquals(
            "“Latest” is now used in the 3 other containers. Undo reverts them together.",
            EditorLensMessages.adopted("Latest", 3),
        )
    }

    @Test
    fun theSuggestedNameFollowsSourcesAndPresetAndFitsTheLibrary() {
        assertEquals("Notifications", EditorLensNames.suggest(notes, LensPreset.EVERYTHING))
        assertEquals("Notifications, newest first", EditorLensNames.suggest(notes, LensPreset.NEWEST_FIRST))
        assertEquals("Notifications", EditorLensNames.suggest(notes, null))
        val many = Lens(listOf(SourceIds.NOTIFICATIONS, SourceIds.CALENDAR, SourceIds.ALL_APPS, SourceIds.MEDIA))
        assertTrue(EditorLensNames.suggest(many, LensPreset.GROUPED_BY_DAY).length <= MAX_SAVED_LENS_NAME)
    }

    @Test
    fun libraryProblemsUseTheSettingsPageWording() {
        assertEquals("A saved lens needs a name.", EditorLensMessages.libraryProblem(LibraryProblem.BLANK_NAME))
        assertTrue("100" in EditorLensMessages.libraryProblem(LibraryProblem.LIBRARY_FULL))
        assertEquals("12 of $MAX_SAVED_LENS_NAME", EditorLensText.nameCounter(12))
    }

    @Test
    fun overviewAndDetachWording() {
        assertEquals("Saved lens: Notes by app", EditorLensText.overviewLine("Notes by app"))
        assertEquals("Saved lens missing: using a copy", EditorLensText.overviewLine(null))
        assertEquals(
            "Detach Page 2 from saved lens Notes by app",
            EditorLensText.detachDescription("Notes by app", "Page 2"),
        )
        assertEquals(
            "Detach Dock section from saved lens that is missing",
            EditorLensText.detachDescription(null, "Dock section"),
        )
    }
}
