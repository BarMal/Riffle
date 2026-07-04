package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppSearchFilters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSurfaceTextTest {
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
