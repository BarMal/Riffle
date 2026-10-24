package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
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
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.homeGridLens
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.Placement
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolDock
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.preset.PresetInstaller
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/** The built-in sources with the capabilities the app's adapters declare (`BuiltInItemSources`). */
internal val APP_DESCRIPTORS =
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

internal fun sourceChoices(access: Map<SourceId, SourceAccess> = emptyMap()): List<SourceChoice> =
    SourceChoices.build(APP_DESCRIPTORS, access)

/** Notification, media, calendar and usage access all still to grant. */
internal val NOTHING_GRANTED: Map<SourceId, SourceAccess> =
    mapOf(
        SourceIds.NOTIFICATIONS to SourceAccess.REQUIRED,
        SourceIds.MEDIA to SourceAccess.REQUIRED,
        SourceIds.CALENDAR to SourceAccess.REQUIRED,
        SourceIds.RECENT_APPS to SourceAccess.REQUIRED,
    )

internal fun ids(prefix: String = "id"): WorkspaceIdFactory {
    var n = 0
    return WorkspaceIdFactory { "$prefix-${++n}" }
}

internal fun app(pkg: String) = AppPick(AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main")), pkg)

/** What a fresh install holds: the seeded Nova default (no arrangement of its own) and nothing else. */
internal fun freshLayout(): LayoutWorkspaces {
    val seeded = PresetInstaller.newInstallLayout(HomeLayoutDeviceClass.PHONE, ids("seed"))
    return seeded.replace(seeded.activeId) { it.copy(presetId = WorkspacePresets.DEFAULT_ID) }
}

internal val MIGRATED = WorkspaceId("ws:phone:standard_app_drawer")
internal val MIGRATED_PAGE = LauncherPageId("home")

/** An existing user's layout: the seeded Nova default and the migrated workspace they use, which is active. */
internal fun existingLayout(): LayoutWorkspaces {
    val binding = LensBinding(homeGridLens("home"), ExpressionKind.ICON_GRID)
    val home = PageContainer(ContainerId("page:home"), PageContent.Bound(binding))
    return freshLayout().add(Workspace(MIGRATED, "Standard", listOf(home)), activate = true)
}

/**
 * A layout's pool as an existing user has it: the migrated arrangement with one app and the dock with one pin. The
 * seeded and preset workspaces borrow the migrated arrangement.
 */
internal fun existingPool(): PlacedItemPool {
    val placed = PoolApp(PoolItemId("app-maps"), app("maps").identity, "maps")
    val pinned = PoolApp(PoolItemId("pin-phone"), app("phone").identity, "phone")
    val page =
        ArrangementPage(
            MIGRATED_PAGE,
            GridDimensions(4, 5),
            listOf(Placement(placed.id, GridPlacement(GridCell(0, 0), GridSpan()))),
        )
    return PlacedItemPool(
        items = mapOf(placed.id to placed, pinned.id to pinned),
        arrangements = mapOf(MIGRATED to Arrangement(listOf(page))),
        dock = PoolDock(pins = listOf(pinned.id)),
    )
}

/** A fresh install's pool: the default (empty) migrated home page and an empty imported dock. */
internal fun freshPool(): PlacedItemPool =
    PlacedItemPool(
        arrangements = mapOf(MIGRATED to Arrangement(listOf(ArrangementPage(MIGRATED_PAGE, GridDimensions(4, 5))))),
        dock = PoolDock(),
    )
