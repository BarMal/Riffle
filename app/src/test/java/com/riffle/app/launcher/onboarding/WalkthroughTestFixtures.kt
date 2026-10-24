package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolDock
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.preset.PresetInstaller
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughContext

/** Shared fixtures for the onboarding tests: a fresh phone layout and pool, apps, and sources with access. */
internal object WalkthroughFixtures {
    val PHONE = HomeLayoutDeviceClass.PHONE
    val MIGRATED = WorkspaceId("ws:phone:standard_app_drawer")

    /** The built-in sources with the capabilities the app's adapters declare (`BuiltInItemSources`). */
    val DESCRIPTORS =
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

    val NOTHING_GRANTED: Map<SourceId, SourceAccess> =
        mapOf(
            SourceIds.NOTIFICATIONS to SourceAccess.REQUIRED,
            SourceIds.MEDIA to SourceAccess.REQUIRED,
            SourceIds.CALENDAR to SourceAccess.REQUIRED,
            SourceIds.RECENT_APPS to SourceAccess.REQUIRED,
        )

    fun sources(access: Map<SourceId, SourceAccess> = emptyMap()): List<SourceChoice> =
        SourceChoices.build(DESCRIPTORS, access)

    fun ids(prefix: String = "id"): WorkspaceIdFactory {
        var n = 0
        return WorkspaceIdFactory { "$prefix-${++n}" }
    }

    fun app(pkg: String): AppPick =
        AppPick(
            AppIdentity(AppPackageName("com.$pkg"), AppActivityName("com.$pkg.Main")),
            pkg.replaceFirstChar(Char::uppercase),
        )

    fun installed(vararg pkgs: String): List<InstalledApp> =
        pkgs.map { app(it) }.map { InstalledApp(it.identity, it.label) }

    /** What a fresh install holds: the seeded Nova default and nothing else. */
    fun freshLayout(): LayoutWorkspaces {
        val seeded = PresetInstaller.newInstallLayout(PHONE, ids("seed"))
        return seeded.replace(seeded.activeId) { it.copy(presetId = WorkspacePresets.DEFAULT_ID) }
    }

    fun freshSet(): WorkspaceSet = WorkspaceSet().withLayout(PHONE, freshLayout())

    /** A fresh install's pool: the default migrated home page and a dock pinning the phone app. */
    fun freshPool(): PlacedItemPool {
        val phone = PoolApp(PoolItemId("pin-phone"), app("phone").identity, "Phone")
        return PlacedItemPool(
            items = mapOf(phone.id to phone),
            arrangements =
                mapOf(MIGRATED to Arrangement(listOf(ArrangementPage(LauncherPageId("home"), GridDimensions(4, 5))))),
            dock = PoolDock(pins = listOf(phone.id)),
        )
    }

    fun context(
        origin: OnboardingOrigin = OnboardingOrigin.FRESH_INSTALL,
        access: Map<SourceId, SourceAccess> = emptyMap(),
        firstRun: Boolean = true,
    ): WalkthroughContext =
        WalkthroughContexts.build(
            WalkthroughInputs(
                origin = origin,
                firstRun = firstRun,
                sources = sources(access),
                workspaces = freshSet(),
                layoutClass = PHONE,
                pool = freshPool(),
                installedApps = installed("phone", "mail", "camera", "maps"),
            ),
        )
}

/** In-memory stores for the applier: what was written, and the guards. */
internal class FakeWalkthroughStores(
    var set: WorkspaceSet? = WalkthroughFixtures.freshSet(),
    var pools: MutableMap<HomeLayoutDeviceClass, PlacedItemPool> =
        mutableMapOf(WalkthroughFixtures.PHONE to WalkthroughFixtures.freshPool()),
    override var readOnly: Boolean = false,
    var holding: Boolean = false,
) : WalkthroughStores {
    var workspaceWrites = 0
    var poolWrites = 0

    override val loading: Boolean get() = set == null || holding

    override fun workspaces(): WorkspaceSet? = set

    override fun saveWorkspaces(set: WorkspaceSet) {
        this.set = set
        workspaceWrites++
    }

    override fun pool(layoutClass: HomeLayoutDeviceClass): PlacedItemPool? = pools[layoutClass]

    override fun savePool(
        layoutClass: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean {
        if (layoutClass !in pools) return false
        pools[layoutClass] = pool
        poolWrites++
        return true
    }
}
