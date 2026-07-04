package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        appProfileFiltersFor(
            apps = apps,
            selectedFilter = selectedFilter,
        ).forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

internal fun appProfileFiltersFor(
    apps: List<InstalledApp>,
    selectedFilter: AppDrawerProfileFilter = AppDrawerProfileFilter.ALL,
): List<AppDrawerProfileFilter> {
    if (apps.isEmpty()) {
        return listOf(AppDrawerProfileFilter.ALL, selectedFilter).distinct()
    }

    val availableProfileTypes = apps.map { app -> app.identity.profile.type }.toSet()
    val availableFilters =
        AppDrawerProfileFilter.entries.filter { filter ->
            filter == AppDrawerProfileFilter.ALL || filter.profileType in availableProfileTypes
        }

    return (availableFilters + selectedFilter).distinct()
}

internal val AppDrawerProfileFilter.label: String
    get() =
        when (this) {
            AppDrawerProfileFilter.ALL -> "All"
            AppDrawerProfileFilter.PERSONAL -> "Personal"
            AppDrawerProfileFilter.WORK -> "Work"
            AppDrawerProfileFilter.PRIVATE -> "Private"
        }

private val AppDrawerProfileFilter.profileType: AppProfileType?
    get() =
        when (this) {
            AppDrawerProfileFilter.ALL -> null
            AppDrawerProfileFilter.PERSONAL -> AppProfileType.PERSONAL
            AppDrawerProfileFilter.WORK -> AppProfileType.WORK
            AppDrawerProfileFilter.PRIVATE -> AppProfileType.PRIVATE
        }
