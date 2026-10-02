package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.LensesMessageText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.LensLibraryResult
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.settings.LensCopyTarget
import com.riffle.core.domain.launcher.workspace.settings.LensDetailModel
import com.riffle.core.domain.launcher.workspace.settings.LensDetailPlanner
import com.riffle.core.domain.launcher.workspace.settings.LensSession
import com.riffle.core.domain.launcher.workspace.settings.LensSourceChoices
import com.riffle.core.domain.launcher.workspace.settings.LensesEnvironment
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsChange
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsPlanner
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-time announcement the Saved lenses page makes after an action: [message] is read out by the snackbar
 * and [canUndo] adds its Undo button. [id] is new for every announcement so an identical message shows again.
 */
internal data class LensesFeedback(
    val id: Long,
    val message: String,
    val canUndo: Boolean,
)

/**
 * Everything the Saved lenses page reads, as pure queries over the stored workspaces (the cached repository in the
 * app). Nothing here changes anything, requests a permission or starts a source.
 */
internal class LensesQueries(
    private val repository: WorkspaceRepository,
    private val descriptors: () -> List<SourceDescriptor>,
    private val capabilities: LayoutCapabilities = LayoutCapabilities(),
) {
    private fun layout(viewed: HomeLayoutDeviceClass) = repository.load()?.workspacesFor(viewed)

    /** The list for [viewed], or null until the repository has loaded. */
    fun model(viewed: HomeLayoutDeviceClass): LensesSettingsModel? =
        repository.load()?.let { set -> LensesSettingsPlanner.plan(set, viewed) }

    /** The other layouts [id] can be copied to, with what each holds. */
    fun copyTargets(
        viewed: HomeLayoutDeviceClass,
        id: LensId,
        available: Collection<HomeLayoutDeviceClass>,
    ): List<LensCopyTarget> =
        repository.load()?.let { set -> LensesSettingsPlanner.copyTargets(set, viewed, id, available) }.orEmpty()

    /** The lenses [id] could be replaced by when deleted. */
    fun replacements(
        viewed: HomeLayoutDeviceClass,
        id: LensId,
    ): List<SavedLens> = layout(viewed)?.let { LensesSettingsPlanner.replacements(it, id) }.orEmpty()

    /** Why [name] cannot be the name of [id] (null: it can; null [id]: a new lens). */
    fun nameProblem(
        viewed: HomeLayoutDeviceClass,
        id: LensId?,
        name: String,
    ): LibraryProblem? = layout(viewed)?.library?.nameProblem(name, id)

    /** Dry run of deleting [id] in favour of [replacement]: how many containers it could not serve. */
    fun replacementImpact(
        viewed: HomeLayoutDeviceClass,
        id: LensId,
        replacement: LensId,
    ): Int {
        val stored = layout(viewed) ?: return 0
        val result =
            LensLibraryOps.remove(
                stored,
                id,
                RemovePolicy.ReplaceWith(replacement),
                descriptors(),
                capabilities,
            )
        return (result as? LensLibraryResult.Applied)?.detached?.size ?: 0
    }

    /** The builder's source rows with Settings > Sources' status for each (read without prompting). */
    fun sourceChoices(
        statuses: Map<SourceId, SourceStatus>,
        disabled: Set<SourceId>,
    ): List<SourceChoice> = LensSourceChoices.build(descriptors(), statuses, disabled)

    /** The detail page for [session] with the typed [name], or null when nothing is open or loaded. */
    fun detail(
        viewed: HomeLayoutDeviceClass,
        session: LensSession?,
        name: String,
    ): LensDetailModel? =
        session?.let { open ->
            layout(viewed)?.let { LensDetailPlanner.plan(it, open.id, name, open.lens, descriptors(), capabilities) }
        }

    /** The name "Save as a new lens" would give the copy of [session]'s lens for the typed [name]. */
    fun copyName(
        viewed: HomeLayoutDeviceClass,
        session: LensSession?,
        name: String,
    ): String = layout(viewed)?.let { LensDetailPlanner.copyName(it, session?.id, name) } ?: name.trim()
}

/**
 * The lens being built on the detail page. It lives here, not in the composition, so a rotation or fold keeps what
 * the person was building; the page drops it with [close] (and the controller with `leave`). Edits go through the
 * same [LensDraftAction] reducer the editor's Source step uses. Holds a lens definition only; nothing is stored.
 */
internal class LensBuilderSessions(
    private val repository: WorkspaceRepository,
) {
    private var viewed: HomeLayoutDeviceClass? = null
    private val mutableSession = MutableStateFlow<LensSession?>(null)

    /** The lens being built, or null while the list shows. */
    val session: StateFlow<LensSession?> = mutableSession.asStateFlow()

    /** The layout the open builder belongs to; null while the list shows. */
    val layout: HomeLayoutDeviceClass? get() = viewed.takeIf { mutableSession.value != null }

    /** Opens the saved lens [id] of [layout] in the builder. False when it no longer exists. */
    fun open(
        layout: HomeLayoutDeviceClass,
        id: LensId,
    ): Boolean {
        val saved = repository.load()?.workspacesFor(layout)?.library?.find(id) ?: return false
        viewed = layout
        mutableSession.value = LensSession.open(saved)
        return true
    }

    /** Starts an empty lens for [layout]. */
    fun startNew(layout: HomeLayoutDeviceClass) {
        viewed = layout
        mutableSession.value = LensSession.startNew()
    }

    /** One edit of the lens being built. */
    fun edit(
        action: LensDraftAction,
        choices: List<SourceChoice>,
    ) {
        mutableSession.value = mutableSession.value?.reduce(action, choices)
    }

    /** Closes the builder without saving. */
    fun close() {
        mutableSession.value = null
        viewed = null
    }

    /** Re-reads the open lens from storage (after an Undo changed it); closes the builder if it is gone. */
    fun refresh() {
        val open = mutableSession.value
        val shown = viewed
        val id = open?.id
        if (id != null && shown != null) {
            mutableSession.value = repository.load()?.workspacesFor(shown)?.library?.find(id)?.let(LensSession::open)
        }
    }
}

/**
 * Runs the Saved lenses page's actions against the workspace [repository] and keeps the one Undo the announcement
 * offers. No Android types. All persistence goes through the repository (the cached one in the app, so the preview
 * re-renders and the dock menu re-plans through [onChanged]); nothing here reads or writes home items.
 *
 * Only the latest destructive change can be undone and any later change drops the offer, so Undo can never
 * overwrite something newer. [queries] and [builder] are the read side and the lens being built.
 */
internal class LensesSettingsController(
    private val repository: WorkspaceRepository,
    descriptors: () -> List<SourceDescriptor>,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val capabilities: LayoutCapabilities = LayoutCapabilities(),
    private val onChanged: () -> Unit = {},
) {
    private val sources = descriptors
    private var offer: LensesSettingsChange? = null
    private var announcements = 0L
    private val mutableFeedback = MutableStateFlow<LensesFeedback?>(null)

    val queries = LensesQueries(repository, descriptors, capabilities)
    val builder = LensBuilderSessions(repository)
    val feedback: StateFlow<LensesFeedback?> = mutableFeedback.asStateFlow()

    /**
     * Applies [action] to [layout] and persists it. Returns the change (not applied: nothing was stored and the
     * reason is announced). The open builder follows: it reopens on the saved lens after a save or a create and
     * closes when its lens was deleted.
     */
    fun dispatch(
        layout: HomeLayoutDeviceClass,
        action: LensesSettingsAction,
    ): LensesSettingsChange? {
        val set = repository.load() ?: return null
        val change = action.applyTo(set, layout, LensesEnvironment(sources(), capabilities, ids))
        offer = change.takeIf { it.applied && it.undoable }
        if (change.applied) {
            repository.save(change.set)
            onChanged()
            followBuilder(layout, action, change)
        }
        announce(LensesMessageText.message(change.message), canUndo = offer != null)
        return change
    }

    /** Puts back what the last destructive change replaced, if its Undo is still on offer. */
    fun undo() {
        val pending = offer ?: return
        val set = repository.load() ?: return
        offer = null
        repository.save(pending.undo(set))
        onChanged()
        builder.refresh()
        announce(UNDONE, canUndo = false)
    }

    /** The announcement was shown (or dismissed): without Undo being pressed the offer ends with it. */
    fun feedbackShown(id: Long) {
        if (mutableFeedback.value?.id == id) {
            mutableFeedback.value = null
            offer = null
        }
    }

    /** The page closed: nothing is left to announce, no Undo is on offer and no lens is being built. */
    fun leave() {
        mutableFeedback.value = null
        offer = null
        builder.close()
    }

    private fun followBuilder(
        layout: HomeLayoutDeviceClass,
        action: LensesSettingsAction,
        change: LensesSettingsChange,
    ) {
        val open = builder.session.value ?: return
        when {
            action is LensesSettingsAction.Create -> change.lensId?.let { builder.open(layout, it) }
            action is LensesSettingsAction.Save && open.id == action.id -> builder.open(layout, action.id)
            action is LensesSettingsAction.Delete && open.id == action.id -> builder.close()
        }
    }

    private fun announce(
        message: String,
        canUndo: Boolean,
    ) {
        mutableFeedback.value = LensesFeedback(++announcements, message, canUndo)
    }

    companion object {
        const val UNDONE = "Undone"
    }
}
