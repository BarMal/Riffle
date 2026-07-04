package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchFieldTest {
    @Test
    fun clearDescriptionUsesSearchFieldLabel() {
        assertEquals("Clear search apps", searchClearContentDescription("Search apps"))
        assertEquals("Clear search hidden apps", searchClearContentDescription("Search hidden apps"))
    }
}
