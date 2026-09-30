package com.riffle.core.domain.launcher.workspace

// Lens builders for the migrated workspaces; every lens references a source by SourceIds only.

internal fun homeGridLens(pageId: String) =
    Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID), filter = LensFilter.GroupKeyIs(pageId))

internal fun appsLens() = sourceLens(SourceIds.ALL_APPS, LensSort(LensSortField.TITLE))

internal fun appsByGroup() = appsLens().copy(group = LensGroup.ByGroupKey)

internal fun profileLens(profile: String) =
    appsLens().copy(filter = LensFilter.ExtEquals(WorkspaceSourceIds.APP_PROFILE_EXT, ItemExtValue.Text(profile)))

internal fun recentAppsLens() = sourceLens(SourceIds.RECENT_APPS, newestFirst())

internal fun favouriteAppsLens() = sourceLens(WorkspaceSourceIds.FAVOURITE_APPS)

internal fun frequentAppsLens() = sourceLens(WorkspaceSourceIds.FREQUENT_APPS)

internal fun notificationsLens() = sourceLens(SourceIds.NOTIFICATIONS, newestFirst())

private fun sourceLens(
    source: SourceId,
    sort: LensSort = LensSort(),
) = Lens(sources = listOf(source), sort = sort)

private fun newestFirst() = LensSort(LensSortField.TIME, SortDirection.DESCENDING)
