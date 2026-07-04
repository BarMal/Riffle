package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppSearchScope
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchScopeFilterChipsTest {
    @Test
    fun labelsSearchScopes() {
        assertEquals("Apps", AppSearchScope.APPS.label)
        assertEquals("Apps + shortcuts", AppSearchScope.APPS_AND_SHORTCUTS.label)
    }
}
