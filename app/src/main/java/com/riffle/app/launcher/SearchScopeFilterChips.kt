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
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import com.riffle.core.domain.launcher.apps.InstalledApp

@Composable
internal fun SearchFilterChips(
    filters: AppSearchFilters,
    installedApps: List<InstalledApp>,
    onContentFilterToggled: (AppSearchContentFilter) -> Unit,
    onProfileFilterToggled: (AppProfileType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppSearchContentFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter in filters.content,
                onClick = { onContentFilterToggled(filter) },
                label = { Text(text = filter.label) },
            )
        }

        val profileOptions = searchProfileFilterOptionsFor(apps = installedApps, selectedProfiles = filters.profiles)
        if (profileOptions.isNotEmpty()) {
            profileOptions.forEach { option ->
                FilterChip(
                    selected = option.profileType in filters.profiles,
                    onClick = { onProfileFilterToggled(option.profileType) },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

internal data class SearchProfileFilterOption(
    val profileType: AppProfileType,
    val count: Int,
) {
    val label: String = "${profileType.label} ($count)"
}

internal fun searchProfileFilterOptionsFor(
    apps: List<InstalledApp>,
    selectedProfiles: Set<AppProfileType> = AppProfileType.entries.toSet(),
): List<SearchProfileFilterOption> {
    return AppProfileType.entries.map { profileType ->
        SearchProfileFilterOption(
            profileType = profileType,
            count = apps.count { app -> app.identity.profile.type == profileType },
        )
    }.filter { option -> option.count > 0 || option.profileType in selectedProfiles }
}

internal val AppSearchContentFilter.label: String
    get() =
        when (this) {
            AppSearchContentFilter.APPS -> "Apps"
            AppSearchContentFilter.SHORTCUTS -> "Shortcuts"
        }

internal val AppProfileType.label: String
    get() =
        when (this) {
            AppProfileType.PERSONAL -> "Personal"
            AppProfileType.WORK -> "Work"
            AppProfileType.PRIVATE -> "Private"
        }
