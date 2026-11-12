package com.riffle.core.domain.launcher.apps

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMatcher
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionTextField
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HiddenAppsMergeTest {
    private fun app(
        pkg: String,
        activity: String = ".Main",
        profile: AppProfile = AppProfile.personal(),
    ) = InstalledApp(AppIdentity(AppPackageName(pkg), AppActivityName(activity), profile), label = pkg)

    private fun rule(
        id: String,
        matcher: ExclusionMatcher,
        source: com.riffle.core.domain.launcher.workspace.SourceId = SourceIds.ALL_APPS,
        enabled: Boolean = true,
    ) = SourceExclusionRule(ExclusionRuleId(id), source, matcher, enabled = enabled)

    private fun rules(vararg perLayout: Pair<HomeLayoutDeviceClass, List<SourceExclusionRule>>) =
        LayoutExclusionRules(perLayout.associate { (layout, list) -> layout to ExclusionRuleSet(list) })

    private val chat = app("com.chat")
    private val exactChat = ExclusionMatcher.App("com.chat", "personal", ".Main")
    private val mail = app("com.mail")
    private val workMail = app("com.mail", profile = AppProfile.work())

    @Test
    fun aRuleHidingAnAppMovesItIntoTheOneListAndTheRuleIsDropped() {
        val stored = setOf(mail.identity)
        val input =
            rules(
                HomeLayoutDeviceClass.PHONE to listOf(rule("r1", exactChat)),
            )

        val result = HiddenAppsMerge.merge(stored, input, listOf(chat, mail))

        assertEquals(setOf(mail.identity, chat.identity), result.hidden)
        assertTrue(result.rules.forLayout(HomeLayoutDeviceClass.PHONE).isEmpty)
        assertTrue(result.changed(stored, input))
    }

    @Test
    fun nothingHiddenBecomesVisible() {
        val stored = setOf(mail.identity)
        val input = rules(HomeLayoutDeviceClass.PHONE to listOf(rule("r1", ExclusionMatcher.App("com.chat"))))

        val result = HiddenAppsMerge.merge(stored, input, listOf(chat, mail))

        assertTrue(result.hidden.containsAll(stored))
        assertTrue(chat.identity in result.hidden)
    }

    @Test
    fun nothingVisibleBecomesHidden() {
        val input =
            rules(
                HomeLayoutDeviceClass.PHONE to
                    listOf(
                        rule("disabled", ExclusionMatcher.App("com.chat"), enabled = false),
                        rule("recents", ExclusionMatcher.App("com.mail"), source = SourceIds.RECENT_APPS),
                        rule("text", ExclusionMatcher.Text(ExclusionTextField.TITLE, "x")),
                        rule("group", ExclusionMatcher.Group("Social")),
                    ),
            )

        val result = HiddenAppsMerge.merge(emptySet(), input, listOf(chat, mail))

        assertTrue(result.hidden.isEmpty())
        assertEquals(input, result.rules)
        assertFalse(result.changed(emptySet(), input))
    }

    @Test
    fun aPackageRuleHidesEveryProfileCopyItMatchesButNotOthers() {
        val input =
            rules(
                HomeLayoutDeviceClass.PHONE to listOf(rule("r", ExclusionMatcher.App("com.mail", profileId = "work"))),
            )

        val result = HiddenAppsMerge.merge(emptySet(), input, listOf(chat, mail, workMail))

        assertEquals(setOf(workMail.identity), result.hidden)
    }

    @Test
    fun aRuleThatMatchesNoInstalledAppIsKeptSoLaterInstallsStayHidden() {
        val ghost = rule("ghost", ExclusionMatcher.App("com.gone"))
        val input = rules(HomeLayoutDeviceClass.PHONE to listOf(ghost))

        val result = HiddenAppsMerge.merge(emptySet(), input, listOf(chat))

        assertTrue(result.hidden.isEmpty())
        assertEquals(listOf(ghost), result.rules.forLayout(HomeLayoutDeviceClass.PHONE).rules)
    }

    @Test
    fun rulesInEveryLayoutAreFoldedIn() {
        val input =
            rules(
                HomeLayoutDeviceClass.PHONE to listOf(rule("a", ExclusionMatcher.App("com.chat"))),
                HomeLayoutDeviceClass.TABLET to listOf(rule("b", ExclusionMatcher.App("com.mail"))),
            )

        val result = HiddenAppsMerge.merge(emptySet(), input, listOf(chat, mail))

        assertEquals(setOf(chat.identity, mail.identity), result.hidden)
    }

    @Test
    fun runningItAgainOverItsOwnResultChangesNothing() {
        val input = rules(HomeLayoutDeviceClass.PHONE to listOf(rule("r1", ExclusionMatcher.App("com.chat"))))
        val first = HiddenAppsMerge.merge(emptySet(), input, listOf(chat, mail))

        val second = HiddenAppsMerge.merge(first.hidden, first.rules, listOf(chat, mail))

        assertEquals(first, second)
        assertFalse(second.changed(first.hidden, first.rules))
    }
}
