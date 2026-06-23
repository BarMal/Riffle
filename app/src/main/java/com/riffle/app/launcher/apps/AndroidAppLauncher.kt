package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut

class AndroidAppLauncher(
    private val context: Context,
) {
    private val launcherApps by lazy { context.getSystemService(LauncherApps::class.java) }

    fun launch(identity: AppIdentity): Boolean =
        runCatching {
            context.startActivity(identity.launchIntent)
        }.isSuccess

    fun launchShortcut(shortcut: AppShortcut): Boolean =
        runCatching {
            launcherApps.startShortcut(
                shortcut.appIdentity.packageName.value,
                shortcut.id.value,
                null,
                null,
                Process.myUserHandle(),
            )
        }.isSuccess

    fun openAppInfo(identity: AppIdentity): Boolean =
        runCatching {
            context.startActivity(identity.appInfoIntent)
        }.isSuccess
}

internal val AppIdentity.launchIntent: Intent
    get() =
        Intent.makeMainActivity(
            ComponentName(
                packageName.value,
                activityName.value,
            ),
        ).addFlags(LAUNCHER_LAUNCH_FLAGS)

internal val AppIdentity.appInfoIntent: Intent
    get() =
        Intent(APP_INFO_ACTION)
            .setData(Uri.parse(appInfoPackageUriString()))
            .addFlags(APP_INFO_FLAGS)

internal fun AppIdentity.appInfoPackageUriString(): String = "$APP_INFO_PACKAGE_SCHEME:${packageName.value}"

internal const val LAUNCHER_LAUNCH_FLAGS: Int =
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

internal const val APP_INFO_ACTION: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS

internal const val APP_INFO_FLAGS: Int = Intent.FLAG_ACTIVITY_NEW_TASK

internal const val APP_INFO_PACKAGE_SCHEME: String = "package"
