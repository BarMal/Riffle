package com.riffle.core.domain.launcher.apps

data class AppProfileSelection(
    val types: Set<AppProfileType>,
) {
    fun matches(profile: AppProfile): Boolean = profile.type in types

    fun matches(app: InstalledApp): Boolean = matches(app.identity.profile)

    companion object {
        fun all(): AppProfileSelection = AppProfileSelection(AppProfileType.entries.toSet())

        fun personal(): AppProfileSelection = AppProfileSelection(setOf(AppProfileType.PERSONAL))

        fun work(): AppProfileSelection = AppProfileSelection(setOf(AppProfileType.WORK))

        fun private(): AppProfileSelection = AppProfileSelection(setOf(AppProfileType.PRIVATE))

        fun of(vararg types: AppProfileType): AppProfileSelection = AppProfileSelection(types.toSet())
    }
}

fun Iterable<InstalledApp>.filterByProfile(selection: AppProfileSelection): List<InstalledApp> {
    return filter { app -> selection.matches(app) }
}

fun Sequence<InstalledApp>.filterByProfile(selection: AppProfileSelection): Sequence<InstalledApp> {
    return filter { app -> selection.matches(app) }
}
