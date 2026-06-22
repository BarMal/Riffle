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

@Composable
fun AppProfileFilterChips(
    selectedFilter: AppDrawerProfileFilter,
    onFilterSelected: (AppDrawerProfileFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDrawerProfileFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filter.label) },
            )
        }
    }
}

private val AppDrawerProfileFilter.label: String
    get() =
        when (this) {
            AppDrawerProfileFilter.ALL -> "All"
            AppDrawerProfileFilter.PERSONAL -> "Personal"
            AppDrawerProfileFilter.WORK -> "Work"
        }
