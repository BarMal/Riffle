package com.riffle.core.domain.launcher.apps

fun interface InstalledAppRepository {
    fun installedApps(): List<InstalledApp>
}
