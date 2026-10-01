package com.riffle.app.launcher.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.notifications.AndroidNotificationDismissalGateway
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

/** [ItemLaunchPort] over the launcher's existing app launcher and notification dismissal gateway. */
internal class AndroidItemLaunchPort(
    private val context: Context,
    private val appLauncher: AndroidAppLauncher,
) : ItemLaunchPort {
    override fun launchActivity(identity: AppIdentity): Boolean = appLauncher.launch(identity)

    override fun launchPackage(packageName: String): Boolean =
        runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            requireNotNull(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess

    override fun launchShortcut(shortcut: AppShortcut): Boolean = appLauncher.launchShortcut(shortcut)

    override fun openDeepLink(uri: String): Boolean =
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess

    override fun dismissNotification(key: String): Boolean =
        AndroidNotificationDismissalGateway.dismissNotifications(listOf(LauncherNotificationKey(key)))
}
