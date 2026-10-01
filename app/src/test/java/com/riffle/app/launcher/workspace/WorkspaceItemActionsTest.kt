package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceItemActionsTest {
    private class RecordingPort : ItemLaunchPort {
        val calls = mutableListOf<String>()

        override fun launchActivity(identity: AppIdentity): Boolean =
            calls.add(
                "activity:${identity.activityName.value}",
            )

        override fun launchPackage(packageName: String): Boolean = calls.add("package:$packageName")

        override fun launchShortcut(shortcut: AppShortcut): Boolean = calls.add("shortcut:${shortcut.id.value}")

        override fun openDeepLink(uri: String): Boolean = calls.add("link:$uri")

        override fun dismissNotification(key: String): Boolean = calls.add("dismiss:$key")
    }

    private val port = RecordingPort()
    private val actions = WorkspaceItemActions(port)
    private val identity =
        AppIdentity(AppPackageName("com.example.mail"), AppActivityName("com.example.mail.Main"), AppProfile.personal())

    @Test
    fun openingAnAppItemLaunchesItsActivity() {
        actions.open(
            Item(
                ItemId("apps.all:personal:x"),
                SourceIds.ALL_APPS,
                ItemTarget.App("com.example.mail", "personal"),
                icon = ItemImageKeys.appIcon(identity),
            ),
        )

        assertEquals(listOf("activity:com.example.mail.Main"), port.calls)
    }

    @Test
    fun openingANotificationItemLaunchesThePackage() {
        actions.open(
            Item(
                ItemId("notifications:personal:k1"),
                SourceIds.NOTIFICATIONS,
                ItemTarget.App("com.example.chat", "personal"),
            ),
        )

        assertEquals(listOf("package:com.example.chat"), port.calls)
    }

    @Test
    fun openingAShortcutLaunchesItWithItsAppIdentity() {
        actions.open(
            Item(
                ItemId("shortcuts:personal:p:a:compose"),
                SourceIds.QUICK_ACTIONS,
                ItemTarget.Shortcut("com.example.mail", "compose", "personal"),
                icon = ItemImageKeys.appIcon(identity),
            ),
        )

        assertEquals(listOf("shortcut:compose"), port.calls)
    }

    @Test
    fun dismissClearsANotificationByItsKey() {
        val item =
            Item(ItemId("notifications:user:10:0|pkg|1"), SourceIds.NOTIFICATIONS, ItemTarget.App("pkg", "user:10"))

        actions.perform(item, ItemAction.Dismiss())

        assertEquals(listOf("dismiss:0|pkg|1"), port.calls)
    }

    @Test
    fun dismissOnANonNotificationItemDoesNothing() {
        actions.perform(
            Item(ItemId("apps.all:personal:x"), SourceIds.ALL_APPS, ItemTarget.App("pkg", "personal")),
            ItemAction.Dismiss(),
        )

        assertTrue(port.calls.isEmpty())
    }

    @Test
    fun unsupportedActionsAndTargetsAreIgnored() {
        val item = Item(ItemId("a"), SourceIds.ALL_APPS, ItemTarget.None)

        actions.perform(item, ItemAction.Reply())
        actions.perform(item, ItemAction.Snooze())
        actions.open(item)

        assertTrue(port.calls.isEmpty())
    }
}
