package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherPageTypeTest {
    @Test
    fun classicHomePageIsTheDefaultPageType() {
        val page =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 4, rows = 5),
            )

        assertEquals(LauncherPageType.Home, page.type)
    }

    @Test
    fun allAppsPageCanBeModelledSeparatelyFromViewMode() {
        val page =
            LauncherPage(
                id = LauncherPageId("all-apps"),
                type = LauncherPageType.AllApps,
                grid = GridDimensions(columns = 4, rows = 5),
            )

        assertEquals(LauncherPageType.AllApps, page.type)
    }

    @Test
    fun generatedPageCanCarryItsPurposeInTheDomainModel() {
        val page =
            LauncherPage(
                id = LauncherPageId("today"),
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                grid = GridDimensions(columns = 4, rows = 5),
            )

        assertEquals(LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY), page.type)
    }
}
