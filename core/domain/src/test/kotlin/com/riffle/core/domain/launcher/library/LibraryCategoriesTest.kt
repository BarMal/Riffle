package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryCategoriesTest {
    private fun app(
        label: String,
        category: String? = null,
        pkg: String = "com.example.${label.lowercase()}",
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName(pkg), AppActivityName(".Main")),
        label = label,
        category = category,
    )

    @Test
    fun groupsByThePlatformCategoryAndFallsBackToOther() {
        val tiles =
            LibraryCategories.from(
                listOf(
                    app("Chat", "Social"),
                    app("Mail"),
                    app("Maps", "Maps"),
                    app("Feed", "Social"),
                    app("Blank", "  "),
                ),
            )

        assertEquals(listOf("Social", "Maps", LibraryCategories.OTHER_KEY), tiles.map { it.key })
        assertEquals(listOf("Chat", "Feed"), tiles.first().apps.map { it.label })
        assertEquals(listOf("Blank", "Mail"), tiles.last().apps.map { it.label })
        assertTrue(tiles.last().isOther)
    }

    @Test
    fun otherIsAlwaysLastAndUnknownCategoriesFollowThePreferredOnesAlphabetically() {
        val tiles =
            LibraryCategories.from(
                listOf(app("A", null), app("B", "Zebra"), app("C", "Audio"), app("D", "Aardvark")),
            )

        assertEquals(listOf("Audio", "Aardvark", "Zebra", LibraryCategories.OTHER_KEY), tiles.map { it.key })
    }

    @Test
    fun appsInATileAreSortedAZIgnoringCase() {
        val tiles = LibraryCategories.from(listOf(app("beta", "Game"), app("Alpha", "Game"), app("Gamma", "Game")))

        assertEquals(listOf("Alpha", "beta", "Gamma"), tiles.single().apps.map { it.label })
    }

    @Test
    fun noAppsMakeNoTiles() {
        assertTrue(LibraryCategories.from(emptyList()).isEmpty())
    }
}
