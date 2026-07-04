package com.riffle.app.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppSearchScope
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
internal fun SearchFilterChips(
    searchScope: AppSearchScope,
    profileFilter: AppDrawerProfileFilter,
    installedApps: List<InstalledApp>,
    onScopeSelected: (AppSearchScope) -> Unit,
    onProfileFilterSelected: (AppDrawerProfileFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSearchScope.entries.forEach { scope ->
            FilterChip(
                selected = scope == searchScope,
                onClick = { onScopeSelected(scope) },
                label = { Text(text = scope.label) },
            )
        }

        val profileOptions =
            appProfileFilterOptionsFor(
                apps = installedApps,
                selectedFilter = profileFilter,
            )
        if (profileOptions.size > 1) {
            profileOptions.forEach { option ->
                FilterChip(
                    selected = option.filter == profileFilter,
                    onClick = { onProfileFilterSelected(option.filter) },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

internal val AppSearchScope.label: String
    get() =
        when (this) {
            AppSearchScope.APPS -> "Apps"
            AppSearchScope.APPS_AND_SHORTCUTS -> "Apps + shortcuts"
        }
