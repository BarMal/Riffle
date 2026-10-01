package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExclusionStorageTest {
    private fun obj(vararg entries: Pair<String, StoredValue>) = StoredValue.Obj(mapOf(*entries))

    private fun s(value: String) = StoredValue.Str(value)

    private val everyMatcher =
        listOf(
            ExclusionMatcher.App("chat", "work", "Main"),
            ExclusionMatcher.Group("g"),
            ExclusionMatcher.ItemKey("i"),
            ExclusionMatcher.Text(
                ExclusionTextField.BODY,
                "x",
                ExclusionMatchMode.WILDCARD,
                ExclusionMatcher.App("chat"),
            ),
            ExclusionMatcher.Text(ExclusionTextField.TITLE, "", ExclusionMatchMode.EXACT),
            ExclusionMatcher.EmptyContent(),
            ExclusionMatcher.EmptyContent(ExclusionMatcher.App("mail", "personal")),
        )

    @Test
    fun `round trips every matcher and the migration flag`() {
        val rules =
            everyMatcher.mapIndexed { index, m ->
                SourceExclusionRule(
                    ExclusionRuleId("r$index"),
                    SourceIds.RSS,
                    m,
                    index % 2 == 0,
                    "label",
                    ExclusionOrigin.MIGRATED_HIDE_RULE,
                    5L,
                )
            }
        val value =
            LayoutExclusionRules(
                mapOf(
                    HomeLayoutDeviceClass.TABLET to ExclusionRuleSet(rules),
                    HomeLayoutDeviceClass.PHONE to ExclusionRuleSet.EMPTY,
                ),
                legacyMigrated = true,
            )
        assertEquals(value, ExclusionRulesCodec.decode(ExclusionRulesCodec.encode(value)))
    }

    @Test
    fun `non objects decode to null and junk never throws`() {
        assertNull(ExclusionRulesCodec.decode(null))
        assertNull(ExclusionRulesCodec.decode(StoredValue.Arr(emptyList())))
        assertEquals(LayoutExclusionRules(), ExclusionRulesCodec.decode(obj("layouts" to s("nope"))))
    }

    @Test
    fun `bad entries are dropped and never widen a rule`() {
        val good =
            ExclusionRulesCodec.encode(
                LayoutExclusionRules(
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to
                            ExclusionRuleSet(listOf(rule("ok", ExclusionMatcher.Group("g")))),
                    ),
                ),
            )
        val layout = (good.fields.getValue("layouts") as StoredValue.Arr).items.single() as StoredValue.Obj
        val goodRule = (layout.fields.getValue("rules") as StoredValue.Arr).items.single() as StoredValue.Obj

        fun withMatcher(m: StoredValue) = StoredValue.Obj(goodRule.fields + ("matcher" to m))
        val badRules =
            listOf(
                withMatcher(obj("kind" to s("regex"), "value" to s("x"))),
                withMatcher(
                    obj(
                        "kind" to s("text"),
                        "field" to s("TITLE"),
                        "value" to s("x"),
                        "app" to obj("kind" to s("app")),
                    ),
                ),
                withMatcher(obj("kind" to s("empty"), "app" to s("junk"))),
                StoredValue.Obj(goodRule.fields - "id"),
                StoredValue.Obj(goodRule.fields - "source"),
                s("not an object"),
                goodRule,
                goodRule,
            )
        val root =
            obj(
                "layouts" to
                    StoredValue.Arr(
                        listOf(
                            obj("deviceClass" to s("WATCH"), "rules" to StoredValue.Arr(badRules)),
                            obj("deviceClass" to s("PHONE"), "rules" to StoredValue.Arr(badRules)),
                        ),
                    ),
            )
        val decoded = assertNotNull(ExclusionRulesCodec.decode(root))
        assertEquals(setOf(HomeLayoutDeviceClass.PHONE), decoded.layouts.keys)
        assertEquals(listOf("ok"), decoded.forLayout(HomeLayoutDeviceClass.PHONE).rules.map { it.id.value })
    }

    @Test
    fun `random garbage trees never throw`() {
        val random = Random(7)

        fun tree(depth: Int): StoredValue =
            when (random.nextInt(if (depth > 3) 3 else 5)) {
                0 -> s(listOf("app", "text", "PHONE", "", "TITLE").random(random))
                1 -> StoredValue.Num(random.nextLong())
                2 -> StoredValue.Bool(random.nextBoolean())
                3 -> StoredValue.Arr(List(random.nextInt(3)) { tree(depth + 1) })
                else ->
                    StoredValue.Obj(
                        listOf("layouts", "rules", "matcher", "kind", "id", "source", "app", "deviceClass", "field")
                            .filter { random.nextBoolean() }.associateWith { tree(depth + 1) },
                    )
            }
        repeat(500) { ExclusionRulesCodec.decode(tree(0)) }
    }

    @Test
    fun `migration copies hidden apps and hide rules into every layout deterministically`() {
        val hiddenApps =
            setOf(
                appIdentity("chat", "Main"),
                appIdentity("mail", "Alt", com.riffle.core.domain.launcher.apps.AppProfile.work()),
            )
        val hide = randomHideRules(Random(1), 4)
        val migrated = ExclusionMigration.migrate(LayoutExclusionRules(), hiddenApps, hide)
        assertTrue(migrated.legacyMigrated)
        assertEquals(HomeLayoutDeviceClass.entries.toSet(), migrated.layouts.keys)
        HomeLayoutDeviceClass.entries.forEach { layout ->
            assertEquals(hiddenApps.size + hide.size, migrated.forLayout(layout).rules.size)
            assertTrue(migrated.forLayout(layout).rules.all { it.id.value.startsWith("mig:${layout.name}:") })
        }
        assertEquals(migrated, ExclusionMigration.migrate(LayoutExclusionRules(), hiddenApps.reversed().toSet(), hide))
        assertEquals(
            "mig:PHONE:app:personal:chat/Main",
            migrated.forLayout(HomeLayoutDeviceClass.PHONE).rules.first().id.value,
        )
    }

    @Test
    fun `migration is idempotent and never overwrites or resurrects user changes`() {
        val hiddenApps = setOf(appIdentity("chat"))
        val once = ExclusionMigration.migrate(LayoutExclusionRules(), hiddenApps, emptyList())
        assertEquals(once, ExclusionMigration.migrate(once, hiddenApps, randomHideRules(Random(2), 3)))
        val phone = HomeLayoutDeviceClass.PHONE
        val edited =
            once.update(phone) { set ->
                set.remove(
                    ExclusionRuleId("mig:TABLET:app:personal:chat/Main"),
                ).setEnabled(ExclusionRuleId("mig:PHONE:app:personal:chat/Main"), false)
                    .add(rule("mine", ExclusionMatcher.Group("g")))
            }.update(HomeLayoutDeviceClass.TABLET) { it.remove(ExclusionRuleId("mig:TABLET:app:personal:chat/Main")) }
        assertEquals(edited, ExclusionMigration.migrate(edited, hiddenApps, emptyList()))
        assertTrue(edited.forLayout(HomeLayoutDeviceClass.TABLET).isEmpty)
    }

    @Test
    fun `pre-flag data keeps existing rules and only adds missing migrated ones`() {
        val hiddenApps = setOf(appIdentity("chat"))
        val existing =
            LayoutExclusionRules(
                mapOf(
                    HomeLayoutDeviceClass.PHONE to
                        ExclusionRuleSet(
                            listOf(
                                rule("mig:PHONE:app:personal:chat/Main", ExclusionMatcher.Group("x"), enabled = false),
                                rule("mine", ExclusionMatcher.Group("g")),
                            ),
                        ),
                ),
            )
        val migrated = ExclusionMigration.migrate(existing, hiddenApps, emptyList())
        val phone = migrated.forLayout(HomeLayoutDeviceClass.PHONE).rules
        assertEquals(listOf("mig:PHONE:app:personal:chat/Main", "mine"), phone.map { it.id.value })
        assertFalse(phone.first().enabled)
        assertEquals(1, migrated.forLayout(HomeLayoutDeviceClass.DESKTOP).rules.size)
    }

    @Test
    fun `layouts are isolated`() {
        val rules =
            LayoutExclusionRules().update(HomeLayoutDeviceClass.PHONE) {
                it.add(rule("a", ExclusionMatcher.App("chat")))
            }
        val i = item("n")
        assertEquals(emptyList(), SourceExclusionFilter.apply(rules.forLayout(HomeLayoutDeviceClass.PHONE), listOf(i)))
        HomeLayoutDeviceClass.entries.filter { it != HomeLayoutDeviceClass.PHONE }.forEach {
            assertEquals(listOf(i), SourceExclusionFilter.apply(rules.forLayout(it), listOf(i)))
        }
    }
}
