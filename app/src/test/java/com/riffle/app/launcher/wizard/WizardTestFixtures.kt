package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardReducer
import com.riffle.core.domain.launcher.workspace.wizard.WizardState

/** Shared values for the wizard's JVM and Compose tests: the built-in sources, one workspace, a fake store. */
internal object WizardTestFixtures {
    /** The built-in sources with the capabilities the app's adapters declare (`BuiltInItemSources`). */
    val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS, setOf(SourceCapability.GROUPABLE, SourceCapability.SEARCHABLE)),
            SourceDescriptor(SourceIds.RECENT_APPS),
            SourceDescriptor(SourceIds.QUICK_ACTIONS, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(
                SourceIds.NOTIFICATIONS,
                setOf(SourceCapability.GROUPABLE, SourceCapability.ACTIONABLE, SourceCapability.PRIVACY_SENSITIVE),
            ),
            SourceDescriptor(SourceIds.MEDIA, setOf(SourceCapability.PRIVACY_SENSITIVE)),
            SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.PRIVACY_SENSITIVE)),
            SourceDescriptor(SourceIds.RSS, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(SourceIds.SEARCH, setOf(SourceCapability.SEARCHABLE)),
            SourceDescriptor(SourceIds.ICS, setOf(SourceCapability.PRIVACY_SENSITIVE, SourceCapability.GROUPABLE)),
        )

    val phone = HomeLayoutDeviceClass.PHONE

    /** Calendar access still to grant, everything else granted. */
    val calendarPending: Map<SourceId, SourceAccess> = mapOf(SourceIds.CALENDAR to SourceAccess.REQUIRED)

    fun sources(access: Map<SourceId, SourceAccess> = calendarPending): List<SourceChoice> =
        SourceChoices.build(descriptors, access)

    fun ids(prefix: String = "id"): WorkspaceIdFactory {
        var n = 0
        return WorkspaceIdFactory { "$prefix-${++n}" }
    }

    val homeId = WorkspaceId("home")

    val appsPage =
        PageContainer(
            ContainerId("apps"),
            PageContent.Bound(LensBinding(Lens(listOf(SourceIds.ALL_APPS)), ExpressionKind.ICON_GRID)),
        )

    val home = Workspace(id = homeId, name = "Home", pages = listOf(appsPage))

    val layout = LayoutWorkspaces(workspaces = listOf(home), activeId = homeId, defaultId = homeId)

    val set = WorkspaceSet(mapOf(phone to layout))

    fun app(pkg: String) =
        AppPick(AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main")), pkg.replaceFirstChar { it.uppercase() })

    val apps = listOf(app("maps"), app("music"), app("phone"))

    fun context(
        entry: WizardEntry = WizardEntry.Editor(homeId),
        layout: LayoutWorkspaces = this.layout,
        sources: List<SourceChoice> = sources(),
    ): WizardContext = WizardContext(entry, sources, layout)

    /** [actions] through the domain reducer from [start]. */
    fun run(
        context: WizardContext,
        start: WizardState,
        vararg actions: WizardAction,
    ): WizardState = actions.fold(start) { state, action -> WizardReducer.reduce(state, action, context) }
}

/** An in-memory [WizardStores] that records every write. */
internal class FakeWizardStores(
    var set: WorkspaceSet? = WizardTestFixtures.set,
    var pool: PlacedItemPool? = PlacedItemPool(),
    override var homeReadOnly: Boolean = false,
    override var holdingItemEdits: Boolean = false,
) : WizardStores {
    val savedSets = mutableListOf<WorkspaceSet>()
    val savedPools = mutableListOf<PlacedItemPool>()

    override fun workspaces(): WorkspaceSet? = set

    override fun saveWorkspaces(set: WorkspaceSet) {
        savedSets += set
        this.set = set
    }

    override fun pool(layout: HomeLayoutDeviceClass): PlacedItemPool? = pool

    override fun savePool(
        layout: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean {
        if (this.pool == null) return false
        savedPools += pool
        this.pool = pool
        return true
    }
}
