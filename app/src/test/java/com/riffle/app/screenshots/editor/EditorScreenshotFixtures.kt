package com.riffle.app.screenshots.editor

import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.editor.EditorAction
import com.riffle.app.launcher.editor.EditorEnvironment
import com.riffle.app.launcher.editor.WorkspaceEditorReducer
import com.riffle.app.launcher.editor.WorkspaceEditorUiState
import com.riffle.app.screenshots.expressions.ExpressionFixtures
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.StaticLensResultProvider
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * Editor states and a fixed-answer provider for the editor screenshot tests. States are reached with the real
 * reducer, so they are exactly what a user could get to; nothing subscribes and nothing is stored.
 */
internal object EditorScreenshotFixtures {
    private val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE, SourceCapability.ACTIONABLE)),
            SourceDescriptor(
                SourceIds.NOTIFICATIONS,
                setOf(SourceCapability.LIVE, SourceCapability.GROUPABLE, SourceCapability.PRIVACY_SENSITIVE),
            ),
            SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.LIVE)),
        )

    private var nextId = 0

    val environment =
        EditorEnvironment(
            sources = SourceChoices.build(descriptors, mapOf(SourceIds.CALENDAR to SourceAccess.REQUIRED)),
            ids = WorkspaceIdFactory { "fixture-${nextId++}" },
        )

    val reducer = WorkspaceEditorReducer(environment)

    /** Answers by what the lens reads: apps, notifications (flat or grouped), or a calendar that needs access. */
    val services =
        ContainerServices(
            provider = StaticLensResultProvider { lens -> outputFor(lens) },
            environment = ExpressionFixtures.environment,
        )

    private fun outputFor(lens: Lens): LensOutput =
        when {
            lens.sources.first() == SourceIds.CALENDAR -> LensOutput.PermissionRequired
            lens.group != LensGroup.None -> LensOutput.ready(ExpressionFixtures.groupedMessages())
            lens.limit == 1 -> LensOutput.ready(ExpressionFixtures.singleCard())
            lens.sources.first() == SourceIds.NOTIFICATIONS -> LensOutput.ready(ExpressionFixtures.flatMessages())
            else -> LensOutput.ready(ExpressionFixtures.flatApps())
        }

    private fun binding(
        source: SourceId,
        kind: ExpressionKind,
        group: LensGroup = LensGroup.None,
    ) = LensBinding(Lens(listOf(source), group = group), kind)

    private val appsPage =
        PageContainer(
            ContainerId("apps"),
            PageContent.Bound(binding(SourceIds.ALL_APPS, ExpressionKind.ICON_GRID)),
        )

    private val inbox =
        WidgetContainer(ContainerId("inbox"), WidgetSpan(4, 2), binding(SourceIds.NOTIFICATIONS, ExpressionKind.LIST))

    private val widgetPage =
        PageContainer(ContainerId("now"), PageContent.WidgetGrid(4, 6, listOf(WidgetPlacement(inbox, 0, 0))))

    private val perApp =
        PageSetContainer(
            ContainerId("per-app"),
            binding(SourceIds.NOTIFICATIONS, ExpressionKind.CARD_STACK, LensGroup.ByGroupKey),
        )

    val workspace = Workspace(id = WorkspaceId("home"), name = "Home", pages = listOf(appsPage, widgetPage, perApp))

    fun overview(): WorkspaceEditorUiState = reducer.start(workspace)

    /** The flow, at [step], for a "newest notifications" lens that can be a list, page or widget. */
    fun flowAt(step: EditorStep): WorkspaceEditorUiState {
        val actions =
            buildList<EditorAction> {
                add(EditorAction.StartFlow(FlowMode.Add))
                add(flow(BindingFlowAction.ToggleSource(SourceIds.NOTIFICATIONS)))
                add(flow(BindingFlowAction.ApplyPreset(LensPreset.NEWEST_FIRST)))
                if (step != EditorStep.SOURCE) {
                    add(flow(BindingFlowAction.Next))
                    add(flow(BindingFlowAction.PickExpression(ExpressionKind.LIST)))
                }
                if (step == EditorStep.CONTAINER || step == EditorStep.CONFIRM) add(flow(BindingFlowAction.Next))
                if (step == EditorStep.CONFIRM) {
                    add(flow(BindingFlowAction.PickContainer(ContainerKind.PAGE)))
                    add(flow(BindingFlowAction.Next))
                }
            }
        return run(overview(), actions)
    }

    /** The Source step with the calendar chosen: it needs access, so the lens is flagged and the preview says so. */
    fun needsAccess(): WorkspaceEditorUiState =
        run(
            overview(),
            listOf(EditorAction.StartFlow(FlowMode.Add), flow(BindingFlowAction.ToggleSource(SourceIds.CALENDAR))),
        )

    /** A second environment that also lists the Search source, so the Source step can show its query field. */
    val searchEnvironment =
        EditorEnvironment(
            sources =
                SourceChoices.build(
                    descriptors + SourceDescriptor(SourceIds.SEARCH, setOf(SourceCapability.SEARCHABLE)),
                    emptyMap(),
                ),
            ids = WorkspaceIdFactory { "search-fixture-${nextId++}" },
        )

    /**
     * The Source step with Search chosen and [query] already applied through the flow action. The field is drawn
     * unfocused and pre-filled from the draft: nothing types into it, so no cursor animation ever runs.
     */
    fun searchQuery(query: String): WorkspaceEditorUiState {
        val searchReducer = WorkspaceEditorReducer(searchEnvironment)
        val actions =
            listOf(
                EditorAction.StartFlow(FlowMode.Add),
                flow(BindingFlowAction.ToggleSource(SourceIds.SEARCH)),
                flow(BindingFlowAction.SetQuery(SourceIds.SEARCH, query)),
            )
        return actions.fold(searchReducer.start(workspace)) { state, action ->
            searchReducer.reduce(state, action).state
        }
    }

    private fun flow(action: BindingFlowAction): EditorAction = EditorAction.Flow(action)

    private fun run(
        start: WorkspaceEditorUiState,
        actions: List<EditorAction>,
    ): WorkspaceEditorUiState = actions.fold(start) { state, action -> reducer.reduce(state, action).state }
}
