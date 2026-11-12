package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AliasedSourceEnablementTest {
    private val agenda = SourceId("calendar.agenda")

    private class Memory : DisabledSourcesStore {
        var ids: Set<String> = emptySet()

        override fun read() = ids

        override fun write(ids: Set<String>) {
            this.ids = ids
        }
    }

    @Test
    fun `an alias follows the source it names, on read, write and change`() {
        val base = StoredSourceEnablement(Memory())
        val aliased = AliasedSourceEnablement(base, mapOf(agenda to SourceIds.CALENDAR))
        val changed = mutableListOf<SourceId>()
        aliased.observe { changed += it }

        assertTrue(aliased.isEnabled(agenda))
        base.setEnabled(SourceIds.CALENDAR, false)
        assertFalse(aliased.isEnabled(agenda))
        assertEquals(listOf(SourceIds.CALENDAR, agenda), changed)

        base.setEnabled(SourceIds.CALENDAR, true)
        aliased.setEnabled(agenda, false)
        assertFalse(base.isEnabled(SourceIds.CALENDAR))
        assertTrue(aliased.isEnabled(SourceIds.RSS))
    }
}
