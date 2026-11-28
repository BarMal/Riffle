package com.riffle.app.launcher.summon

import androidx.annotation.StringRes
import com.riffle.app.R
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.notifications.NotificationStageAction

/** One button on a Summon card: its label and what it does. */
internal data class SummonCardAction(
    @StringRes val label: Int,
    val action: LauncherShellAction,
)

/**
 * The buttons a kept card offers. An app's card acts on its notifications through the same shell actions the Stream
 * uses (open the top one, clear the clearable ones) and can open the app; a glance's card opens its place in the
 * Stream (its own controls, such as play and pause, are live on the card itself). Anything else has no card.
 */
internal fun summonCardActions(entry: SummonEntry): List<SummonCardAction> =
    when (entry) {
        is SummonEntry.App -> {
            val group = entry.notifications?.takeIf { it.notifications.isNotEmpty() }
            if (group == null) {
                emptyList()
            } else {
                listOfNotNull(
                    SummonCardAction(
                        R.string.summon_card_open_notification,
                        LauncherShellAction.PerformNotificationStageAction(
                            group.notifications.first().key,
                            NotificationStageAction.Open,
                        ),
                    ),
                    group.dismissibleNotificationKeys.takeIf { it.isNotEmpty() }?.let {
                        SummonCardAction(R.string.summon_card_clear, LauncherShellAction.DismissNotifications(it))
                    },
                    SummonCardAction(R.string.summon_card_open_app, LauncherShellAction.LaunchApp(entry.identity)),
                )
            }
        }
        is SummonEntry.Glance ->
            listOf(
                SummonCardAction(R.string.summon_card_open_stream, LauncherShellAction.OpenStream),
            )
        is SummonEntry.Folder, is SummonEntry.Favourites, is SummonEntry.Action -> emptyList()
    }
