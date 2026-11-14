package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.HomeMode
import org.junit.Test
import org.robolectric.annotation.Config

/** The App library Home, the paged Library and the Library sheet in its three views. */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LibraryLongPressCoverageTest : LongPressCoverageBase() {
    @Test
    fun appLibraryHomeFolded() {
        show(folded(DockPosition.BOTTOM).withHomeMode(HomeMode.AppLibrary))
        assertNoDeadPoint("App library Home, folded", probe())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun appLibraryHomeUnfolded() {
        show(unfolded().withHomeMode(HomeMode.AppLibrary))
        assertNoDeadPoint("App library Home, unfolded", probe())
    }

    @Test
    fun pagedLibraryFolded() {
        show(folded(DockPosition.BOTTOM).libraryOpenAs(LibraryView.Paged))
        assertNoDeadPoint("paged Library, folded", probe())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun pagedLibraryUnfolded() {
        show(unfolded().libraryOpenAs(LibraryView.Paged))
        assertNoDeadPoint("paged Library, unfolded", probe())
    }

    @Test
    fun libraryCategoriesFolded() {
        show(folded(DockPosition.BOTTOM).libraryOpenAs(LibraryView.Categories))
        assertNoDeadPoint("Library categories, folded", probe())
    }

    @Test
    fun libraryAlphaListFolded() {
        show(folded(DockPosition.BOTTOM).libraryOpenAs(LibraryView.AlphaList))
        assertNoDeadPoint("Library A-Z, folded", probe())
    }

    @Test
    fun libraryGridFolded() {
        show(folded(DockPosition.BOTTOM).libraryOpenAs(LibraryView.Grid))
        assertNoDeadPoint("Library grid, folded", probe())
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun libraryCategoriesUnfolded() {
        show(unfolded().libraryOpenAs(LibraryView.Categories))
        assertNoDeadPoint("Library categories, unfolded", probe())
    }
}
