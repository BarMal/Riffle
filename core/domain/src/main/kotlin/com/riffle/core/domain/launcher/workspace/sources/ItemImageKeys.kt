package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/**
 * Key scheme for the lazy [ItemImageHandle]s source adapters emit. Keys only name an image; resolving
 * one to pixels is a platform concern that must run off the main thread.
 */
object ItemImageKeys {
    /** Launcher icon of one specific activity. */
    fun appIcon(identity: AppIdentity): ItemImageHandle =
        ItemImageHandle(
            "app-icon:${identity.profile.id.value}:${identity.packageName.value}/${identity.activityName.value}",
        )

    /** Default icon of a package, for sources that do not know the launch activity. */
    fun packageIcon(
        packageName: AppPackageName,
        profileId: AppProfileId,
    ): ItemImageHandle = ItemImageHandle("package-icon:${profileId.value}:${packageName.value}")

    fun notificationArtwork(
        profileId: AppProfileId,
        notificationKey: String,
    ): ItemImageHandle = ItemImageHandle("notification-art:${profileId.value}:$notificationKey")
}
