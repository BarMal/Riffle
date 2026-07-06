package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDockJsonCodecTest {
    @Test
    fun roundTripsNonAppDockItems() {
        val camera = appShortcut(id = "camera")
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = listOf(camera),
            )
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(items = listOf(folder, widget)),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(listOf(folder, widget), decodedLayout.dock.items)
    }

    private fun appShortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:$id:1"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )
}
