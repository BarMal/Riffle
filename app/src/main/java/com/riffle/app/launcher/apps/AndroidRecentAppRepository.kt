package com.riffle.app.launcher.apps

import android.app.usage.UsageStatsManager
import android.content.Context
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.RecentAppRepository
import com.riffle.core.domain.launcher.apps.RecentAppUsage

class AndroidRecentAppRepository(
    private val usageStatsManager: UsageStatsManager?,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val lookbackMillis: Long = DEFAULT_RECENT_APP_LOOKBACK_MILLIS,
) : RecentAppRepository {
    constructor(
        context: Context,
        currentTimeMillis: () -> Long = System::currentTimeMillis,
        lookbackMillis: Long = DEFAULT_RECENT_APP_LOOKBACK_MILLIS,
    ) : this(
        usageStatsManager = context.getSystemService(UsageStatsManager::class.java),
        currentTimeMillis = currentTimeMillis,
        lookbackMillis = lookbackMillis,
    )

    override fun recentAppUsages(): List<RecentAppUsage> {
        val endTimeMillis = currentTimeMillis()
        val startTimeMillis = (endTimeMillis - lookbackMillis).coerceAtLeast(0)

        return recentAppUsagesOrEmpty(
            runCatching {
                usageStatsManager
                    ?.queryAndAggregateUsageStats(startTimeMillis, endTimeMillis)
                    .orEmpty()
                    .values
                    .map { usage ->
                        PlatformRecentAppUsage(
                            packageName = usage.packageName,
                            lastUsedAtMillis = usage.lastTimeUsed,
                        )
                    }
            },
        )
    }
}

internal data class PlatformRecentAppUsage(
    val packageName: String,
    val lastUsedAtMillis: Long,
)

internal typealias PlatformRecentAppUsages = List<PlatformRecentAppUsage>

internal fun PlatformRecentAppUsages.toRecentAppUsages(): List<RecentAppUsage> =
    asSequence()
        .filter { usage -> usage.packageName.isNotBlank() && usage.lastUsedAtMillis > 0 }
        .map { usage ->
            RecentAppUsage(
                packageName = AppPackageName(usage.packageName),
                lastUsedAtMillis = usage.lastUsedAtMillis,
            )
        }.sortedWith(
            compareByDescending<RecentAppUsage> { usage -> usage.lastUsedAtMillis }
                .thenBy { usage -> usage.packageName.value },
        )
        .toList()

internal fun recentAppUsagesOrEmpty(platformUsages: Result<PlatformRecentAppUsages>): List<RecentAppUsage> =
    platformUsages.map { usages -> usages.toRecentAppUsages() }.getOrDefault(emptyList())

internal const val DEFAULT_RECENT_APP_LOOKBACK_MILLIS = 30L * 24 * 60 * 60 * 1_000
