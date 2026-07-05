package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSurfaceTextTest {
    @Test
    fun emptySearchTextReflectsContentFilters() {
        assertEquals(
            "No apps found for \"camera\"",
            searchEmptyText(
                query = " camera ",
                filters = AppSearchFilters(content = setOf(AppSearchContentFilter.APPS)),
            ),
        )
        assertEquals(
            "No shortcuts found for \"camera\"",
            searchEmptyText(
                query = "camera",
                filters = AppSearchFilters(content = setOf(AppSearchContentFilter.SHORTCUTS)),
            ),
        )
        assertEquals(
            "No results found for \"camera\"",
            searchEmptyText(
                query = "camera",
                filters =
                    AppSearchFilters(
                        content = setOf(AppSearchContentFilter.APPS, AppSearchContentFilter.SHORTCUTS),
                    ),
            ),
        )
    }

    @Test
    fun emptySearchTextKeepsDisabledFilterGuidance() {
        assertEquals(
            "Enable apps or shortcuts to search",
            searchEmptyText(
                query = "camera",
                filters = AppSearchFilters(content = emptySet()),
            ),
        )
        assertEquals(
            "Enable a profile to search",
            searchEmptyText(
                query = "camera",
                filters = AppSearchFilters(profiles = emptySet()),
            ),
        )
        assertEquals(
            "No shortcuts match the selected filters",
            searchEmptyText(
                query = "",
                filters = AppSearchFilters(content = setOf(AppSearchContentFilter.SHORTCUTS)),
            ),
        )
    }

    @Test
    fun showsFilterResetWhenSearchFiltersDifferFromDefaults() {
        assertFalse(shouldShowSearchFilterReset(AppSearchFilters()))
        assertTrue(
            shouldShowSearchFilterReset(
                AppSearchFilters(content = setOf(AppSearchContentFilter.APPS, AppSearchContentFilter.SHORTCUTS)),
            ),
        )
        assertTrue(
            shouldShowSearchFilterReset(
                AppSearchFilters(profiles = setOf(AppProfileType.PERSONAL, AppProfileType.WORK)),
            ),
        )
    }
}
