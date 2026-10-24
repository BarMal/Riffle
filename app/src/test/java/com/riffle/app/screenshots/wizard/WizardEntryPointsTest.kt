package com.riffle.app.screenshots.wizard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.riffle.app.launcher.LENSES_NEW_ADVANCED_TEST_TAG
import com.riffle.app.launcher.LENSES_NEW_TEST_TAG
import com.riffle.app.launcher.LensesPageCallbacks
import com.riffle.app.launcher.LensesSettingsContent
import com.riffle.app.launcher.SOURCE_DETAIL_ADD_TAG
import com.riffle.app.launcher.SourceDetailScaffold
import com.riffle.app.launcher.editor.EDITOR_ADD_TEST_TAG
import com.riffle.app.launcher.editor.EditorEntry
import com.riffle.app.launcher.wizard.ContentWizard
import com.riffle.app.launcher.wizard.FakeWizardStores
import com.riffle.app.launcher.wizard.GoalOptions
import com.riffle.app.launcher.wizard.GoalOptionsCallbacks
import com.riffle.app.launcher.wizard.LookOption
import com.riffle.app.launcher.wizard.WIZARD_ADVANCED_TAG
import com.riffle.app.launcher.wizard.WIZARD_CLOSE_TAG
import com.riffle.app.launcher.wizard.WIZARD_NEXT_TAG
import com.riffle.app.launcher.wizard.WIZARD_TAG
import com.riffle.app.launcher.wizard.WizardCommit
import com.riffle.app.launcher.wizard.WizardCommitResult
import com.riffle.app.launcher.wizard.WizardOutcome
import com.riffle.app.launcher.wizard.WizardPlacement
import com.riffle.app.launcher.wizard.WizardTestFixtures
import com.riffle.app.launcher.wizard.wizardAppTag
import com.riffle.app.launcher.wizard.wizardGoalTag
import com.riffle.app.launcher.wizard.wizardLookTag
import com.riffle.app.screenshots.LensesSettingsFixtures
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotDevices
import com.riffle.app.screenshots.editor.EditorScreenshotFixtures
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.editor.LensScope
import com.riffle.core.domain.launcher.workspace.editor.LensScopeChange
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalCatalog
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.ContentRecipes
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A happy path from each of the wizard's entry points, driven by taps (no screenshots): the editor's "Add page or
 * widget" (Undo and Done cover the result), Settings > Saved lenses > New, a source page's "Use this source", and the
 * per-goal options the walkthrough embeds. Nothing requests a permission or touches platform storage.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WizardEntryPointsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val notifications =
        SourceRow(SourceIds.NOTIFICATIONS, "Notifications", "Your notifications.", SourceStatus.READY, enabled = true)

    private val placement = WizardPlacement(WizardTestFixtures.phone, ids = WizardTestFixtures.ids("ui"))

    private fun content(body: @Composable () -> Unit) {
        composeRule.setContent { ScreenshotBackdrop { body() } }
    }

    private fun next(times: Int) = repeat(times) { composeRule.onNodeWithTag(WIZARD_NEXT_TAG).performClick() }

    /** The wizard over [context], applying through [stores] as a Settings host does. */
    private fun renderHosted(
        context: WizardContext,
        stores: FakeWizardStores,
        results: MutableList<WizardCommitResult>,
        cancelled: MutableList<Unit> = mutableListOf(),
        advanced: MutableList<AdvancedHandoff> = mutableListOf(),
    ) = content {
        ContentWizard(
            context = context,
            onFinished = { state ->
                val result = WizardCommit.commit(state, context, stores, placement)
                results += result
                when (result) {
                    is WizardCommitResult.Refused -> WizardOutcome.Refused(result.message)
                    is WizardCommitResult.Saved -> WizardOutcome.Closed
                }
            },
            onCancelled = { cancelled += Unit },
            onAdvanced = { advanced += it },
            services = EditorScreenshotFixtures.services,
            apps = WizardTestFixtures.apps,
        )
    }

    @Test
    fun editorAddPageRunsTheWizardAndUndoAndDoneCoverIt() {
        var saved: Pair<Workspace, LensScopeChange>? = null
        content {
            EditorEntry(
                workspace = WizardTestFixtures.home,
                sources = WizardTestFixtures.sources(),
                services = EditorScreenshotFixtures.services,
                onSave = { workspace, change -> saved = workspace to change },
                onClose = {},
                scope = LensScope(),
            )
        }

        composeRule.onNodeWithTag(EDITOR_ADD_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(WIZARD_TAG).assertExists()
        composeRule.onNodeWithTag(wizardGoalTag(ContentGoalId.MY_APPS.key)).performClick()
        // Source, Content, Look, Place, Save (Add).
        next(5)

        composeRule.onNodeWithTag(WIZARD_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Added My apps, grid").assertExists()
        composeRule.onNodeWithText("Undo").assertIsEnabled()
        composeRule.onNodeWithText("Done").performClick()

        val (workspace, change) = checkNotNull(saved)
        assertEquals(2, workspace.pages.size)
        assertEquals(listOf("My apps, grid"), change.library?.lenses?.map { it.name })
    }

    @Test
    fun savedLensesNewOpensTheWizardAndTheAdvancedBuilderStaysOneTapAway() {
        var wizard = 0
        var builder = 0
        content {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LensesSettingsContent(
                    model = LensesSettingsFixtures.list,
                    viewed = LensesSettingsFixtures.phone,
                    tabs = LensesSettingsFixtures.tabs,
                    builder = null,
                    services = null,
                    callbacks = LensesPageCallbacks(onNew = { builder++ }, onNewWizard = { wizard++ }),
                )
            }
        }

        composeRule.onNodeWithTag(LENSES_NEW_TEST_TAG).performClick()
        composeRule.onNodeWithTag(LENSES_NEW_ADVANCED_TEST_TAG).performClick()

        assertEquals(1, wizard)
        assertEquals(1, builder)
    }

    @Test
    fun aNewSavedLensIsSavedToTheLibrary() {
        val stores = FakeWizardStores()
        val results = mutableListOf<WizardCommitResult>()
        renderHosted(WizardTestFixtures.context(entry = WizardEntry.NewSavedLens), stores, results)

        composeRule.onNodeWithTag(wizardGoalTag(ContentGoalId.MY_APPS.key)).performClick()
        // Source, Content, Look, Save (Save).
        composeRule.onNodeWithTag(WIZARD_NEXT_TAG).performClick()
        next(2)
        composeRule.onNodeWithText("Save").assertIsEnabled()
        next(1)

        assertTrue(results.single() is WizardCommitResult.Saved)
        val layout = checkNotNull(stores.set).workspacesFor(WizardTestFixtures.phone)
        assertEquals(listOf("My apps, grid"), layout.library.lenses.map { it.name })
    }

    @Test
    fun aSourcePageOffersUseThisSource() {
        var used = 0
        content {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SourceDetailScaffold(
                    row = notifications,
                    calendarAccess = CalendarAccessStatus.NOT_GRANTED,
                    places = emptyList(),
                    showLayoutNames = false,
                    currentLayout = WizardTestFixtures.phone,
                    onToggle = {},
                    onAllow = {},
                    onEdit = {},
                    onUseSource = { used++ },
                )
            }
        }

        composeRule.onNodeWithTag(SOURCE_DETAIL_ADD_TAG).performScrollTo().performClick()

        assertEquals(1, used)
    }

    @Test
    fun useThisSourceAddsToTheActiveWorkspace() {
        val stores = FakeWizardStores()
        val results = mutableListOf<WizardCommitResult>()
        val context =
            WizardTestFixtures.context(
                entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, WizardTestFixtures.homeId),
            )
        renderHosted(context, stores, results)

        // Pre-selected: Content, Look, Place, Save (Add).
        next(4)

        assertTrue(results.single() is WizardCommitResult.Saved)
        val home = checkNotNull(stores.set?.workspacesFor(WizardTestFixtures.phone)?.find(WizardTestFixtures.homeId))
        assertEquals(2, home.pages.size)
    }

    @Test
    fun cancelChangesNothingAndAdvancedHandsOverTheDraft() {
        val stores = FakeWizardStores()
        val cancelled = mutableListOf<Unit>()
        val advanced = mutableListOf<AdvancedHandoff>()
        renderHosted(WizardTestFixtures.context(), stores, mutableListOf(), cancelled, advanced)

        composeRule.onNodeWithTag(wizardGoalTag(ContentGoalId.MY_APPS.key)).performClick()
        composeRule.onNodeWithTag(WIZARD_ADVANCED_TAG).performClick()

        assertEquals(listOf(SourceIds.ALL_APPS), advanced.single().draft.sources)
        assertTrue(stores.savedSets.isEmpty())
        assertTrue(cancelled.isEmpty())
    }

    @Test
    fun closeCancels() {
        val stores = FakeWizardStores()
        val cancelled = mutableListOf<Unit>()
        renderHosted(WizardTestFixtures.context(), stores, mutableListOf(), cancelled)

        composeRule.onNodeWithTag(WIZARD_CLOSE_TAG).performClick()

        assertEquals(1, cancelled.size)
        assertTrue(stores.savedSets.isEmpty())
    }

    @Test
    fun theWalkthroughsGoalOptionsSendEachChoice() {
        val goal = ContentGoalCatalog.notifications
        val looks = mutableListOf<String>()
        val apps = mutableListOf<AppPick>()
        content {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                GoalOptions(
                    setup = ContentRecipes.defaultSetup(goal),
                    looks = goal.looks.map { LookOption(it) },
                    apps = WizardTestFixtures.apps,
                    feeds = emptyList(),
                    callbacks = GoalOptionsCallbacks(onLook = { looks += it }, onToggleApp = { apps += it }),
                )
            }
        }

        composeRule.onNodeWithTag(wizardLookTag(goal.defaultLook.id)).performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag(wizardLookTag("list")).performScrollTo().performClick()
        composeRule.onNodeWithTag(wizardAppTag("maps")).performScrollTo().performClick()

        assertEquals(listOf("list"), looks)
        assertEquals(listOf(WizardTestFixtures.app("maps")), apps)
    }
}
