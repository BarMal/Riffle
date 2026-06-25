package com.riffle.app.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
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

    override fun installedApps(): List<InstalledApp> =
        queryLaunchableActivities().ifEmpty { queryPackageManagerLaunchableActivities() }
            .map(mapper::map)

    override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp = appShortcutRepository.shortcutsFor(apps)

    @Suppress("DEPRECATION")
    private fun queryLaunchableActivities(): List<LaunchableActivity> =
        launcherApps
            ?.let { apps ->
                userProfiles.flatMap { user -> apps.launchableActivitiesFor(user) }
            }
            .orEmpty()

    private fun LauncherApps.launchableActivitiesFor(user: UserHandle): List<LaunchableActivity> =
        runCatching {
            getActivityList(null, user)
                .map { activity -> activity.toLaunchableActivity(user) }
        }.getOrDefault(emptyList())

    private fun LauncherActivityInfo.toLaunchableActivity(user: UserHandle): LaunchableActivity =
        LaunchableActivity(
            packageName = componentName.packageName,
            activityName = componentName.className,
            label = label?.toString().orEmpty(),
            profile = user.toAppProfile(),
            enabled = true,
        )

    @Suppress("DEPRECATION")
    private fun queryPackageManagerLaunchableActivities(): List<LaunchableActivity> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0),
                )

            else -> packageManager.queryIntentActivities(launcherIntent, 0)
        }.mapNotNull { resolveInfo -> resolveInfo.toLaunchableActivity() }

    private fun ResolveInfo.toLaunchableActivity(): LaunchableActivity? =
        activityInfo?.let { info ->
            LaunchableActivity(
                packageName = info.packageName,
                activityName = info.name,
                label = loadLabel(packageManager)?.toString().orEmpty(),
                enabled = info.enabled,
            )
        }

    private val launcherIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private val userProfiles: List<UserHandle>
        get() = userManager?.userProfiles.orEmpty().ifEmpty { listOf(Process.myUserHandle()) }
}
