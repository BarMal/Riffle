package com.riffle.core.domain.launcher.apps

data class AppSearchFilters(
    val content: Set<AppSearchContentFilter> = setOf(AppSearchContentFilter.APPS),
    val profiles: Set<AppProfileType> = setOf(AppProfileType.PERSONAL),
) {
    fun withToggledContent(filter: AppSearchContentFilter): AppSearchFilters {
        return copy(content = content.toggled(filter))
    }

    fun withToggledProfile(profileType: AppProfileType): AppSearchFilters {
        return copy(profiles = profiles.toggled(profileType))
    }
}

enum class AppSearchContentFilter {
    APPS,
    SHORTCUTS,
}

private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) {
        this - value
    } else {
        this + value
    }
