package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class PoolItemActionsTest {
    private val identity = AppIdentity(AppPackageName("com.a"), AppActivityName("com.a.Main"))
    private val launchedApps = mutableListOf<AppIdentity>()
    private val launchedShortcuts = mutableListOf<AppShortcut>()
    private val actions =
        PoolItemActions(
            launchApp = { launchedApps.add(it) },
            launchShortcut = { launchedShortcuts.add(it) },
        )

    @Test
    fun anAppItemLaunchesItsActivity() {
        assertEquals(true, actions.open(AppShortcutItem(LauncherItemId("a"), identity, "A")))
        assertEquals(listOf(identity), launchedApps)
        assertEquals(emptyList<AppShortcut>(), launchedShortcuts)
    }

    @Test
    fun aShortcutItemLaunchesTheShortcut() {
        actions.open(AppShortcutItem(LauncherItemId("s"), identity, "Compose", AppShortcutId("compose")))
        assertEquals(emptyList<AppIdentity>(), launchedApps)
        assertEquals(listOf(AppShortcut(AppShortcutId("compose"), identity, "Compose")), launchedShortcuts)
    }

    @Test
    fun theLaunchersResultIsReported() {
        val failing = PoolItemActions(launchApp = { false }, launchShortcut = { false })
        assertEquals(false, failing.open(AppShortcutItem(LauncherItemId("a"), identity, "A")))
    }
}
