package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A source's own page shows only that source's rules, keeping kind order and recounting. */
class ExclusionsForSourcesTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val text = ExclusionMatcher.Text(ExclusionTextField.TITLE, "sale", ExclusionMatchMode.CONTAINS)

    private val model =
        ExclusionsSettingsPlanner.plan(
            LayoutExclusionRules(
                mapOf(
                    phone to
                        ExclusionRuleSet(
                            listOf(
                                rule("n1", text, SourceIds.NOTIFICATIONS),
                                rule("a1", ExclusionMatcher.App("chat"), SourceIds.ALL_APPS),
                                rule("n2", ExclusionMatcher.App("mail"), SourceIds.NOTIFICATIONS),
                                rule("f1", ExclusionMatcher.Group("feed"), SourceIds.RSS),
                            ),
                        ),
                ),
                legacyMigrated = true,
            ),
            phone,
            listOf(phone),
        )

    @Test
    fun `only the named sources rules remain and the count follows`() {
        val notifications = model.forSources(setOf(SourceIds.NOTIFICATIONS))

        assertEquals(2, notifications.ruleCount)
        assertEquals(listOf("n2", "n1"), notifications.rows.map { it.id.value })
        assertEquals(listOf(ExclusionRuleKind.APPS, ExclusionRuleKind.TEXT), notifications.sections.map { it.kind })
    }

    @Test
    fun `several sources combine and an unrelated source leaves nothing`() {
        assertEquals(1, model.forSources(setOf(SourceIds.ALL_APPS, SourceIds.RECENT_APPS)).ruleCount)
        val none = model.forSources(setOf(SourceIds.MEDIA))
        assertTrue(none.isEmpty)
        assertTrue(none.sections.isEmpty())
        assertEquals(model.canAdd, none.canAdd)
    }
}
