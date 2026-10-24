package com.riffle.app.screenshots.wizard

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.app.launcher.wizard.ContentWizardScreen
import com.riffle.app.launcher.wizard.WizardHolder
import com.riffle.app.launcher.wizard.WizardTestFixtures
import com.riffle.app.launcher.wizard.WizardUiState
import com.riffle.app.screenshots.editor.EditorScreenshotFixtures
import com.riffle.app.screenshots.expressions.renderExpression
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow

/**
 * Content wizard states for the screenshot tests, reached with the real reducer from the editor's entry (so exactly
 * what a user could get to). The live preview draws over the editor screenshots' fixed-answer provider; nothing
 * subscribes, requests or is stored.
 */
internal object WizardScreenshotFixtures {
    val editor: WizardContext = WizardTestFixtures.context()

    val fromSource: WizardContext =
        WizardTestFixtures.context(entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, WizardTestFixtures.homeId))

    fun at(
        context: WizardContext,
        vararg actions: WizardAction,
    ): WizardUiState = WizardUiState(WizardTestFixtures.run(context, WizardFlow.start(context), *actions))

    fun source() = at(editor)

    /** Calendar chosen while its access is still to grant: the badge, the explanation and Review access. */
    fun needsAccess() = at(editor, WizardAction.ChooseGoal(ContentGoalId.CALENDAR))

    /** Notifications: its filters and "Only these apps", one picked. */
    fun content() =
        at(
            editor,
            WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
            WizardAction.Next,
            WizardAction.ToggleApp(WizardTestFixtures.app("maps")),
        )

    fun look() = at(editor, WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS), WizardAction.Next, WizardAction.Next)

    fun place() =
        at(
            editor,
            WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
            WizardAction.Next,
            WizardAction.Next,
            WizardAction.Next,
        )

    /** My apps on the last step: the save switch on and the suggested name, unfocused (no cursor blink). */
    fun save() =
        at(
            editor,
            WizardAction.ChooseGoal(ContentGoalId.MY_APPS),
            WizardAction.Next,
            WizardAction.Next,
            WizardAction.Next,
            WizardAction.Next,
        )

    /** A finish the home refused (read-only), back on the last step with the existing notice. */
    fun refused() = WizardHolder.refused(save(), READ_ONLY_HOME_NOTICE, editor)
}

internal fun ComposeContentTestRule.renderWizard(
    ui: WizardUiState,
    context: WizardContext = WizardScreenshotFixtures.editor,
) {
    renderExpression {
        ContentWizardScreen(
            ui = ui,
            context = context,
            dispatch = {},
            services = EditorScreenshotFixtures.services,
            apps = WizardTestFixtures.apps,
        )
    }
}
