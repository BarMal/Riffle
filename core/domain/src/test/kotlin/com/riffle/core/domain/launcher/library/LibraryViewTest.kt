package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.AppDrawerPresentation
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryViewTest {
    @Test
    fun theDefaultIsCategories() {
        assertEquals(LibraryView.Categories, LibraryView.DEFAULT)
        assertEquals(LibraryView.Categories, AppDrawerSettings().libraryViewFor(HomeLayoutDeviceClass.PHONE))
    }

    @Test
    fun storedIdsRoundTripAndNeverChange() {
        assertEquals(
            listOf("categories", "alpha_list", "grid"),
            LibraryView.entries.map { it.storedId },
        )
        LibraryView.entries.forEach { view -> assertEquals(view, LibraryView.fromStoredId(view.storedId)) }
        assertNull(LibraryView.fromStoredId("carousel"))
        assertNull(LibraryView.fromStoredId(null))
    }

    @Test
    fun theOldPresentationMapsListToAlphaListAndIconsToGrid() {
        assertEquals(LibraryView.AlphaList, LibraryView.decode("LIST"))
        assertEquals(LibraryView.Grid, LibraryView.decode("ICONS"))
        assertEquals(LibraryView.AlphaList, LibraryView.fromLegacy(AppDrawerPresentation.LIST))
        assertEquals(LibraryView.Grid, LibraryView.fromLegacy(AppDrawerPresentation.ICONS))
    }

    @Test
    fun anythingElseDecodesToCategories() {
        assertEquals(LibraryView.Categories, LibraryView.decode(null))
        assertEquals(LibraryView.Categories, LibraryView.decode(""))
        assertEquals(LibraryView.Categories, LibraryView.decode("CARDS"))
        assertEquals(LibraryView.Categories, LibraryView.decode("categories"))
    }

    @Test
    fun theViewIsPerDeviceClassAndLandscapeSharesThePhone() {
        val settings =
            AppDrawerSettings()
                .withLibraryView(HomeLayoutDeviceClass.PHONE, LibraryView.Grid)
                .withLibraryView(HomeLayoutDeviceClass.TABLET, LibraryView.AlphaList)

        assertEquals(LibraryView.Grid, settings.libraryViewFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(LibraryView.Grid, settings.libraryViewFor(HomeLayoutDeviceClass.PHONE_LANDSCAPE))
        assertEquals(LibraryView.AlphaList, settings.libraryViewFor(HomeLayoutDeviceClass.TABLET))
        assertEquals(LibraryView.Categories, settings.libraryViewFor(HomeLayoutDeviceClass.FOLDABLE))
    }
}
