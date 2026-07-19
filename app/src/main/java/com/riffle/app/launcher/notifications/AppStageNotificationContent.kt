package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageContent
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageContentSnapshot
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageSnapshot
import com.riffle.core.domain.launcher.cards.LauncherCardId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

/** Process-only card data for one notification or media item in an app stage. */
data class AppStageNotificationCard(
    val content: AppStageContent,
    val notificationKey: LauncherNotificationKey,
    val title: String,
    val text: String,
    val isRedacted: Boolean,
    val supportedActions: Set<NotificationStageAction>,
)

/** Fixed content for a pinned stage that currently has no dynamic cards. */
data class AppStageEmptyAppCard(
    val app: InstalledApp,
    val shortcuts: List<AppShortcut>,
)

/** Process-only shell projection pairing reconciled stages with their focused live card data. */
data class AppStageShellState(
    val snapshot: AppStageSnapshot,
    val notificationCards: List<AppStageNotificationCard>,
    val emptyAppCards: Map<AppStageId, AppStageEmptyAppCard>,
)

fun LauncherShellState.appStageShellState(
    actionAvailability: NotificationStageActionAvailability = NotificationStageActionAvailability.None,
): AppStageShellState {
    val notificationCards =
        appStageNotificationCards(
            notifications = notificationGroupsByApp.flatMap { group -> group.notifications },
            notificationAccessStatus = notificationAccessStatus,
            profileContentVisibility = profileContentVisibility,
            actionAvailability = actionAvailability,
        )
    val snapshot = appStageSnapshot(contentSnapshot = appStageContentSnapshot(notificationCards))
    return AppStageShellState(
        snapshot = snapshot,
        notificationCards = notificationCards,
        emptyAppCards =
            snapshot.stages
                .filter { stage -> stage.content.isEmpty() }
                .mapNotNull { stage -> appStageEmptyAppCard(stage.id, installedApps, appShortcutsByApp) }
                .associateBy { card -> AppStageId(card.app.identity.packageName, card.app.identity.profile.id) },
    )
}

/** Builds stable, privacy-aware dynamic input for the app-stage planner. */
fun appStageNotificationCards(
    notifications: List<LauncherNotification>,
    notificationAccessStatus: NotificationAccessStatus,
    profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility>,
    actionAvailability: NotificationStageActionAvailability = NotificationStageActionAvailability.None,
): List<AppStageNotificationCard> {
    if (notificationAccessStatus != NotificationAccessStatus.GRANTED) return emptyList()
    return notifications.mapNotNull { notification ->
        val visibility =
            profileContentVisibility[notification.profileId]
                ?: AppProfileContentVisibility.REDACTED_UNAVAILABLE
        when (visibility) {
            AppProfileContentVisibility.REDACTED_LOCKED,
            AppProfileContentVisibility.REDACTED_UNAVAILABLE,
            -> null

            AppProfileContentVisibility.VISIBLE,
            AppProfileContentVisibility.REDACTED_QUIET,
            ->
                notification.toAppStageCard(
                    isRedacted = visibility != AppProfileContentVisibility.VISIBLE,
                    actionAvailability = actionAvailability,
                )
        }
    }.sortedWith(
        compareByDescending<AppStageNotificationCard> { it.content.meaningfulActivityAtEpochMillis }
            .thenBy { it.notificationKey.value },
    )
}

fun appStageContentSnapshot(cards: List<AppStageNotificationCard>): AppStageContentSnapshot =
    AppStageContentSnapshot(cards.map(AppStageNotificationCard::content))

fun appStageEmptyAppCard(
    stageId: AppStageId,
    installedApps: List<InstalledApp>,
    shortcutsByApp: AppShortcutsByApp,
): AppStageEmptyAppCard? =
    installedApps
        .asSequence()
        .filter { app ->
            app.identity.packageName == stageId.packageName &&
                app.identity.profile.id == stageId.profileId
        }
        .sortedBy { app -> app.identity.activityName.value }
        .firstOrNull()
        ?.let { app ->
            AppStageEmptyAppCard(
                app = app,
                shortcuts = shortcutsByApp[app.identity].orEmpty().filter(AppShortcut::enabled),
            )
        }

private fun LauncherNotification.toAppStageCard(
    isRedacted: Boolean,
    actionAvailability: NotificationStageActionAvailability,
): AppStageNotificationCard {
    val contentKind = if (isMediaSession) AppStageContentKind.MEDIA else AppStageContentKind.NOTIFICATION
    val actions =
        if (isRedacted) {
            emptySet()
        } else {
            actionAvailability.actionsFor(key, isMediaSession).toMutableSet().apply {
                if (canDismiss) add(NotificationStageAction.Dismiss)
            }
        }
    return AppStageNotificationCard(
        content =
            AppStageContent(
                id = LauncherCardId("stage-notification:${profileId.value}:${key.value}"),
                stageId = AppStageId(packageName, profileId),
                kind = contentKind,
                meaningfulActivityAtEpochMillis = postedAtEpochMillis.coerceAtLeast(0L),
            ),
        notificationKey = key,
        title = if (isRedacted) "Hidden notification" else title,
        text = if (isRedacted) "Content hidden for this profile" else text,
        isRedacted = isRedacted,
        supportedActions = actions,
    )
}
