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

    /**
     * The icon of one launcher shortcut of an app: `shortcut-icon:<profile>/<package>/<shortcut id>` (the id is the
     * rest of the key, so it may contain anything, slashes included).
     */
    fun shortcutIcon(
        identity: AppIdentity,
        shortcutId: String,
    ): ItemImageHandle =
        ItemImageHandle("shortcut-icon:${identity.profile.id.value}/${identity.packageName.value}/$shortcutId")

    /** Cached artwork of one feed article, keyed by its opaque digest; resolving it never uses the network. */
    fun feedArtwork(digest: String): ItemImageHandle = ItemImageHandle("feed-art:$digest")

    fun notificationArtwork(
        profileId: AppProfileId,
        notificationKey: String,
    ): ItemImageHandle = ItemImageHandle("notification-art:${profileId.value}:$notificationKey")

    /**
     * The contact photo of a messaging notification (the sender's or conversation's person icon, when the app
     * publishes one): held in memory only, for as long as the notification is active.
     */
    fun notificationPerson(
        profileId: AppProfileId,
        notificationKey: String,
    ): ItemImageHandle = ItemImageHandle("notification-person:${profileId.value}:$notificationKey")

    /** The picture a notification carries (a big-picture style): held in memory only, as long as it is active. */
    fun notificationPicture(
        profileId: AppProfileId,
        notificationKey: String,
    ): ItemImageHandle = ItemImageHandle("notification-picture:${profileId.value}:$notificationKey")
}
