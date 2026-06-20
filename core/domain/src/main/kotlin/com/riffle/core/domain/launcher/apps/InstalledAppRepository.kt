package com.riffle.core.domain.launcher.apps

interface InstalledAppRepository {
    fun installedApps(): List<InstalledApp>
}
