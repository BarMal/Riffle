package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SummonSeedTest {
    private fun app(name: String) = AppIdentity(AppPackageName("pkg.$name"), AppActivityName("pkg.$name.Main"))

    private fun shortcut(
        name: String,
        deep: Boolean = false,
    ) = AppShortcutItem(
        id = LauncherItemId("item-$name${if (deep) "-deep" else ""}"),
        appIdentity = app(name),
        label = name,
        appShortcutId = if (deep) AppShortcutId("deep") else null,
    )

    private fun layout(
        home: List<AppShortcutItem>,
        dock: List<AppShortcutItem>,
    ) = HomeLayout(
        viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
        pages =
            listOf(
                LauncherPage(LauncherPageId("p0"), grid = GridDimensions(4, 5), items = home),
            ),
        selectedPageId = LauncherPageId("p0"),
        dock = DockModel(capacity = 4, items = dock),
    )

    @Test
    fun homeComesFirstInPageOrderSkippingDeepShortcutsFoldersAndRepeats() {
        val layout =
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages =
                    listOf(
                        LauncherPage(
                            LauncherPageId("p0"),
                            grid = GridDimensions(4, 5),
                            items =
                                listOf(
                                    shortcut("maps"),
                                    FolderItem(LauncherItemId("f"), "Work", listOf(shortcut("files"))),
                                    shortcut("mail", deep = true),
                                    shortcut("maps"),
                                ),
                        ),
                        LauncherPage(
                            LauncherPageId("p1"),
                            grid = GridDimensions(4, 5),
                            items = listOf(shortcut("camera")),
                        ),
                    ),
                selectedPageId = LauncherPageId("p0"),
                dock = DockModel(capacity = 4),
            )
        assertEquals(listOf("maps", "camera").map(::app), summonSeedApps(layout))
    }

    @Test
    fun theDocksAppsComeLastSoTheRingDoesNotRepeatFavourites() {
        val seeded =
            summonSeedApps(
                layout(
                    home = listOf(shortcut("maps"), shortcut("phone"), shortcut("camera")),
                    dock = listOf(shortcut("phone"), shortcut("messages")),
                ),
            )
        assertEquals(listOf("maps", "camera", "phone", "messages").map(::app), seeded)
    }
}
