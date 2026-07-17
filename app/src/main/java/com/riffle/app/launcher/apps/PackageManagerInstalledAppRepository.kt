package com.riffle.app.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import com.riffle.core.domain.launcher.apps.InstalledAppRepository

class PackageManagerInstalledAppRepository(
    private val packageManager: PackageManager,
    private val launcherApps: LauncherApps? = null,
    private val userManager: UserManager? = null,
    private val mapper: PackageManagerInstalledAppMapper = PackageManagerInstalledAppMapper(),
    private val appShortcutRepository: AppShortcutRepository = AppShortcutRepository { emptyMap() },
) : InstalledAppRepository,
    AppShortcutRepository {
    constructor(
        context: Context,
        mapper: PackageManagerInstalledAppMapper = PackageManagerInstalledAppMapper(),
        appShortcutRepository: AppShortcutRepository = AppShortcutRepository { emptyMap() },
    ) : this(
        packageManager = context.packageManager,
        launcherApps = context.getSystemService(LauncherApps::class.java),
        userManager = context.getSystemService(UserManager::class.java),
        mapper = mapper,
        appShortcutRepository = appShortcutRepository,
    )

    override fun installedApps(): List<InstalledApp> = refreshResult().apps

    override fun refreshResult(): InstalledAppRefreshResult =
        launcherApps
            ?.let(::queryLauncherApps)
            ?: queryPackageManagerLaunchableActivities()

    override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp = appShortcutRepository.shortcutsFor(apps)

    private fun queryLauncherApps(apps: LauncherApps): InstalledAppRefreshResult {
        val profiles = runCatching { apps.getProfiles() }.getOrElse { return InstalledAppRefreshResult.Unavailable }
        val users = profiles.ifEmpty { listOf(Process.myUserHandle()) }
        val activities = mutableListOf<LaunchableActivity>()
        val profileContentVisibility = mutableMapOf<AppProfileId, AppProfileContentVisibility>()
        var partial = false

        users.forEach { user ->
            val profile = user.toAppProfile(userManager = userManager, launcherApps = launcherApps)
            val visibility = user.profileContentVisibility(userManager)
            profileContentVisibility[profile.id] = visibility
            if (profile.type == AppProfileType.PRIVATE && visibility != AppProfileContentVisibility.VISIBLE) {
                partial = true
                return@forEach
            }
            val profileActivities =
                runCatching {
                    apps.getActivityList(null, user)
                        .map { activity -> activity.toLaunchableActivity(user) }
                }.getOrElse {
                    partial = true
                    emptyList()
                }
            activities += profileActivities
        }

        val mappedApps = activities.map(mapper::map)
        return if (partial) {
            InstalledAppRefreshResult.Partial(mappedApps, profileContentVisibility)
        } else {
            InstalledAppRefreshResult.Authoritative(mappedApps, profileContentVisibility)
        }
    }

    private fun LauncherActivityInfo.toLaunchableActivity(user: UserHandle): LaunchableActivity =
        LaunchableActivity(
            packageName = componentName.packageName,
            activityName = componentName.className,
            label = label?.toString().orEmpty(),
            profile =
                user.toAppProfile(
                    userManager = userManager,
                    launcherApps = launcherApps,
                ),
            category = applicationInfo.launcherCategoryLabel(),
            enabled = true,
        )

    @Suppress("DEPRECATION")
    private fun queryPackageManagerLaunchableActivities(): InstalledAppRefreshResult =
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    packageManager.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(0),
                    )

                else -> packageManager.queryIntentActivities(launcherIntent, 0)
            }
                .mapNotNull { resolveInfo -> resolveInfo.toLaunchableActivity() }
                .map(mapper::map)
        }.fold(
            onSuccess = ::packageManagerFallbackRefreshResult,
            onFailure = { InstalledAppRefreshResult.Unavailable },
        )

    private fun ResolveInfo.toLaunchableActivity(): LaunchableActivity? =
        activityInfo?.let { info ->
            LaunchableActivity(
                packageName = info.packageName,
                activityName = info.name,
                label = loadLabel(packageManager)?.toString().orEmpty(),
                category = info.applicationInfo?.launcherCategoryLabel(),
                enabled = info.enabled,
            )
        }

    private val launcherIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}

private fun ApplicationInfo.launcherCategoryLabel(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        when (category) {
            ApplicationInfo.CATEGORY_GAME -> "Game"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Image"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> null
        }
    } else {
        null
    }

private val InstalledAppRefreshResult.apps: List<InstalledApp>
    get() =
        when (this) {
            is InstalledAppRefreshResult.Authoritative -> apps
            is InstalledAppRefreshResult.Partial -> apps
            InstalledAppRefreshResult.Unavailable -> emptyList()
        }

/** PackageManager only enumerates the calling profile, so this fallback is never a complete catalog. */
internal fun packageManagerFallbackRefreshResult(apps: List<InstalledApp>): InstalledAppRefreshResult =
    InstalledAppRefreshResult.Partial(apps)
