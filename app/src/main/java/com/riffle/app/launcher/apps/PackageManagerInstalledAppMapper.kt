package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIconKey
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp

class PackageManagerInstalledAppMapper {
    internal fun map(activity: LaunchableActivity): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(activity.packageName),
                    activityName = AppActivityName(activity.activityName),
                    profile = activity.profile,
                ),
            label = activity.label.ifBlank { activity.packageName },
            iconKey = AppIconKey("${activity.packageName}/${activity.activityName}"),
            enabled = activity.enabled,
        )
}

internal data class LaunchableActivity(
    val packageName: String,
    val activityName: String,
    val label: String,
    val profile: AppProfile = AppProfile.personal(),
    val enabled: Boolean = true,
)
