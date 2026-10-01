package com.riffle.core.recurrence

import com.riffle.core.domain.launcher.workspace.sources.ics.RecurrenceExpander

class Ical4jRecurrenceExpanderTest : RecurrenceCorpus() {
    override fun newExpander(): RecurrenceExpander = Ical4jRecurrenceExpander()
}
