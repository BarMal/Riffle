package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationHidingSettingsTest {
    private val packageName = AppPackageName("com.example.chat")
    private val profileId = AppProfile.personal().id

    @Test
    fun withRuleAddsANewRule() {
        val settings = NotificationHidingSettings().withRule(packageName, profileId, NotificationHideRule.Kind.APP)

        assertEquals(1, settings.rules.size)
        assertEquals(NotificationHideRule.Kind.APP, settings.rules.single().kind)
    }

    @Test
    fun withRuleIgnoresAnExactDuplicate() {
        val once =
            NotificationHidingSettings()
                .withRule(packageName, profileId, NotificationHideRule.Kind.TITLE, value = "Hello")
        val twice = once.withRule(packageName, profileId, NotificationHideRule.Kind.TITLE, value = "Hello")

        assertEquals(1, twice.rules.size)
    }

    @Test
    fun withRuleAllowsDifferentRulesForTheSameApp() {
        val settings =
            NotificationHidingSettings()
                .withRule(packageName, profileId, NotificationHideRule.Kind.TITLE, value = "Hello")
                .withRule(packageName, profileId, NotificationHideRule.Kind.TITLE, value = "Goodbye")

        assertEquals(2, settings.rules.size)
    }

    @Test
    fun withRuleNormalizesValueForAppAndEmptyContentKinds() {
        val settings =
            NotificationHidingSettings()
                .withRule(packageName, profileId, NotificationHideRule.Kind.APP, value = "ignored")

        assertEquals("", settings.rules.single().value)
    }

    @Test
    fun withoutRuleRemovesOnlyTheMatchingId() {
        val settings =
            NotificationHidingSettings()
                .withRule(packageName, profileId, NotificationHideRule.Kind.APP)
                .withRule(packageName, profileId, NotificationHideRule.Kind.EMPTY_CONTENT)
        val remaining = settings.withoutRule(settings.rules.first().id)

        assertEquals(1, remaining.rules.size)
        assertEquals(NotificationHideRule.Kind.EMPTY_CONTENT, remaining.rules.single().kind)
    }

    @Test
    fun withoutRuleIsANoOpForAnUnknownId() {
        val settings = NotificationHidingSettings().withRule(packageName, profileId, NotificationHideRule.Kind.APP)

        assertEquals(settings, settings.withoutRule(NotificationHideRuleId("missing")))
    }

    @Test
    fun withRuleCapsAtTheMaximumRuleCount() {
        var settings = NotificationHidingSettings()
        repeat(MAX_NOTIFICATION_HIDE_RULES + 5) { index ->
            settings = settings.withRule(packageName, profileId, NotificationHideRule.Kind.TITLE, value = "rule-$index")
        }

        assertEquals(MAX_NOTIFICATION_HIDE_RULES, settings.rules.size)
    }
}
