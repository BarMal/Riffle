package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleKind
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsMessage
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsPlanner
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionsSettingsTextTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val rule =
        SourceExclusionRule(ExclusionRuleId("a"), SourceIds.RSS, ExclusionMatcher.Group("blog"))

    private fun row(
        enabled: Boolean = true,
        counts: Map<ExclusionRuleId, Int> = emptyMap(),
        off: Set<SourceId> = emptySet(),
    ) = ExclusionsSettingsPlanner.plan(
        LayoutExclusionRules(mapOf(phone to ExclusionRuleSet(listOf(rule.copy(enabled = enabled))))),
        phone,
        listOf(phone, HomeLayoutDeviceClass.TABLET),
        counts,
        off,
    ).rows.single()

    @Test
    fun theStatusLineIsTheCountOrWhyThereIsNone() {
        assertEquals("Hides 4 items right now", ExclusionsSettingsText.statusLine(row(counts = mapOf(rule.id to 4))))
        assertEquals("Hides 1 item right now", ExclusionsSettingsText.statusLine(row(counts = mapOf(rule.id to 1))))
        assertEquals("Hides nothing right now", ExclusionsSettingsText.statusLine(row(counts = mapOf(rule.id to 0))))
        assertEquals(
            "Turned off",
            ExclusionsSettingsText.statusLine(row(enabled = false, counts = mapOf(rule.id to 4))),
        )
        assertEquals("Source is off", ExclusionsSettingsText.statusLine(row(off = setOf(SourceIds.RSS))))
        assertNull(ExclusionsSettingsText.statusLine(row()))
    }

    @Test
    fun aRowIsSpokenAsOneSummaryWithItsPositionAndState() {
        val spoken = ExclusionsSettingsText.rowSpokenSummary(row(counts = mapOf(rule.id to 4)), 2, 5)

        assertEquals(
            "Feed blog. Feeds and groups, RSS feeds, Only on this layout. Hides 4 items right now. " +
                "Rule 2 of 5. On. Double tap to turn off",
            spoken,
        )
        assertTrue(
            ExclusionsSettingsText.rowSpokenSummary(row(enabled = false), 1, 1).endsWith("Double tap to turn on"),
        )
        assertTrue(ExclusionsSettingsText.rowSpokenSummary(row(), 1, 1).contains("Hidden count unavailable"))
    }

    @Test
    fun everyKindHasATitleAndEveryProblemAMessage() {
        ExclusionRuleKind.entries.forEach { assertFalse(ExclusionsSettingsText.kindTitle(it).isBlank()) }
        TextRuleProblem.entries.forEach { assertFalse(ExclusionsAnnouncements.problem(it).isBlank()) }
        assertEquals(
            "Use at least 3 characters (wildcards do not count).",
            ExclusionsAnnouncements.problem(TextRuleProblem.TOO_SHORT),
        )
    }

    @Test
    fun announcementsNameTheLayoutAndNeverCarryItemText() {
        val name = { layout: HomeLayoutDeviceClass -> "L-${layout.name}" }

        assertEquals(
            "Hidden on L-PHONE: this feed",
            ExclusionsAnnouncements.message(ExclusionsMessage.Hidden(phone, "this feed"), name),
        )
        assertEquals(
            "Added to 4 other layouts",
            ExclusionsAnnouncements.message(ExclusionsMessage.AppliedToAll(4), name),
        )
        assertEquals(
            "Added to 1 other layout",
            ExclusionsAnnouncements.message(ExclusionsMessage.AppliedToAll(1), name),
        )
        assertEquals("Nothing changed.", ExclusionsAnnouncements.message(ExclusionsMessage.NothingToDo, name))
    }

    @Test
    fun theLayoutSummaryCountsRules() {
        assertEquals("Rules for Phone: no rules", ExclusionsSettingsText.layoutSummary("Phone", 0))
        assertEquals("Rules for Phone: 1 rule", ExclusionsSettingsText.layoutSummary("Phone", 1))
        assertEquals("Rules for Phone: 7 rules", ExclusionsSettingsText.layoutSummary("Phone", 7))
    }
}
