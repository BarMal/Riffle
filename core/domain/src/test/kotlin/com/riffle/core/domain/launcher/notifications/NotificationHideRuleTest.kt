package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationHideRuleTest {
    private val packageName = AppPackageName("com.example.chat")
    private val profileId = AppProfile.personal().id

    @Test
    fun appRuleMatchesAnyContentFromTheSameApp() {
        val rule = rule(NotificationHideRule.Kind.APP)

        assertTrue(rule.matches(notification(title = "Anything", text = "at all")))
        assertTrue(rule.matches(notification(title = "", text = "")))
    }

    @Test
    fun appRuleDoesNotMatchAnotherAppOrProfile() {
        val rule = rule(NotificationHideRule.Kind.APP)

        assertFalse(rule.matches(notification(packageName = AppPackageName("com.other.app"))))
        assertFalse(rule.matches(notification(profileId = AppProfile.work().id)))
    }

    @Test
    fun exactTitleMatchIsCaseAndWhitespaceInsensitive() {
        val rule = rule(NotificationHideRule.Kind.TITLE, value = "Order shipped")

        assertTrue(rule.matches(notification(title = "  ORDER   shipped ")))
        assertFalse(rule.matches(notification(title = "Order delayed")))
    }

    @Test
    fun containsMatchRequiresNonEmptyValue() {
        val rule =
            rule(NotificationHideRule.Kind.BODY, value = "promo", matchMode = NotificationHideRule.MatchMode.CONTAINS)

        assertTrue(rule.matches(notification(text = "Special promo just for you")))
        assertFalse(rule.matches(notification(text = "Your receipt")))

        val emptyValueRule =
            rule(NotificationHideRule.Kind.BODY, value = "", matchMode = NotificationHideRule.MatchMode.CONTAINS)
        assertFalse(emptyValueRule.matches(notification(text = "Anything")))
    }

    @Test
    fun wildcardMatchGeneralizesPlaceholders() {
        val pattern = NotificationHideRule.generalizeNumbers("Order #4821 shipped")
        assertEquals("order #{?} shipped", pattern)

        val rule =
            rule(
                NotificationHideRule.Kind.TITLE,
                value = pattern!!,
                matchMode = NotificationHideRule.MatchMode.WILDCARD,
            )

        assertTrue(rule.matches(notification(title = "Order #5190 shipped")))
        assertFalse(rule.matches(notification(title = "Order cancelled")))
    }

    @Test
    fun generalizeNumbersReturnsNullWithNoDigits() {
        assertNull(NotificationHideRule.generalizeNumbers("No numbers here"))
        assertNull(NotificationHideRule.generalizeNumbers("   "))
    }

    @Test
    fun emptyContentRuleMatchesOnlyBlankTitleAndBody() {
        val rule = rule(NotificationHideRule.Kind.EMPTY_CONTENT)

        assertTrue(rule.matches(notification(title = "  ", text = "")))
        assertFalse(rule.matches(notification(title = "Something", text = "")))
    }

    @Test
    fun filterDropsAnyNotificationMatchedByAtLeastOneRule() {
        val keep = notification(key = "keep", title = "Keep me")
        val drop = notification(key = "drop", title = "Order #10 shipped")
        val rules =
            listOf(
                rule(
                    NotificationHideRule.Kind.TITLE,
                    value = "order #{?} shipped",
                    matchMode = NotificationHideRule.MatchMode.WILDCARD,
                ),
            )

        assertEquals(listOf(keep), NotificationHideRuleFilter().visible(listOf(keep, drop), rules))
    }

    @Test
    fun filterIsANoOpFastPathWithNoRules() {
        val notifications = listOf(notification(key = "a"), notification(key = "b"))

        assertEquals(notifications, NotificationHideRuleFilter().visible(notifications, emptyList()))
    }

    private fun rule(
        kind: NotificationHideRule.Kind,
        value: String = "",
        matchMode: NotificationHideRule.MatchMode = NotificationHideRule.MatchMode.EXACT,
    ) = NotificationHideRule(
        id = NotificationHideRuleId("rule-1"),
        packageName = packageName,
        profileId = profileId,
        kind = kind,
        value = value,
        matchMode = matchMode,
    )

    private fun notification(
        key: String = "key",
        packageName: AppPackageName = this.packageName,
        profileId: com.riffle.core.domain.launcher.apps.AppProfileId = this.profileId,
        title: String = "",
        text: String = "",
    ) = LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = packageName,
        profileId = profileId,
        title = title,
        text = text,
        postedAtEpochMillis = 0L,
    )
}
