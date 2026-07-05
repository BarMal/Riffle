package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerClearFiltersTest {
    @Test
    fun showsClearFiltersWhenQueryOrProfileFilterIsActive() {
        assertFalse(
            shouldShowAppDrawerClearFilters(
                query = "",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertTrue(
            shouldShowAppDrawerClearFilters(
                query = "camera",
                profileFilter = AppDrawerProfileFilter.ALL,
            ),
        )
        assertTrue(
            shouldShowAppDrawerClearFilters(
                query = "",
                profileFilter = AppDrawerProfileFilter.WORK,
            ),
        )
    }

    @Test
    fun clearFiltersResetsDrawerQueryAndProfileFilter() {
        assertEquals(
            listOf(
                LauncherShellAction.AppDrawerQueryChanged(""),
                LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.ALL),
            ),
            appDrawerClearFilterActions(),
        )
    }
}
