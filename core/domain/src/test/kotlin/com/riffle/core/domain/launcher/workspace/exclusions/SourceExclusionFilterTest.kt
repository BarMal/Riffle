package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SourceExclusionFilterTest {
    private fun hides(
        matcher: ExclusionMatcher,
        item: com.riffle.core.domain.launcher.workspace.Item,
    ) = SourceExclusionFilter.matches(rule("r", matcher), item)

    @Test
    fun `no rules or only disabled rules return the same list`() {
        val items = listOf(item("a", title = "x"))
        assertSame(items, SourceExclusionFilter.apply(ExclusionRuleSet.EMPTY, items))
        val disabled = ExclusionRuleSet(listOf(rule("r", ExclusionMatcher.App("chat"), enabled = false)))
        assertSame(items, SourceExclusionFilter.apply(disabled, items))
    }

    @Test
    fun `app matcher honours package profile and activity`() {
        val chat = item("a")
        assertTrue(hides(ExclusionMatcher.App("chat"), chat))
        assertTrue(hides(ExclusionMatcher.App("chat", "personal"), chat))
        assertFalse(hides(ExclusionMatcher.App("chat", "work"), chat))
        assertFalse(hides(ExclusionMatcher.App("mail"), chat))
        assertFalse(hides(ExclusionMatcher.App("chat", activityName = "Main"), chat))
    }

    @Test
    fun `activity is read from app and shortcut ids`() {
        val app = item("apps.all:personal:chat/Main", SourceIds.ALL_APPS)
        val shortcut = item("shortcuts:personal:chat:Main:s1", SourceIds.QUICK_ACTIONS)
        val activityRule = ExclusionMatcher.App("chat", "personal", "Main")
        val otherRule = ExclusionMatcher.App("chat", "personal", "Other")
        assertTrue(SourceExclusionFilter.matches(rule("r", activityRule, SourceIds.ALL_APPS), app))
        assertTrue(SourceExclusionFilter.matches(rule("r", activityRule, SourceIds.ALL_APPS), shortcut))
        assertFalse(SourceExclusionFilter.matches(rule("r", otherRule, SourceIds.ALL_APPS), app))
        assertFalse(SourceExclusionFilter.matches(rule("r", otherRule, SourceIds.ALL_APPS), shortcut))
    }

    @Test
    fun `group and item key are source specific`() {
        val feed = item("rss:f:1", SourceIds.RSS, pkg = null, group = "feed1")
        val other = item("rss:g:1", SourceIds.RSS, pkg = null, group = "feed2")
        assertTrue(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.Group("feed1"), SourceIds.RSS), feed))
        assertFalse(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.Group("feed1"), SourceIds.RSS), other))
        assertTrue(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.ItemKey("rss:f:1"), SourceIds.RSS), feed))
        assertFalse(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.Group("feed1"), SourceIds.CALENDAR), feed))
    }

    @Test
    fun `text modes normalise like the legacy rule`() {
        val i = item("a", title = "  Order   #4821 SHIPPED ")

        fun text(
            value: String,
            mode: ExclusionMatchMode,
        ) = ExclusionMatcher.Text(ExclusionTextField.TITLE, value, mode)
        assertTrue(hides(text("order #4821 shipped", ExclusionMatchMode.EXACT), i))
        assertTrue(hides(text("#48", ExclusionMatchMode.CONTAINS), i))
        assertTrue(hides(text("order #{?} shipped", ExclusionMatchMode.WILDCARD), i))
        assertFalse(hides(text("", ExclusionMatchMode.CONTAINS), i))
        assertFalse(hides(text("", ExclusionMatchMode.WILDCARD), i))
        assertFalse(hides(text("shipped", ExclusionMatchMode.EXACT), i))
    }

    @Test
    fun `empty content needs no title and no body`() {
        assertTrue(hides(ExclusionMatcher.EmptyContent(), item("a", title = " ")))
        assertFalse(hides(ExclusionMatcher.EmptyContent(), item("a", body = "x")))
        assertFalse(hides(ExclusionMatcher.EmptyContent(ExclusionMatcher.App("mail")), item("a")))
    }

    @Test
    fun `content matchers never match sensitive items but structure matchers do`() {
        val sensitive = item("a").copy(privacy = ItemPrivacy.SENSITIVE)
        assertFalse(hides(ExclusionMatcher.EmptyContent(), sensitive))
        assertFalse(hides(ExclusionMatcher.Text(ExclusionTextField.TITLE, "", ExclusionMatchMode.EXACT), sensitive))
        assertTrue(hides(ExclusionMatcher.App("chat"), sensitive))
    }

    @Test
    fun `app rules span the app and notification families`() {
        val media = item("m", SourceIds.MEDIA)
        assertTrue(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.App("chat")), media))
        val recent = item("apps.recent:personal:chat/Main", SourceIds.RECENT_APPS)
        assertTrue(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.App("chat"), SourceIds.ALL_APPS), recent))
        assertFalse(SourceExclusionFilter.matches(rule("r", ExclusionMatcher.App("chat"), SourceIds.ALL_APPS), media))
    }

    @Test
    fun `counts are per rule and ignore disabled rules`() {
        val rules =
            ExclusionRuleSet(
                listOf(
                    rule("a", ExclusionMatcher.App("chat")),
                    rule("b", ExclusionMatcher.App("chat"), enabled = false),
                ),
            )
        val counts = SourceExclusionFilter.matchCounts(rules, listOf(item("1"), item("2"), item("3", pkg = "mail")))
        assertEquals(2, counts[ExclusionRuleId("a")])
        assertEquals(0, counts[ExclusionRuleId("b")])
    }
}
