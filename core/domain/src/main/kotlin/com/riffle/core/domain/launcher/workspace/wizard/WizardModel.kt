package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditContext
import com.riffle.core.domain.launcher.workspace.editor.EditRejection
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget

/** Where the wizard was opened from. Decides the target workspace, the skipped steps and the save default. */
sealed interface WizardEntry {
    /** The workspace editor's "Add page or widget" on [workspaceId]; [pageId] is a widget page to add to, if any. */
    data class Editor(
        val workspaceId: WorkspaceId,
        val pageId: ContainerId? = null,
    ) : WizardEntry

    /** Settings > Saved lenses > New: the lens is only saved to the library (no Place step, save is required). */
    data object NewSavedLens : WizardEntry

    /** A source detail page's "Use this source": [source] is pre-selected; the content goes to [workspaceId]. */
    data class FromSource(
        val source: SourceId,
        val workspaceId: WorkspaceId,
    ) : WizardEntry
}

enum class WizardStep {
    /** Choose a source, presented as content goals ("Notifications", "My apps"). */
    SOURCE,

    /** Choose what to show: filter presets and pickers (apps, feeds, search text). */
    CONTENT,

    /** Choose how it looks: valid expressions only, with a live preview. */
    LOOK,

    /** Choose where it goes: new page, widget, dock section, or save as a lens only. */
    PLACE,

    /** Name it and save it to the library (optional). */
    SAVE,

    /** Finished: apply [WizardApply.apply] and close. */
    DONE,
}

/** Everything the wizard reads that is not its own state. Read-only; nothing here is written by the reducer. */
data class WizardContext(
    val entry: WizardEntry,
    /** `SourceChoices.build(registry.descriptors(), access)`, exactly as the editor builds it. */
    val sources: List<SourceChoice>,
    /** The layout being configured: its workspaces and its saved-lens library. */
    val layout: LayoutWorkspaces,
    val capabilities: LayoutCapabilities = LayoutCapabilities(),
    /** The feeds the FEEDS picker offers (RSS and ICS feeds by group key). */
    val feeds: List<FeedOption> = emptyList(),
) {
    /** The workspace the content goes to; null for [WizardEntry.NewSavedLens] or an id the layout does not hold. */
    val workspace: Workspace?
        get() =
            when (entry) {
                is WizardEntry.Editor -> layout.find(entry.workspaceId)
                is WizardEntry.FromSource -> layout.find(entry.workspaceId)
                WizardEntry.NewSavedLens -> null
            }

    /** The known source descriptors, with `home.grid` (the pool, never in the registry) always known. */
    val descriptors: List<SourceDescriptor>?
        get() =
            sources.takeIf { it.isNotEmpty() }?.map { it.descriptor }?.let { known ->
                if (known.any {
                        it.id == WorkspaceSourceIds.HOME_GRID
                    }
                ) {
                    known
                } else {
                    known + SourceDescriptor(WorkspaceSourceIds.HOME_GRID)
                }
            }

    val edit: EditContext get() = EditContext(capabilities, descriptors)
}

/** Why the wizard should close, and what the host does next. */
sealed interface WizardExit {
    /** Cancel (or Back on the first step): nothing was changed. */
    data object Cancelled : WizardExit

    /** "Open advanced builder": open the editor's lens builder with [handoff]. Nothing was changed. */
    data class Advanced(val handoff: AdvancedHandoff) : WizardExit

    /** Done: call [WizardApply.apply] with the final state and persist its result in one save. */
    data object Finished : WizardExit
}

/**
 * The wizard's draft as the advanced builder takes it: [draft] for `BindingFlowState.draft` (or the Saved lenses page's
 * draft), the [expression] and [container] picked so far (null when not yet chosen or not a container).
 */
data class AdvancedHandoff(
    val draft: LensDraft,
    val expression: ExpressionKind? = null,
    val container: ContainerKind? = null,
)

/**
 * The user's choices so far. [setup] is null until a goal is chosen. [name] null means "use the suggestion"
 * ([WizardFlow.name]). [exit] is set when the wizard should close.
 */
data class WizardState(
    val step: WizardStep = WizardStep.SOURCE,
    val setup: GoalSetup? = null,
    val destination: Destination? = null,
    val widgetTarget: WidgetTarget? = null,
    val saveToLibrary: Boolean = false,
    val name: String? = null,
    val exit: WizardExit? = null,
)

sealed interface WizardAction {
    data class ChooseGoal(val id: ContentGoalId) : WizardAction

    data class ChooseFilter(val id: String) : WizardAction

    data class ToggleApp(val app: AppPick) : WizardAction

    data class ToggleFeed(val key: String) : WizardAction

    data class SetQuery(val text: String) : WizardAction

    data class ChooseLook(val id: String) : WizardAction

    /** [target] is only read for [Destination.WIDGET]; null takes the first accepted place. */
    data class ChooseDestination(
        val destination: Destination,
        val target: WidgetTarget? = null,
    ) : WizardAction

    data class SetSaveToLibrary(val save: Boolean) : WizardAction

    data class SetName(val name: String) : WizardAction

    /** Advances when the step is valid; on the last step before [WizardStep.DONE] it finishes. */
    data object Next : WizardAction

    /** One step back; on the first step it cancels. */
    data object Back : WizardAction

    data object Cancel : WizardAction

    /** Hands the current draft to the advanced lens builder. Ignored before a goal is chosen (unless pre-selected). */
    data object OpenAdvanced : WizardAction
}

/** Whether a goal can be chosen right now. */
enum class GoalAvailability {
    READY,

    /** Choosable; it shows "Needs access" until the permission is granted. */
    NEEDS_ACCESS,

    /** Not choosable: a source is missing or unavailable on this device, or it cannot be used from this entry. */
    UNAVAILABLE,

    /** Not choosable: the workspace already shows it (its placed home page). */
    ALREADY_THERE,
}

data class GoalChoice(
    val goal: ContentGoal,
    val availability: GoalAvailability,
    /** The permissions still to grant (empty when ready). */
    val needs: List<SourcePermission> = emptyList(),
) {
    val enabled: Boolean get() = availability == GoalAvailability.READY || availability == GoalAvailability.NEEDS_ACCESS
}

data class LookChoice(
    val look: GoalLook,
    val enabled: Boolean,
    /** Where it can go from this entry (the Place step's enabled destinations once it is chosen). */
    val destinations: List<Destination>,
)

data class DestinationChoice(
    val destination: Destination,
    val enabled: Boolean,
    /** Why the editor refuses it (disabled container destinations). */
    val rejection: EditRejection? = null,
    /** Widget only: every place the widget could go. */
    val widgetTargets: List<WidgetTarget> = emptyList(),
    /** Dock section only: the workspace already has one, which this replaces. */
    val replaces: Boolean = false,
)

/** The live preview the Look and Place steps draw with the existing hosts. Transient; never stored. */
data class WizardPreview(
    val binding: LensBinding,
    /** Draw one group per page (a page-set). */
    val perGroup: Boolean,
    /** Placed items (`home.grid`): draw the picked apps as icons, not a lens result. */
    val placed: Boolean,
    val apps: List<AppPick> = emptyList(),
)

/** Why the wizard cannot advance or apply. The UI words it (doc: copy table). */
sealed interface WizardProblem {
    data object NoGoal : WizardProblem

    data class GoalUnavailable(val goal: ContentGoalId) : WizardProblem

    data class Setup(val problem: SetupProblem) : WizardProblem

    data object NoDestination : WizardProblem

    data class DestinationRejected(val rejection: EditRejection?) : WizardProblem

    data class Name(val problem: LibraryProblem) : WizardProblem

    /** The target workspace is gone (deleted while the wizard was open). */
    data object NoWorkspace : WizardProblem
}
