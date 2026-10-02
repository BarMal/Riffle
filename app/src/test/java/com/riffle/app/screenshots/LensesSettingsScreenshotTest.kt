package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LENSES_EMPTY_TEST_TAG
import com.riffle.app.launcher.LENSES_NEW_TEST_TAG
import com.riffle.app.launcher.LENS_BREAKS_TEST_TAG
import com.riffle.app.launcher.LENS_DELETE_REPLACE_TEST_TAG
import com.riffle.app.launcher.LENS_PREVIEW_TEST_TAG
import com.riffle.app.launcher.LENS_PROBLEMS_TEST_TAG
import com.riffle.app.launcher.LENS_SAVE_DETACH_TEST_TAG
import com.riffle.app.launcher.LENS_SAVE_TEST_TAG
import com.riffle.app.launcher.LensBuilderData
import com.riffle.app.launcher.LensesPageCallbacks
import com.riffle.app.launcher.LensesSettingsContent
import com.riffle.app.launcher.lensRowMenuTestTag
import com.riffle.app.launcher.lensRowTestTag
import com.riffle.app.launcher.lensUsedByTestTag
import com.riffle.app.screenshots.editor.EditorScreenshotFixtures
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.settings.LensSession
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings > Saved lenses over fixed fake lenses (no repository, no sources): the list at compact and unfolded widths
 * (list-detail), the builder for a saved lens, a new lens and an edit that would break a page-set, and the delete,
 * copy and save-choice dialogs. No test types into a field and no dialog with a text field is opened.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LensesSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LensesSettingsAction>()
    private val opened = mutableListOf<LensId>()
    private val edited = mutableListOf<WorkspaceId>()

    @Test
    fun listCompact() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun listCompactDark() {
        render()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun listCompactLargeFont() {
        render()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun listUnfoldedShowsAPlaceholderForTheDetailPane() {
        render()

        composeRule.onNodeWithTag("lenses-detail-placeholder").assertIsDisplayed()
    }

    @Test
    fun listEmptyLayoutSaysSoAndStillOffersNew() {
        render(LensesSettingsFixtures.empty, viewed = LensesSettingsFixtures.foldable)

        composeRule.onNodeWithTag(LENSES_EMPTY_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LENSES_NEW_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun listNotLoadedYet() {
        render(null)
    }

    @Test
    fun tappingARowOpensItAndNewStartsABlankLens() {
        var created = 0
        render(
            callbacks =
                LensesSettingsFixtures.callbacks(actions, opened, edited).let {
                        base ->
                    withNew(base) { created++ }
                },
        )

        composeRule.onNodeWithTag(lensRowTestTag(LensesSettingsFixtures.notesId)).performClick()
        composeRule.onNodeWithTag(LENSES_NEW_TEST_TAG).performClick()

        assertEquals(listOf(LensesSettingsFixtures.notesId), opened)
        assertEquals(1, created)
    }

    @Test
    fun rowsAreSortedByNameAndEachIsOneElementWithASpokenSummary() {
        render(capture = false)

        composeRule.onNodeWithContentDescription("Everything. All apps · flat · used in 1 place", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Lens 2 of 3", substring = true).assertExists()
        assertEquals(
            listOf(
                "Everything",
                "Notes by app",
                "Recent, newest first",
            ),
            LensesSettingsFixtures.list.rows.map {
                it.name
            },
        )
    }

    @Test
    fun deleteAsksFirstAndOffersDetachOrReplace() {
        render(capture = false)

        composeRule.onNodeWithTag(lensRowMenuTestTag(LensesSettingsFixtures.notesId)).performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNode(hasText("Delete saved lens?") and hasAnyAncestor(isDialog())).assertExists()
        composeRule.onNodeWithTag(LENS_DELETE_REPLACE_TEST_TAG).assertExists()
        assertEquals(emptyList<LensesSettingsAction>(), actions)
        composeRule.captureScreen()

        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog()) and hasClickAction())
            .performClick()

        assertEquals(
            listOf<LensesSettingsAction>(
                LensesSettingsAction.Delete(LensesSettingsFixtures.notesId, RemovePolicy.Detach),
            ),
            actions,
        )
    }

    @Test
    fun copyToAnotherLayoutExplainsAOneTimeCopyAndConfirms() {
        render(capture = false)

        composeRule.onNodeWithTag(lensRowMenuTestTag(LensesSettingsFixtures.recentId)).performClick()
        composeRule.onNodeWithText("Copy to Foldable (unfolded)").performClick()

        composeRule.onNode(hasText("one-time copy, not a link", substring = true) and hasAnyAncestor(isDialog()))
            .assertExists()
        composeRule.captureScreen()

        composeRule.onNode(hasText("Copy") and hasAnyAncestor(isDialog()) and hasClickAction())
            .performClick()

        assertEquals(
            listOf<LensesSettingsAction>(
                LensesSettingsAction.CopyToLayout(LensesSettingsFixtures.recentId, LensesSettingsFixtures.foldable),
            ),
            actions,
        )
    }

    @Test
    fun detailCompact() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.everything))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun detailCompactDark() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.everything))
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun detailUnfoldedListDetail() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.notes))

        composeRule.onNodeWithTag(LENS_PREVIEW_TEST_TAG).assertExists()
    }

    @Test
    fun detailShowsEveryUserAndOpensTheEditorForThem() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.notes), capture = false)

        composeRule.onNodeWithTag(lensUsedByTestTag(0)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(lensUsedByTestTag(0)).performClick()

        assertEquals(listOf(WorkspaceId("standard")), edited)
    }

    @Test
    fun detailOnAnotherLayoutListsUsersWithoutAnEditButton() {
        renderDetail(
            LensesSettingsFixtures.session(LensesSettingsFixtures.notes),
            builder = { LensesSettingsFixtures.builder(it, canEditWorkspaces = false) },
        )

        composeRule.onNodeWithText("Switch Settings to this device's layout to open the editor.", substring = true)
            .performScrollTo()
            .assertExists()
    }

    @Test
    fun detailOfANewLensIsBlockedUntilItHasASource() {
        renderDetail(LensSession.startNew())

        composeRule.onNodeWithTag(LENS_PROBLEMS_TEST_TAG).assertExists()
        composeRule.onNodeWithText("Choose at least one source.", substring = true).assertExists()
    }

    @Test
    fun detailShowsSourceStatusInWords() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.everything), capture = false)

        composeRule.onAllNodesWithText("Needs access", substring = true)[0].performScrollTo().assertExists()
        composeRule.onAllNodesWithText("Off", substring = true)[0].performScrollTo().assertExists()
        composeRule.captureScreen()
    }

    @Test
    fun detailForAnEditThatBreaksAPageSetListsItAndSaveOffersAChoice() {
        val broken =
            LensesSettingsFixtures.session(
                LensesSettingsFixtures.notes,
                LensDraftAction.SetGroup(LensGroup.None),
            )
        renderDetail(broken, capture = false)

        composeRule.onNodeWithTag(LENS_BREAKS_TEST_TAG).performScrollTo().assertExists()
        composeRule.captureScreen()
        composeRule.onNodeWithTag(LENS_SAVE_TEST_TAG).performScrollTo().performClick()

        composeRule.onNode(hasText("This change affects containers") and hasAnyAncestor(isDialog())).assertExists()
        composeRule.captureScreen()

        composeRule.onNodeWithTag(LENS_SAVE_DETACH_TEST_TAG).performClick()

        val save = actions.single() as LensesSettingsAction.Save
        assertEquals(BreakPolicy.DETACH_BROKEN, save.policy)
        assertEquals(LensesSettingsFixtures.notesId, save.id)
    }

    @Test
    fun saveIsDisabledWhileNothingChanged() {
        renderDetail(LensesSettingsFixtures.session(LensesSettingsFixtures.everything), capture = false)

        composeRule.onNodeWithTag(LENS_SAVE_TEST_TAG).performScrollTo()
        composeRule.onNodeWithTag(LENS_SAVE_TEST_TAG).assertIsNotEnabled()
    }

    private fun withNew(
        base: LensesPageCallbacks,
        onNew: () -> Unit,
    ) = LensesPageCallbacks(onOpen = base.onOpen, onNew = onNew, onDispatch = base.onDispatch, queries = base.queries)

    private fun render(
        model: LensesSettingsModel? = LensesSettingsFixtures.list,
        viewed: HomeLayoutDeviceClass = LensesSettingsFixtures.phone,
        callbacks: LensesPageCallbacks = LensesSettingsFixtures.callbacks(actions, opened, edited),
        capture: Boolean = true,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Page {
                    LensesSettingsContent(
                        model = model,
                        viewed = viewed,
                        tabs = LensesSettingsFixtures.tabs,
                        builder = null,
                        services = null,
                        callbacks = callbacks,
                    )
                }
            }
        }
        if (capture) composeRule.captureScreen()
    }

    private fun renderDetail(
        session: LensSession,
        capture: Boolean = true,
        builder: (LensSession) -> LensBuilderData = {
            LensesSettingsFixtures.builder(it)
        },
    ) {
        val callbacks = LensesSettingsFixtures.callbacks(actions, opened, edited) { session }
        composeRule.setContent {
            ScreenshotBackdrop {
                Page {
                    LensesSettingsContent(
                        model = LensesSettingsFixtures.list,
                        viewed = LensesSettingsFixtures.phone,
                        tabs = LensesSettingsFixtures.tabs,
                        builder = builder(session),
                        services = EditorScreenshotFixtures.services,
                        callbacks = callbacks,
                    )
                }
            }
        }
        if (capture) composeRule.captureScreen()
    }

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) { content() }
    }
}
