package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.containers.ContainerActions
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/** The platform side of item actions, so the routing below is testable without a device. */
internal interface ItemLaunchPort {
    fun launchActivity(identity: AppIdentity): Boolean

    /** An app target that does not name an activity (notifications): the package's launcher entry. */
    fun launchPackage(packageName: String): Boolean

    fun launchShortcut(shortcut: AppShortcut): Boolean

    fun openDeepLink(uri: String): Boolean

    /** Dismisses the notification with this key; false when it could not be. */
    fun dismissNotification(key: String): Boolean
}

/**
 * What a tap on an item or one of its actions does in the workspace preview: Open launches the item's
 * target through the existing launchers, Dismiss clears a notification item. Other actions (reply, snooze,
 * custom) have no handler yet and are ignored. Never logs or stores item content.
 */
internal class WorkspaceItemActions(private val port: ItemLaunchPort) {
    fun containerActions(): ContainerActions = ContainerActions(onItemClick = ::open, onAction = ::perform)

    fun perform(
        item: Item,
        action: ItemAction,
    ) {
        when (action) {
            is ItemAction.Open -> open(item)
            is ItemAction.Dismiss -> dismiss(item)
            else -> Unit
        }
    }

    fun open(item: Item) {
        when (val target = item.target) {
            is ItemTarget.App -> openApp(item, target)
            is ItemTarget.Shortcut -> openShortcut(item, target)
            is ItemTarget.DeepLink -> port.openDeepLink(target.uri)
            is ItemTarget.Intent, ItemTarget.None -> Unit
        }
    }

    private fun dismiss(item: Item) {
        val notificationItem = item.sourceId == SourceIds.NOTIFICATIONS || item.sourceId == SourceIds.MEDIA
        val key = notificationKey(item)
        if (notificationItem && key != null) port.dismissNotification(key)
    }

    private fun openApp(
        item: Item,
        target: ItemTarget.App,
    ) {
        val identity = (item.icon?.let(ItemImageKeyParser::parse) as? ParsedImageKey.ActivityIcon)?.identity
        if (identity == null) port.launchPackage(target.packageName) else port.launchActivity(identity)
    }

    private fun openShortcut(
        item: Item,
        target: ItemTarget.Shortcut,
    ) {
        val identity = (item.icon?.let(ItemImageKeyParser::parse) as? ParsedImageKey.ActivityIcon)?.identity
        if (identity != null) {
            port.launchShortcut(
                AppShortcut(AppShortcutId(target.shortcutId), identity, shortLabel = item.title.orEmpty()),
            )
        }
    }

    /** Notification item ids are `<source>:<profile>:<key>`; the key is what the listener dismisses by. */
    private fun notificationKey(item: Item): String? {
        val prefix = "${item.sourceId.value}:"
        val rest = item.id.value.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        val profile = (item.target as? ItemTarget.App)?.profileId
        return if (rest != null && profile != null && rest.startsWith("$profile:")) {
            rest.removePrefix("$profile:").takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }
}
