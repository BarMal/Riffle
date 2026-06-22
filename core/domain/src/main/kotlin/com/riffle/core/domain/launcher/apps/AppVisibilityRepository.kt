package com.riffle.core.domain.launcher.apps

interface AppVisibilityRepository {
    fun hiddenAppIdentities(): Set<AppIdentity>

    fun hideApp(identity: AppIdentity)

    fun showApp(identity: AppIdentity)
}

fun List<InstalledApp>.withHiddenApps(hiddenAppIdentities: Set<AppIdentity>): List<InstalledApp> =
    map { app ->
        when (app.identity) {
            in hiddenAppIdentities -> app.copy(visibility = AppVisibility.HIDDEN)
            else -> app
        }
    }
