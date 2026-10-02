package com.riffle.app.screenshots

import com.riffle.app.launcher.LensBuilderData
import com.riffle.app.launcher.LensesPageCallbacks
import com.riffle.app.launcher.LensesPageQueries
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.settings.LensDetailPlanner
import com.riffle.core.domain.launcher.workspace.settings.LensSession
import com.riffle.core.domain.launcher.workspace.settings.LensSourceChoices
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsPlanner
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus

/** Fixed saved lenses for the Settings > Saved lenses screenshots (fakes only; nothing is stored or subscribed). */
internal object LensesSettingsFixtures {
    val phone = HomeLayoutDeviceClass.PHONE
    val foldable = HomeLayoutDeviceClass.FOLDABLE
    val tabs = WorkspacesSettingsFixtures.tabs

    private val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE, SourceCapability.ACTIONABLE)),
            SourceDescriptor(SourceIds.RECENT_APPS, setOf(SourceCapability.LIVE)),
            SourceDescriptor(
                SourceIds.NOTIFICATIONS,
                setOf(SourceCapability.LIVE, SourceCapability.GROUPABLE, SourceCapability.PRIVACY_SENSITIVE),
            ),
            SourceDescriptor(SourceIds.MEDIA, setOf(SourceCapability.LIVE)),
            SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.LIVE)),
        )

    private val everythingLens = Lens(listOf(SourceIds.ALL_APPS))
    val notesLens = Lens(listOf(SourceIds.NOTIFICATIONS), group = LensGroup.ByGroupKey)
    private val recentLens =
        Lens(
            listOf(SourceIds.RECENT_APPS),
            sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING),
            limit = 5,
        )

    val everythingId = LensId("everything")
    val notesId = LensId("notes")
    val recentId = LensId("recent")
    val everything = SavedLens(everythingId, "Everything", everythingLens)
    val notes = SavedLens(notesId, "Notes by app", notesLens)
    private val recent = SavedLens(recentId, "Recent, newest first", recentLens)

    private val standard =
        Workspace(
            id = WorkspaceId("standard"),
            name = "Standard",
            pages =
                listOf(
                    PageContainer(
                        ContainerId("home-list"),
                        PageContent.Bound(LensBinding(everythingLens, ExpressionKind.LIST, everythingId)),
                    ),
                    PageSetContainer(
                        ContainerId("inbox"),
                        LensBinding(notesLens, ExpressionKind.CARD_STACK, notesId),
                    ),
                ),
        )

    val layout: LayoutWorkspaces =
        LayoutWorkspaces.single(standard).copy(library = LensLibrary(listOf(notes, recent, everything)))

    val set =
        WorkspaceSet(
            mapOf(phone to layout, foldable to LayoutWorkspaces.single(standard.copy(id = WorkspaceId("fold")))),
        )

    private val statuses =
        mapOf(
            SourceIds.ALL_APPS to SourceStatus.READY,
            SourceIds.RECENT_APPS to SourceStatus.READY,
            SourceIds.NOTIFICATIONS to SourceStatus.READY,
            SourceIds.CALENDAR to SourceStatus.NEEDS_PERMISSION,
        )

    val choices = LensSourceChoices.build(descriptors, statuses, disabled = setOf(SourceIds.MEDIA))

    val list: LensesSettingsModel = LensesSettingsPlanner.plan(set, phone)
    val empty: LensesSettingsModel = LensesSettingsPlanner.plan(set, foldable)

    fun session(
        saved: SavedLens,
        vararg edits: LensDraftAction,
    ): LensSession = edits.fold(LensSession.open(saved)) { session, edit -> session.reduce(edit, choices) }

    fun builder(
        session: LensSession,
        canEditWorkspaces: Boolean = true,
    ) = LensBuilderData(session, choices, phone, canEditWorkspaces)

    /** Callbacks that record dispatched actions and answer every query from the fixed layout. */
    fun callbacks(
        actions: MutableList<LensesSettingsAction>,
        opened: MutableList<LensId> = mutableListOf(),
        edited: MutableList<WorkspaceId> = mutableListOf(),
        session: () -> LensSession? = { null },
    ) = LensesPageCallbacks(
        onOpen = { opened += it },
        onDispatch = { actions += it },
        onEditWorkspace = { edited += it },
        queries =
            LensesPageQueries(
                detailFor = { name ->
                    session()?.let { LensDetailPlanner.plan(layout, it.id, name, it.lens, descriptors) }
                },
                copyNameFor = { name -> LensDetailPlanner.copyName(layout, session()?.id, name) },
                nameProblem = { id, name -> layout.library.nameProblem(name, id) },
                replacements = { id -> LensesSettingsPlanner.replacements(layout, id) },
                replacementImpact = { _, _ -> 1 },
                copyTargets = { id -> LensesSettingsPlanner.copyTargets(set, phone, id, listOf(phone, foldable)) },
            ),
    )
}
