package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.DefaultLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExclusionPropertyTest {
    private fun randomItem(
        random: Random,
        index: Int,
    ): Item =
        item(
            id = "n$index",
            source = listOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA).random(random),
            pkg = PACKAGES.random(random),
            profile = PROFILES.random(random).id.value,
            title = TEXTS.random(random).takeIf { random.nextBoolean() },
            body = TEXTS.random(random).takeIf { random.nextBoolean() },
            group = listOf("g1", "g2", null).random(random),
        ).let {
            if (random.nextInt(5) == 0) {
                it.copy(
                    privacy = ItemPrivacy.SENSITIVE,
                    title = null,
                    body = null,
                )
            } else {
                it
            }
        }

    private fun randomMatcher(random: Random): ExclusionMatcher {
        val app =
            ExclusionMatcher.App(
                PACKAGES.random(random),
                PROFILES.random(random).id.value.takeIf {
                    random.nextBoolean()
                },
            )
        return when (random.nextInt(5)) {
            0 -> app
            1 -> ExclusionMatcher.Group(listOf("g1", "g2").random(random))
            2 -> ExclusionMatcher.ItemKey("n${random.nextInt(30)}")
            3 ->
                ExclusionMatcher.Text(
                    ExclusionTextField.entries.random(random),
                    listOf("", "hel", "hello", "order #{?} shipped").random(random),
                    ExclusionMatchMode.entries.random(random),
                    app.takeIf { random.nextBoolean() },
                )
            else -> ExclusionMatcher.EmptyContent(app.takeIf { random.nextBoolean() })
        }
    }

    private fun randomRules(random: Random): ExclusionRuleSet =
        ExclusionRuleSet(
            List(random.nextInt(0, 8)) {
                rule(
                    "r$it",
                    randomMatcher(random),
                    listOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA).random(random),
                    random.nextInt(4) != 0,
                )
            },
        )

    @Test
    fun `the filter never reveals an excluded item and only removes`() {
        repeat(300) { seed ->
            val random = Random(seed)
            val items = List(30) { randomItem(random, it) }
            val rules = randomRules(random)
            val kept = SourceExclusionFilter.apply(rules, items)
            assertTrue(items.containsAll(kept), "seed $seed")
            assertEquals(items.filter { it in kept }, kept, "order preserved, seed $seed")
            items.filter { it !in kept }.forEach { dropped ->
                assertTrue(rules.rules.any { SourceExclusionFilter.matches(it, dropped) }, "seed $seed")
            }
            kept.forEach { k -> assertTrue(rules.rules.none { SourceExclusionFilter.matches(it, k) }, "seed $seed") }
        }
    }

    @Test
    fun `rule order is irrelevant`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val items = List(30) { randomItem(random, it) }
            val rules = randomRules(random)
            val shuffled = ExclusionRuleSet(rules.rules.shuffled(random))
            assertEquals(
                SourceExclusionFilter.apply(rules, items),
                SourceExclusionFilter.apply(shuffled, items),
                "seed $seed",
            )
        }
    }

    @Test
    fun `no lens output ever contains an excluded item`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val items = List(30) { randomItem(random, it) }
            val rules = randomRules(random)
            val lens = Lens(sources = listOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA), limit = random.nextInt(1, 40))
            val context = LensEvaluationContext(nowEpochMillis = 0L, exclusions = rules)
            val out =
                (
                    DefaultLensEvaluator.evaluate(
                        lens,
                        items,
                        context,
                    ) as LensResult.Flat
                ).items.map { it.id }.toSet()
            val excluded =
                items.filter {
                        i ->
                    rules.rules.any { SourceExclusionFilter.matches(it, i) }
                }.map { it.id }.toSet()
            assertTrue(out.intersect(excluded).isEmpty(), "seed $seed")
            val baseline = DefaultLensEvaluator.evaluate(lens, items, LensEvaluationContext(nowEpochMillis = 0L))
            assertEquals(
                baseline,
                DefaultLensEvaluator.evaluate(
                    lens,
                    items,
                    LensEvaluationContext(0L, exclusions = ExclusionRuleSet.EMPTY),
                ),
            )
        }
    }
}
