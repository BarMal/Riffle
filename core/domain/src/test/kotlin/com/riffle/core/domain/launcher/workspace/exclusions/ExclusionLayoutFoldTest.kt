package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.backup.WorkspaceBackup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Owner decision N10: rotating a phone never changes what is hidden. */
class ExclusionLayoutFoldTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val landscape = HomeLayoutDeviceClass.PHONE_LANDSCAPE
    private val tablet = HomeLayoutDeviceClass.TABLET

    private fun set(vararg rules: SourceExclusionRule) = ExclusionRuleSet(rules.toList())

    @Test
    fun `a rotated phone reads and writes the phone rules`() {
        val rules = LayoutExclusionRules().update(landscape) { it.add(rule("a", ExclusionMatcher.App("chat"))) }
        assertEquals(setOf(phone), rules.layouts.keys)
        assertEquals(rules.forLayout(phone), rules.forLayout(landscape))
    }

    @Test
    fun `only landscape rules become the phone rules`() {
        val stored = LayoutExclusionRules(mapOf(landscape to set(rule("l", ExclusionMatcher.App("chat")))), true)
        val folded = stored.folded()
        assertEquals(mapOf(phone to set(rule("l", ExclusionMatcher.App("chat")))), folded.layouts)
        assertTrue(folded.legacyMigrated)
        assertEquals(folded, folded.folded())
    }

    @Test
    fun `both are merged so nothing hidden becomes visible and the phone wins on an id conflict`() {
        val stored =
            LayoutExclusionRules(
                mapOf(
                    phone to set(rule("same", ExclusionMatcher.App("phone")), rule("p", ExclusionMatcher.Group("g"))),
                    landscape to
                        set(
                            rule("same", ExclusionMatcher.App("landscape")),
                            rule("l", ExclusionMatcher.App("mail")),
                            // An equal rule the phone already applies adds nothing.
                            rule("dup", ExclusionMatcher.Group("g")),
                        ),
                    tablet to set(rule("t", ExclusionMatcher.App("maps"))),
                ),
            )
        val folded = stored.folded()
        assertEquals(setOf(phone, tablet), folded.layouts.keys)
        assertEquals(listOf("same", "p", "l"), folded.forLayout(phone).rules.map { it.id.value })
        assertEquals(ExclusionMatcher.App("phone"), folded.forLayout(phone).find(ExclusionRuleId("same"))?.matcher)
        assertEquals(stored.forLayout(tablet), folded.forLayout(tablet))
        // Everything either layout hid is still hidden on the phone.
        listOf("phone", "mail").forEach { pkg ->
            val hidden = item("n", pkg = pkg)
            assertEquals(emptyList(), SourceExclusionFilter.apply(folded.forLayout(phone), listOf(hidden)))
        }
        val grouped = item("g", group = "g")
        assertEquals(emptyList(), SourceExclusionFilter.apply(folded.forLayout(landscape), listOf(grouped)))
    }

    @Test
    fun `an enabled landscape rule is kept when the phone has the same rule turned off`() {
        val stored =
            LayoutExclusionRules(
                mapOf(
                    phone to set(rule("p", ExclusionMatcher.App("chat"), enabled = false)),
                    landscape to set(rule("l", ExclusionMatcher.App("chat"))),
                ),
            )
        assertEquals(listOf("p", "l"), stored.folded().forLayout(phone).rules.map { it.id.value })
    }

    @Test
    fun `merged rules bypass the user rule cap`() {
        val many = (0 until SourceExclusionRule.MAX_USER_RULES).map { rule("p$it", ExclusionMatcher.Group("p$it")) }
        val stored =
            LayoutExclusionRules(
                mapOf(phone to ExclusionRuleSet(many), landscape to set(rule("l", ExclusionMatcher.App("chat")))),
            )
        assertEquals(many.size + 1, stored.folded().forLayout(phone).rules.size)
    }

    @Test
    fun `neither is unchanged`() {
        val stored = LayoutExclusionRules(mapOf(tablet to set(rule("t", ExclusionMatcher.App("maps")))))
        assertSame(stored, stored.folded())
        assertSame(LayoutExclusionRules().layouts, LayoutExclusionRules().folded().layouts)
    }

    @Test
    fun `decoding and restoring an older file fold its landscape rules`() {
        val stored =
            LayoutExclusionRules(
                mapOf(
                    landscape to set(rule("l", ExclusionMatcher.App("mail"))),
                    phone to set(rule("p", ExclusionMatcher.App("chat"))),
                ),
                legacyMigrated = true,
            )
        val decoded = ExclusionRulesCodec.decode(ExclusionRulesCodec.encode(stored))
        assertEquals(stored.folded(), decoded)
        assertEquals(setOf(phone), decoded?.layouts?.keys)
        assertEquals(stored.folded(), WorkspaceBackup.restoreExclusions(stored))
    }

    @Test
    fun `hostile rule sets never throw and never keep a landscape set`() {
        val random = Random(4)
        val encodedRule =
            ExclusionRulesCodec.encode(LayoutExclusionRules(mapOf(phone to set(rule("r", ExclusionMatcher.App("a"))))))
                .let { ((it.fields.getValue("layouts") as StoredValue.Arr).items.first() as StoredValue.Obj) }
                .let { (it.fields.getValue("rules") as StoredValue.Arr).items.first() }
        val junk = listOf(encodedRule, StoredValue.Num(-1), StoredValue.Obj(emptyMap()), StoredValue.Str("r"))
        repeat(300) {
            val layouts =
                List(random.nextInt(0, 6)) {
                    StoredValue.Obj(
                        mapOf(
                            "deviceClass" to StoredValue.Str(listOf("PHONE", "PHONE_LANDSCAPE", "?").random(random)),
                            "rules" to StoredValue.Arr(List(random.nextInt(0, 4)) { junk.random(random) }),
                        ),
                    )
                }
            val decoded = ExclusionRulesCodec.decode(StoredValue.Obj(mapOf("layouts" to StoredValue.Arr(layouts))))
            assertNotNull(decoded)
            assertFalse(landscape in decoded.layouts)
            decoded.layouts.values.forEach { assertEquals(it.rules.size, it.rules.distinctBy { r -> r.id }.size) }
        }
    }

    @Test
    fun `migration copies the legacy rules into the phone layout once and no landscape set`() {
        val migrated = ExclusionMigration.migrate(LayoutExclusionRules(), setOf(appIdentity("chat")), emptyList())
        assertFalse(landscape in migrated.layouts)
        assertEquals(1, migrated.forLayout(landscape).rules.size)
    }
}
