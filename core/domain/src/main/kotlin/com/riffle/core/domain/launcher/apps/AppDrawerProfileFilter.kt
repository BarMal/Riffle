package com.riffle.core.domain.launcher.apps

enum class AppDrawerProfileFilter {
    ALL,
    PERSONAL,
    WORK,
    PRIVATE,
}

fun AppDrawerProfileFilter.toProfileSelection(): AppProfileSelection =
    when (this) {
        AppDrawerProfileFilter.ALL -> AppProfileSelection.all()
        AppDrawerProfileFilter.PERSONAL -> AppProfileSelection.personal()
        AppDrawerProfileFilter.WORK -> AppProfileSelection.work()
        AppDrawerProfileFilter.PRIVATE -> AppProfileSelection.private()
    }

fun AppDrawerProfileFilter.matches(app: InstalledApp): Boolean = toProfileSelection().matches(app)
