package com.riffle.core.domain.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a stored dock position, including the legacy direction-relative names layouts written
 * before the edges became absolute still hold on disk.
 */
class DockPositionFromStoredNameTest {
    @Test
    fun mapsLegacyLeadingAndTrailingToTheAbsoluteEdgesTheyWere() {
        assertEquals(DockPosition.LEFT, dockPositionFromStoredName("LEADING"))
        assertEquals(DockPosition.RIGHT, dockPositionFromStoredName("TRAILING"))
    }

    @Test
    fun readsTheCurrentNamesUnchanged() {
        DockPosition.entries.forEach { position ->
            assertEquals(position, dockPositionFromStoredName(position.name))
        }
    }

    @Test
    fun returnsNullForAnUnknownName() {
        assertNull(dockPositionFromStoredName("SIDEWAYS"))
        assertNull(dockPositionFromStoredName(""))
    }
}
