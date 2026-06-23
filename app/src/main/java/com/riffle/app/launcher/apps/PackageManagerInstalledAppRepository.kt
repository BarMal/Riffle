package com.riffle.app.launcher.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository

class PackageManagerInstalledAppRepository(
    private val packageManager: PackageManager,
    private val mapper: PackageManagerInstalledAppMapper = PackageManagerInstalledAppMapper(),
    private val appShortcutRepository: AppShortcutRepository = AppShortcutRepository { emptyMap() },
) : InstalledAppRepository,
    AppShortcutRepository {
    override fun installedApps(): List<InstalledApp> =
        queryLaunchableActivities()
            .mapNotNull { resolveInfo -> resolveInfo.toLaunchableActivity() }
            .map(mapper::map)

    override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp = appShortcutRepository.shortcutsFor(apps)

    @Suppress("DEPRECATION")
    private fun queryLaunchableActivities(): List<ResolveInfo> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0),
                )

            else -> packageManager.queryIntentActivities(launcherIntent, 0)
        }

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
}
