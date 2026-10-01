package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExclusionRuleSetTest {
    private val app = ExclusionMatcher.App("chat")

    @Test
    fun `add edit disable and delete`() {
        val a = rule("a", app)
        var set = ExclusionRuleSet.EMPTY.add(a)
        assertSame(set, set.add(a))
        set = set.edit(ExclusionRuleId("a")) { it.copy(id = ExclusionRuleId("zzz"), label = "  Mine  ") }
        assertEquals("Mine", set.find(ExclusionRuleId("a"))?.label)
        assertNull(set.find(ExclusionRuleId("zzz")))
        set = set.setEnabled(ExclusionRuleId("a"), false)
        assertFalse(set.rules.single().enabled)
        assertSame(set, set.setEnabled(ExclusionRuleId("missing"), true))
        assertTrue(set.remove(ExclusionRuleId("a")).isEmpty)
    }

    @Test
    fun `user rules are capped but migrated rules are not`() {
        val full = ExclusionRuleSet((0 until SourceExclusionRule.MAX_USER_RULES).map { rule("u$it", app) })
        assertSame(full, full.add(rule("extra", app)))
        val withMigrated = full.add(rule("m", app).copy(origin = ExclusionOrigin.MIGRATED_HIDE_RULE))
        assertEquals(SourceExclusionRule.MAX_USER_RULES + 1, withMigrated.rules.size)
    }

    @Test
    fun `merge de-duplicates by source and matcher and is idempotent`() {
        val mine = ExclusionRuleSet(listOf(rule("a", app)))
        val theirs = ExclusionRuleSet(listOf(rule("b", app), rule("c", ExclusionMatcher.App("mail"))))
        val merged = mine.merge(theirs)
        assertEquals(listOf("a", "c"), merged.rules.map { it.id.value })
        assertEquals(merged, merged.merge(theirs))
    }

    @Test
    fun `builders create rules from items and refuse what they cannot explain`() {
        var next = 0
        val builders = ExclusionRuleBuilders(WorkspaceIdFactory { "id${next++}" }, { 42L })
        val i = item("n1", title = "Order #4821 shipped", body = "ok", group = "chat:personal")
        assertEquals(ExclusionMatcher.App("chat", "personal"), builders.hideApp(i)?.matcher)
        assertEquals(ExclusionMatcher.Group("chat:personal"), builders.hideGroup(i)?.matcher)
        assertEquals(ExclusionMatcher.ItemKey("n1"), builders.hideItem(i).matcher)
        val like = assertNotNull(builders.hideLike(i, ExclusionTextField.TITLE))
        assertEquals(
            ExclusionMatcher.Text(
                ExclusionTextField.TITLE,
                "order #{?} shipped",
                ExclusionMatchMode.WILDCARD,
                ExclusionMatcher.App("chat", "personal"),
            ),
            like.matcher,
        )
        assertTrue(SourceExclusionFilter.matches(like, item("n2", title = "Order #1 shipped")))
        assertEquals(42L, like.createdAtEpochMillis)
        assertNull(builders.hideLike(i, ExclusionTextField.BODY)) // "ok" is shorter than three characters
        assertNull(
            builders.hideLike(
                item(
                    "s",
                    title = "secret",
                ).copy(privacy = com.riffle.core.domain.launcher.workspace.ItemPrivacy.SENSITIVE),
                ExclusionTextField.TITLE,
            ),
        )
        assertNull(builders.hideGroup(item("x")))
    }
}
