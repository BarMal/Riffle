package com.riffle.core.domain.launcher.apps

fun interface InstalledAppRepository {
    fun installedApps(): List<InstalledApp>

    fun refreshResult(): InstalledAppRefreshResult =
        runCatching { InstalledAppRefreshResult.Authoritative(installedApps()) }
            .getOrElse { InstalledAppRefreshResult.Unavailable }
}

/**
 * The trust level of an installed-app query. Only an [Authoritative] snapshot may
 * change launcher-owned placement data; platform availability is routinely transient.
 */
sealed interface InstalledAppRefreshResult {
    data class Authoritative(
        val apps: List<InstalledApp>,
    ) : InstalledAppRefreshResult

    data class Partial(
        val apps: List<InstalledApp>,
    ) : InstalledAppRefreshResult

    data object Unavailable : InstalledAppRefreshResult
}
