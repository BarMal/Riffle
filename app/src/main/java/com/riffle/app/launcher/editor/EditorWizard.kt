package com.riffle.app.launcher.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.wizard.ContentWizard
import com.riffle.app.launcher.wizard.EditorWizardResult
import com.riffle.app.launcher.wizard.WizardCommit
import com.riffle.app.launcher.wizard.WizardOutcome
import com.riffle.app.launcher.wizard.WizardPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * The editor's "Add page or widget": the content wizard over the session. Its result goes into the session as one
 * step (`EditorAction.ApplyWizard`), so the editor's Undo covers it and Done saves it as any other edit; picked apps
 * are written to the pool at once (it has its own store), unless the home refuses item edits right now.
 */
@Suppress("LongParameterList") // The editor screen's own inputs, passed through.
@Composable
internal fun EditorContentWizard(
    state: WorkspaceEditorUiState,
    open: EditorWizard,
    environment: EditorEnvironment,
    services: ContainerServices,
    dispatch: (EditorAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    support: EditorWizardSupport?,
    modifier: Modifier = Modifier,
) {
    val context = editorWizardContext(state, open, environment, support?.feeds.orEmpty())
    val placement = (support?.placement ?: WizardPlacement(HomeLayoutDeviceClass.PHONE)).copy(ids = environment.ids)
    ContentWizard(
        context = context,
        onFinished = { finished ->
            when (val result = WizardCommit.forEditor(finished, context, support?.stores, placement)) {
                is EditorWizardResult.Apply -> {
                    dispatch(EditorAction.ApplyWizard(result.edit, result.library, result.name))
                    WizardOutcome.Closed
                }
                is EditorWizardResult.Refused -> WizardOutcome.Refused(result.message)
            }
        },
        onCancelled = { dispatch(EditorAction.CloseWizard) },
        onAdvanced = { handoff -> dispatch(EditorAction.StartAdvanced(handoff)) },
        modifier = modifier,
        services = services,
        apps = support?.apps.orEmpty(),
        onRequestSourceAccess = onRequestSourceAccess,
        reducedMotion = services.environment.reducedMotion,
    )
}
