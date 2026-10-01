package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatchMode
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionOrigin
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleBuilders
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import com.riffle.core.domain.launcher.workspace.exclusions.item
import com.riffle.core.domain.launcher.workspace.exclusions.rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExclusionsSettingsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val app = ExclusionMatcher.App("chat", "work")
    private val text = ExclusionMatcher.Text(ExclusionTextField.TITLE, "sale", ExclusionMatchMode.CONTAINS)

    private var counter = 0
    private val ids = WorkspaceIdFactory { "id${counter++}" }

    private fun rules(vararg onPhone: SourceExclusionRule) =
        LayoutExclusionRules(mapOf(phone to ExclusionRuleSet(onPhone.toList())), legacyMigrated = true)

    @Test
    fun `rows are grouped by kind in display order and keep creation order inside a kind`() {
        val set =
            rules(
                rule("t", text),
                rule("a1", app, SourceIds.ALL_APPS),
                rule("g", ExclusionMatcher.Group("feed-1"), SourceIds.RSS),
                rule("a2", ExclusionMatcher.App("mail"), SourceIds.ALL_APPS),
                rule("i", ExclusionMatcher.ItemKey("rss:x"), SourceIds.RSS),
                rule("e", ExclusionMatcher.EmptyContent()),
            )

        val model = ExclusionsSettingsPlanner.plan(set, phone, listOf(phone, tablet))

        assertEquals(ExclusionRuleKind.entries, model.sections.map { it.kind })
        assertEquals(listOf("a1", "a2"), model.sections.first().rows.map { it.id.value })
        assertEquals(6, model.ruleCount)
        assertFalse(model.isEmpty)
        assertEquals("a2", model.row(ExclusionRuleId("a2"))?.id?.value)
    }

    @Test
    fun `an empty layout has no sections and still allows adding`() {
        val model = ExclusionsSettingsPlanner.plan(LayoutExclusionRules(), phone, listOf(phone))

        assertTrue(model.isEmpty)
        assertTrue(model.sections.isEmpty())
        assertTrue(model.canAdd)
        assertFalse(model.canApplyToOtherLayouts)
    }

    @Test
    fun `descriptions say what a rule matches without item content`() {
        fun describe(
            matcher: ExclusionMatcher,
            source: com.riffle.core.domain.launcher.workspace.SourceId = SourceIds.NOTIFICATIONS,
        ) = ExclusionRuleDescriber.describe(source, matcher)

        assertEquals("App chat (work profile)", describe(app, SourceIds.ALL_APPS))
        assertEquals("Everything from chat (work profile)", describe(app))
        assertEquals(
            "App chat, one launcher entry",
            describe(ExclusionMatcher.App("chat", null, "Main"), SourceIds.ALL_APPS),
        )
        assertEquals("Feed feed-1", describe(ExclusionMatcher.Group("feed-1"), SourceIds.RSS))
        assertEquals("Category tools", describe(ExclusionMatcher.Group("tools"), SourceIds.ALL_APPS))
        assertEquals("One specific item", describe(ExclusionMatcher.ItemKey("secret-article-id"), SourceIds.RSS))
        assertEquals("Title contains \"sale\"", describe(text))
        assertEquals(
            "Body matches pattern \"order #{?} shipped\" from chat",
            describe(
                ExclusionMatcher.Text(
                    ExclusionTextField.BODY,
                    "order #{?} shipped",
                    ExclusionMatchMode.WILDCARD,
                    ExclusionMatcher.App("chat"),
                ),
            ),
        )
        assertEquals("Items with no title and no text", describe(ExclusionMatcher.EmptyContent()))
    }

    @Test
    fun `an item rule never shows its key and long text is cut`() {
        assertFalse(
            ExclusionRuleDescriber.describe(SourceIds.RSS, ExclusionMatcher.ItemKey("https://x/y")).contains("x/y"),
        )
        val long = ExclusionMatcher.Text(ExclusionTextField.TITLE, "a".repeat(200), ExclusionMatchMode.CONTAINS)
        val described = ExclusionRuleDescriber.describe(SourceIds.NOTIFICATIONS, long)
        assertTrue(described.length < 100)
        assertTrue(described.contains("…"))
    }

    @Test
    fun `counts are attached and withheld when the source is off`() {
        val set = rules(rule("a", app, SourceIds.NOTIFICATIONS), rule("b", text, SourceIds.RSS))
        val counts = mapOf(ExclusionRuleId("a") to 3, ExclusionRuleId("b") to 1)

        val plain = ExclusionsSettingsPlanner.plan(set, phone, listOf(phone), counts).rows
        assertEquals(listOf(3, 1), plain.map { it.matchCount })

        val off = ExclusionsSettingsPlanner.plan(set, phone, listOf(phone), counts, setOf(SourceIds.RSS)).rows
        assertEquals(listOf<Int?>(3, null), off.map { it.matchCount })
        assertEquals(listOf(false, true), off.map { it.sourceOff })
    }

    @Test
    fun `only on this layout is shown when no other listed layout has an equal rule`() {
        val shared = rule("s", app, SourceIds.ALL_APPS)
        val onlyHere = rule("o", text)
        val all =
            LayoutExclusionRules(
                mapOf(
                    phone to ExclusionRuleSet(listOf(shared, onlyHere)),
                    tablet to ExclusionRuleSet(listOf(rule("s2", app, SourceIds.ALL_APPS))),
                ),
            )

        val rows = ExclusionsSettingsPlanner.plan(all, phone, listOf(phone, tablet)).rows
        assertEquals(listOf(false, true), rows.map { it.onlyOnThisLayout })
        assertTrue(ExclusionsSettingsPlanner.plan(all, phone, listOf(phone, tablet)).canApplyToOtherLayouts)

        val alone = ExclusionsSettingsPlanner.plan(all, phone, listOf(phone)).rows
        assertEquals(listOf(false, false), alone.map { it.onlyOnThisLayout })
    }

    @Test
    fun `migrated rules are marked and the user cap turns off adding`() {
        val migrated = rule("m", app, SourceIds.ALL_APPS).copy(origin = ExclusionOrigin.MIGRATED_HIDDEN_APP)
        val many = (0 until SourceExclusionRule.MAX_USER_RULES).map { rule("u$it", text) }
        val model = ExclusionsSettingsPlanner.plan(rules(*(many + migrated).toTypedArray()), phone, listOf(phone))

        assertTrue(model.row(ExclusionRuleId("m"))!!.migrated)
        assertFalse(model.row(ExclusionRuleId("u0"))!!.migrated)
        assertFalse(model.canAdd)
    }

    @Test
    fun `enable and disable change only that rule on only that layout`() {
        val stored =
            LayoutExclusionRules(
                mapOf(
                    phone to ExclusionRuleSet(listOf(rule("a", text))),
                    tablet to ExclusionRuleSet(listOf(rule("a", text))),
                ),
            )

        val off = ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("a"), false).applyTo(stored, phone, ids)

        assertTrue(off.applied)
        assertFalse(off.undoable)
        assertFalse(off.after.forLayout(phone).rules.single().enabled)
        assertTrue(off.after.forLayout(tablet).rules.single().enabled)
        assertTrue(off.message is ExclusionsMessage.Disabled)

        val same = ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("a"), false).applyTo(off.after, phone, ids)
        assertFalse(same.applied)
        assertEquals(ExclusionsMessage.NothingToDo, same.message)
        assertEquals(off.after, same.after)

        val missing = ExclusionsSettingsAction.SetEnabled(ExclusionRuleId("zz"), true).applyTo(stored, phone, ids)
        assertEquals(ExclusionsMessage.UnknownRule, missing.message)
    }

    @Test
    fun `delete removes the rule and undo restores the exact previous value`() {
        val stored = rules(rule("a", text), rule("b", app, SourceIds.ALL_APPS))

        val change = ExclusionsSettingsAction.Delete(ExclusionRuleId("a")).applyTo(stored, phone, ids)

        assertTrue(change.applied && change.undoable)
        assertEquals(listOf("b"), change.after.forLayout(phone).rules.map { it.id.value })
        assertEquals(stored, change.undo())
        assertEquals(ExclusionsMessage.Deleted("Title contains \"sale\""), change.message)
    }

    @Test
    fun `adding a text rule normalises the value and records the author's choice`() {
        val draft =
            TextRuleDraft(
                SourceIds.NOTIFICATIONS,
                ExclusionTextField.BODY,
                ExclusionMatchMode.CONTAINS,
                "  Flash   SALE ",
            )

        val change = ExclusionsSettingsAction.AddText(draft).applyTo(LayoutExclusionRules(), phone, ids) { 42L }

        assertTrue(change.applied && change.undoable)
        val added = change.after.forLayout(phone).rules.single()
        assertEquals(ExclusionRuleId("id0"), added.id)
        assertEquals(SourceIds.NOTIFICATIONS, added.source)
        assertEquals(
            ExclusionMatcher.Text(ExclusionTextField.BODY, "flash sale", ExclusionMatchMode.CONTAINS),
            added.matcher,
        )
        assertEquals(42L, added.createdAtEpochMillis)
        assertEquals(ExclusionOrigin.USER, added.origin)
        assertTrue(change.after.forLayout(tablet).isEmpty)
        assertEquals(LayoutExclusionRules(), change.undo())
    }

    @Test
    fun `validation refuses short long unsupported duplicate and over the cap`() {
        fun draft(
            value: String,
            mode: ExclusionMatchMode = ExclusionMatchMode.CONTAINS,
            source: com.riffle.core.domain.launcher.workspace.SourceId = SourceIds.NOTIFICATIONS,
        ) = TextRuleDraft(source, ExclusionTextField.TITLE, mode, value)

        val existing = ExclusionRuleSet(listOf(rule("t", text)))

        assertEquals(TextRuleProblem.TOO_SHORT, TextRuleValidator.validate(draft("ab"), existing))
        assertEquals(TextRuleProblem.TOO_SHORT, TextRuleValidator.validate(draft("   "), existing))
        assertEquals(
            TextRuleProblem.TOO_SHORT,
            TextRuleValidator.validate(draft("{?}{?}", ExclusionMatchMode.WILDCARD), existing),
        )
        assertEquals(
            TextRuleProblem.TOO_SHORT,
            TextRuleValidator.validate(draft("a{?}b", ExclusionMatchMode.WILDCARD), existing),
        )
        assertEquals(TextRuleProblem.TOO_LONG, TextRuleValidator.validate(draft("x".repeat(121)), existing))
        assertEquals(
            TextRuleProblem.UNSUPPORTED_SOURCE,
            TextRuleValidator.validate(draft("sale", source = SourceIds.SEARCH), existing),
        )
        assertEquals(TextRuleProblem.ALREADY_EXISTS, TextRuleValidator.validate(draft("SALE"), existing))
        assertNull(TextRuleValidator.validate(draft("sale", ExclusionMatchMode.EXACT), existing))
        assertNull(TextRuleValidator.validate(draft("order {?} shipped", ExclusionMatchMode.WILDCARD), existing))

        val full =
            ExclusionRuleSet((0 until SourceExclusionRule.MAX_USER_RULES).map { rule("u$it", app, SourceIds.ALL_APPS) })
        assertEquals(TextRuleProblem.LIMIT_REACHED, TextRuleValidator.validate(draft("fresh text"), full))
    }

    @Test
    fun `a refused add changes nothing and says why`() {
        val change =
            ExclusionsSettingsAction
                .AddText(TextRuleDraft(SourceIds.RSS, ExclusionTextField.TITLE, ExclusionMatchMode.EXACT, "x"))
                .applyTo(LayoutExclusionRules(), phone, ids)

        assertFalse(change.applied)
        assertEquals(ExclusionsMessage.Problem(TextRuleProblem.TOO_SHORT), change.message)
        assertEquals(change.before, change.after)
    }

    @Test
    fun `apply to all layouts copies with fresh ids de-duplicates and is idempotent`() {
        val stored = rules(rule("a", text))

        val first = ExclusionsSettingsAction.ApplyToAllLayouts(ExclusionRuleId("a")).applyTo(stored, phone, ids)

        assertTrue(first.applied && first.undoable)
        HomeLayoutDeviceClass.entries.forEach { layout ->
            assertEquals(1, first.after.forLayout(layout).rules.size)
            assertEquals(text, first.after.forLayout(layout).rules.single().matcher)
        }
        assertEquals(HomeLayoutDeviceClass.entries.size - 1, (first.message as ExclusionsMessage.AppliedToAll).added)
        val copiedIds =
            HomeLayoutDeviceClass.entries.filter { it != phone }.map {
                first.after.forLayout(
                    it,
                ).rules.single().id
            }
        assertEquals(copiedIds.size, copiedIds.toSet().size)
        assertFalse(copiedIds.contains(ExclusionRuleId("a")))

        val again = ExclusionsSettingsAction.ApplyToAllLayouts(ExclusionRuleId("a")).applyTo(first.after, phone, ids)
        assertFalse(again.applied)
        assertEquals(first.after, again.after)
        assertEquals(stored, first.undo())
    }

    @Test
    fun `contextual choices follow what the item can support`() {
        val notification =
            item("n:1", SourceIds.NOTIFICATIONS, "chat", title = "Order #4821 shipped", group = "chat:personal")
        val choices = ExclusionHideActions.choicesFor(notification).associateBy { it.kind }

        assertEquals(
            setOf(HideKind.APP, HideKind.GROUP, HideKind.ITEM, HideKind.LIKE_THIS, HideKind.EMPTY_CONTENT),
            choices.keys,
        )
        assertEquals("Hide notifications from this app", choices.getValue(HideKind.APP).label)
        assertEquals("Hide notifications like this", choices.getValue(HideKind.LIKE_THIS).label)

        val article = item("rss:f:1", SourceIds.RSS, pkg = null, title = "Hello world", group = "f")
        val articleChoices = ExclusionHideActions.choicesFor(article).associateBy { it.kind }
        assertFalse(HideKind.APP in articleChoices)
        assertEquals("Hide this feed", articleChoices.getValue(HideKind.GROUP).label)
        assertEquals("Hide this article", articleChoices.getValue(HideKind.ITEM).label)

        val bare = item("x", SourceIds.CALENDAR, pkg = null)
        assertEquals(
            listOf(HideKind.ITEM, HideKind.EMPTY_CONTENT),
            ExclusionHideActions.choicesFor(bare).map { it.kind },
        )
    }

    @Test
    fun `labels and announcements never contain item text`() {
        val secret = item("n:1", SourceIds.NOTIFICATIONS, "chat", title = "Private diagnosis", body = "Secret body")
        ExclusionHideActions.choicesFor(secret).forEach {
            assertFalse(
                it.label.contains("diagnosis", ignoreCase = true) || it.what.contains("Secret", ignoreCase = true),
            )
        }
        val change = ExclusionHideActions.apply(LayoutExclusionRules(), phone, secret, HideKind.LIKE_THIS)
        assertEquals(ExclusionsMessage.Hidden(phone, "notifications like this"), change.message)
    }

    @Test
    fun `sensitive items only offer structural choices`() {
        val sensitive =
            item("n:2", SourceIds.NOTIFICATIONS, "chat", group = "chat:personal")
                .copy(privacy = com.riffle.core.domain.launcher.workspace.ItemPrivacy.SENSITIVE, title = null)

        assertEquals(
            listOf(HideKind.APP, HideKind.GROUP, HideKind.ITEM),
            ExclusionHideActions.choicesFor(sensitive).map { it.kind },
        )
    }

    @Test
    fun `hide adds one user rule for this layout with undo and announces where`() {
        val article = item("rss:f:1", SourceIds.RSS, pkg = null, title = "Hello world", group = "f")
        val builders = ExclusionRuleBuilders(ids, { 7L })

        val change =
            ExclusionHideActions.apply(
                LayoutExclusionRules(),
                tablet,
                article,
                HideKind.GROUP,
                builders = builders,
            )

        assertTrue(change.applied && change.undoable)
        assertEquals(ExclusionsMessage.Hidden(tablet, "this feed"), change.message)
        assertEquals(ExclusionMatcher.Group("f"), change.after.forLayout(tablet).rules.single().matcher)
        assertTrue(change.after.forLayout(phone).isEmpty)
        assertEquals(LayoutExclusionRules(), change.undo())
    }

    @Test
    fun `hide on all layouts adds an equal rule everywhere and hiding twice is a no-op`() {
        val article = item("rss:f:1", SourceIds.RSS, pkg = null, group = "f")
        val builders = ExclusionRuleBuilders(ids, { 7L })

        val everywhere =
            ExclusionHideActions.apply(
                LayoutExclusionRules(),
                phone,
                article,
                HideKind.ITEM,
                allLayouts = true,
                builders = builders,
            )

        HomeLayoutDeviceClass.entries.forEach { layout ->
            assertEquals(ExclusionMatcher.ItemKey("rss:f:1"), everywhere.after.forLayout(layout).rules.single().matcher)
        }
        val again =
            ExclusionHideActions.apply(
                everywhere.after,
                phone,
                article,
                HideKind.ITEM,
                allLayouts = true,
                builders = builders,
            )
        assertFalse(again.applied)
        assertEquals(ExclusionsMessage.AlreadyHidden("this article"), again.message)
    }

    @Test
    fun `hiding something whose rule is switched off turns the rule back on`() {
        val article = item("rss:f:1", SourceIds.RSS, pkg = null, group = "f")
        val builders = ExclusionRuleBuilders(ids, { 7L })
        val hidden =
            ExclusionHideActions.apply(
                LayoutExclusionRules(),
                phone,
                article,
                HideKind.GROUP,
                builders = builders,
            )
        val id = hidden.after.forLayout(phone).rules.single().id
        val off = ExclusionsSettingsAction.SetEnabled(id, false).applyTo(hidden.after, phone, ids).after

        val back = ExclusionHideActions.apply(off, phone, article, HideKind.GROUP, builders = builders)

        assertTrue(back.applied)
        assertEquals(1, back.after.forLayout(phone).rules.size)
        assertTrue(back.after.forLayout(phone).rules.single().enabled)
        assertNotEquals(off, back.after)
    }

    @Test
    fun `a hide the item cannot support is refused`() {
        val article = item("rss:f:1", SourceIds.RSS, pkg = null, group = "f")

        val change = ExclusionHideActions.apply(LayoutExclusionRules(), phone, article, HideKind.APP)

        assertFalse(change.applied)
        assertEquals(ExclusionsMessage.CannotHide, change.message)
    }
}
