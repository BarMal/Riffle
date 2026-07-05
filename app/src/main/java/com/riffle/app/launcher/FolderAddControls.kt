package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
internal fun FolderAddControls(
    query: String,
    onQueryChanged: (String) -> Unit,
    selectedProfileFilter: AppDrawerProfileFilter,
    onProfileFilterSelected: (AppDrawerProfileFilter) -> Unit,
    addableApps: List<InstalledApp>,
    resultCount: Int,
    onClearFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppSearchField(
            query = query,
            onQueryChanged = onQueryChanged,
            label = "Add app",
        )
        AppProfileFilterChips(
            selectedFilter = selectedProfileFilter,
            onFilterSelected = onProfileFilterSelected,
            apps = addableApps,
        )
        Text(
            text =
                folderAddResultSummaryText(
                    totalCandidateCount = addableApps.size,
                    resultCount = resultCount,
                    query = query,
                    profileFilter = selectedProfileFilter,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FolderAddClearFiltersButton(
            query = query,
            profileFilter = selectedProfileFilter,
            onClick = onClearFilters,
        )
    }
}

@Composable
private fun FolderAddClearFiltersButton(
    query: String,
    profileFilter: AppDrawerProfileFilter,
    onClick: () -> Unit,
) {
    if (!shouldShowFolderAddClearFilters(query = query, profileFilter = profileFilter)) return

    TextButton(onClick = onClick) {
        Text(text = "Clear filters")
    }
}
