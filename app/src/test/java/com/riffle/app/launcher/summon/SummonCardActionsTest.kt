package com.riffle.app.launcher.summon

import com.riffle.app.R
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.notifications.NotificationStageAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SummonCardActionsTest {
    private val mail = AppIdentity(AppPackageName("pkg.mail"), AppActivityName("pkg.mail.Main"))

    private fun notification(
        key: String,
        canDismiss: Boolean,
    ) = LauncherNotification(LauncherNotificationKey(key), mail.packageName, canDismiss = canDismiss)

    private fun group(vararg notifications: LauncherNotification) =
        AppNotificationGroup(
            packageName = mail.packageName,
            profileId = mail.profile.id,
            latestCategory = NotificationCategory.UNKNOWN,
            latestAgeBucket = NotificationAgeBucket.entries.first(),
            notifications = notifications.toList(),
        )

    @Test
    fun anAppsCardOpensItsTopNotificationClearsWhatCanBeClearedAndOpensTheApp() {
        val entry =
            SummonEntry.App(
                mail,
                "Mail",
                group(notification("top", canDismiss = false), notification("older", canDismiss = true)),
            )
        assertEquals(
            listOf(
                SummonCardAction(
                    R.string.summon_card_open_notification,
                    LauncherShellAction.PerformNotificationStageAction(
                        LauncherNotificationKey("top"),
                        NotificationStageAction.Open,
                    ),
                ),
                SummonCardAction(
                    R.string.summon_card_clear,
                    LauncherShellAction.DismissNotifications(listOf(LauncherNotificationKey("older"))),
                ),
                SummonCardAction(R.string.summon_card_open_app, LauncherShellAction.LaunchApp(mail)),
            ),
            summonCardActions(entry),
        )
    }

    @Test
    fun nothingClearableMeansNoClearButton() {
        val entry = SummonEntry.App(mail, "Mail", group(notification("only", canDismiss = false)))
        assertEquals(
            listOf(R.string.summon_card_open_notification, R.string.summon_card_open_app),
            summonCardActions(entry).map { it.label },
        )
    }

    @Test
    fun aGlancesCardOpensItsPlaceInTheStreamAndOtherEntriesHaveNoCard() {
        assertEquals(
            listOf(SummonCardAction(R.string.summon_card_open_stream, LauncherShellAction.OpenStream)),
            summonCardActions(SummonEntry.Glance(GlanceWidgetKind.NEXT_EVENT, "Next event")),
        )
        assertEquals(emptyList<SummonCardAction>(), summonCardActions(SummonEntry.App(mail, "Mail")))
        assertEquals(
            emptyList<SummonCardAction>(),
            summonCardActions(SummonEntry.Favourites("Favourites", emptyList())),
        )
    }
}
