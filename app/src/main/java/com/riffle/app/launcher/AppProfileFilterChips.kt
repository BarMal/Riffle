package com.riffle.app.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
fun AppProfileFilterChips(
    selectedFilter: AppDrawerProfileFilter,
    onFilterSelected: (AppDrawerProfileFilter) -> Unit,
    apps: List<InstalledApp> = emptyList(),
) {
    val options =
        appProfileFilterOptionsFor(
            apps = apps,
            selectedFilter = selectedFilter,
        )

    if (options.size <= 1) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.filter == selectedFilter,
                onClick = { onFilterSelected(option.filter) },
                label = { Text(text = option.label) },
            )
        }
    }
}

internal data class AppProfileFilterOption(
    val filter: AppDrawerProfileFilter,
    val count: Int,
) {
    val label: String = "${filter.label} ($count)"
}

internal fun appProfileFilterOptionsFor(
    apps: List<InstalledApp>,
    selectedFilter: AppDrawerProfileFilter = AppDrawerProfileFilter.ALL,
): List<AppProfileFilterOption> =
    appProfileFiltersFor(
        selectedFilter = selectedFilter,
    ).map { filter ->
        AppProfileFilterOption(
            filter = filter,
            count = apps.countForProfileFilter(filter),
        )
    }

internal fun appProfileFiltersFor(selectedFilter: AppDrawerProfileFilter): List<AppDrawerProfileFilter> {
    return (AppDrawerProfileFilter.entries + selectedFilter).distinct()
}

internal fun appProfileFiltersFor(): List<AppDrawerProfileFilter> {
    return appProfileFiltersFor(selectedFilter = AppDrawerProfileFilter.ALL)
}

internal fun AppDrawerProfileFilter.availableFor(apps: List<InstalledApp>): Boolean =
    this == AppDrawerProfileFilter.ALL || apps.any { app -> app.matchesProfileFilter(this) }

internal fun AppDrawerProfileFilter.coerceAvailableFor(apps: List<InstalledApp>): AppDrawerProfileFilter =
    takeIf { filter -> filter.availableFor(apps) } ?: AppDrawerProfileFilter.ALL

internal val AppDrawerProfileFilter.label: String
    get() =
        when (this) {
            AppDrawerProfileFilter.ALL -> "All"
            AppDrawerProfileFilter.PERSONAL -> "Personal"
            AppDrawerProfileFilter.WORK -> "Work"
            AppDrawerProfileFilter.PRIVATE -> "Private"
        }

private fun List<InstalledApp>.countForProfileFilter(filter: AppDrawerProfileFilter): Int =
    count { app -> app.matchesProfileFilter(filter) }

private fun InstalledApp.matchesProfileFilter(filter: AppDrawerProfileFilter): Boolean =
    when (filter) {
        AppDrawerProfileFilter.ALL -> true
        AppDrawerProfileFilter.PERSONAL -> identity.profile.type == AppProfileType.PERSONAL
        AppDrawerProfileFilter.WORK -> identity.profile.type == AppProfileType.WORK
        AppDrawerProfileFilter.PRIVATE -> identity.profile.type == AppProfileType.PRIVATE
    }
