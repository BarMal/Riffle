package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut

/** The platform side of item actions (the Stream's gateway uses it), so the routing is testable without a device. */
internal interface ItemLaunchPort {
    fun launchActivity(identity: AppIdentity): Boolean

    /** An app target that does not name an activity (notifications): the package's launcher entry. */
    fun launchPackage(packageName: String): Boolean

    fun launchShortcut(shortcut: AppShortcut): Boolean

    fun openDeepLink(uri: String): Boolean

    /** Dismisses the notification with this key; false when it could not be. */
    fun dismissNotification(key: String): Boolean
}
