package com.riffle.core.domain.launcher.apps

/**
 * A package's most recent foreground-use timestamp, supplied by a platform-specific source.
 *
 * The package identifier intentionally does not imply a launch activity or user profile. Android
 * usage statistics only provide package-level recency; callers must resolve it against the
 * currently available installed-app catalogue before displaying or launching an app.
 */
data class RecentAppUsage(
    val packageName: AppPackageName,
    val lastUsedAtMillis: Long,
)

/**
 * Provides package-level app usage ordered from most to least recently used.
 *
 * An empty list represents unavailable, denied, or empty platform usage data.
 */
fun interface RecentAppRepository {
    fun recentAppUsages(): List<RecentAppUsage>
}
