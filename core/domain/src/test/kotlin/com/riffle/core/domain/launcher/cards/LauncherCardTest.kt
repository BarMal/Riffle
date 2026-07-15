package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LauncherCardTest {
    @Test
    fun acceptsValidCardSizes() {
        validSizes.forEach { size ->
            assertEquals(size.columns * size.rows, size.cellCount)
        }
    }

    @Test
    fun rejectsInvalidCardSizes() {
        invalidSizeInputs.forEach { (columns, rows) ->
            assertFailsWith<IllegalArgumentException>("columns=$columns rows=$rows") {
                LauncherCardSize(columns = columns, rows = rows)
            }
        }
    }

    @Test
    fun derivesKindsFromSourceRefs() {
        assertEquals(LauncherCardKind.APP, LauncherCardSourceRef.App(appIdentity()).kind)
        assertEquals(
            LauncherCardKind.WIDGET_PROVIDER,
            LauncherCardSourceRef.WidgetProvider(widgetProviderIdentity()).kind,
        )
        assertEquals(
            LauncherCardKind.NOTIFICATION_GROUP,
            LauncherCardSourceRef.AppNotificationGroup(notificationGroupKey()).kind,
        )
        assertEquals(LauncherCardKind.MEDIA, LauncherCardSourceRef.Media(sourceId(), AppProfile.personal().id).kind)
        assertEquals(LauncherCardKind.AGENDA, LauncherCardSourceRef.Agenda(sourceId(), AppProfile.personal().id).kind)
        assertEquals(LauncherCardKind.ALARM, LauncherCardSourceRef.Alarm(sourceId(), AppProfile.personal().id).kind)
        assertEquals(LauncherCardKind.TASK, LauncherCardSourceRef.Task(sourceId(), AppProfile.personal().id).kind)
    }

    @Test
    fun storesSourceRefsWithExistingDomainIds() {
        val app = appIdentity(packageName = "com.riffle.mail")
        val widgetProvider = widgetProviderIdentity(packageName = "com.riffle.clock")
        val notificationGroup = notificationGroupKey(packageName = "com.riffle.chat")

        assertEquals(app, LauncherCardSourceRef.App(app).identity)
        assertEquals(widgetProvider, LauncherCardSourceRef.WidgetProvider(widgetProvider).identity)
        assertEquals(notificationGroup, LauncherCardSourceRef.AppNotificationGroup(notificationGroup).key)
    }

    @Test
    fun keepsCardIdStableAcrossContentChanges() {
        val card =
            LauncherCard(
                id = LauncherCardId("card:mail"),
                sourceRef = LauncherCardSourceRef.App(appIdentity(packageName = "com.riffle.mail")),
                size = LauncherCardSize(columns = 2, rows = 1),
            )

        val resized = card.copy(size = LauncherCardSize(columns = 2, rows = 2))

        assertEquals(card.id, resized.id)
        assertEquals(LauncherCardKind.APP, resized.kind)
    }

    @Test
    fun comparesCardsByStableValueFields() {
        val first =
            LauncherCard(
                id = LauncherCardId("card:mail"),
                sourceRef = LauncherCardSourceRef.App(appIdentity(packageName = "com.riffle.mail")),
                size = LauncherCardSize(columns = 2, rows = 1),
            )
        val matching =
            LauncherCard(
                id = LauncherCardId("card:mail"),
                sourceRef = LauncherCardSourceRef.App(appIdentity(packageName = "com.riffle.mail")),
                size = LauncherCardSize(columns = 2, rows = 1),
            )
        val differentId = matching.copy(id = LauncherCardId("card:mail:alternate"))

        assertEquals(first, matching)
        assertEquals(first.hashCode(), matching.hashCode())
        assertNotEquals(first, differentId)
    }

    @Test
    fun keepsSourcePayloadSeparateFromPersistableUserIntent() {
        val card =
            LauncherCard(
                id = LauncherCardId("card:mail"),
                sourceRef = LauncherCardSourceRef.AppNotificationGroup(notificationGroupKey()),
                content = LauncherCardContent.Text(title = "Private message", body = "Do not persist this"),
                state = LauncherCardState.READY,
                privacy = LauncherCardPrivacy.REDACTED,
                chronology = LauncherCardChronology(updatedAtEpochMillis = 100L, rankingScore = 2),
                dismissibility = LauncherCardDismissibility.DISMISSIBLE,
                supportedActions = setOf(LauncherCardAction.DISMISS, LauncherCardAction.EXPAND),
                userIntent = LauncherCardUserIntent(isPinned = true),
            )

        assertEquals(LauncherCardPrivacy.REDACTED, card.privacy)
        assertEquals(LauncherCardDismissibility.DISMISSIBLE, card.dismissibility)
        assertTrue(card.supportsExpansion)
        assertTrue(card.userIntent.isPinned)
    }

    @Test
    fun exposesAllRecoverableSourceAndActionStates() {
        assertEquals(
            setOf(
                LauncherCardState.LOADING,
                LauncherCardState.READY,
                LauncherCardState.EMPTY,
                LauncherCardState.UNAVAILABLE,
                LauncherCardState.PERMISSION_REQUIRED,
                LauncherCardState.PROFILE_LOCKED,
                LauncherCardState.PROFILE_QUIET,
                LauncherCardState.REDACTED,
                LauncherCardState.STALE,
                LauncherCardState.ACTION_IN_PROGRESS,
                LauncherCardState.ACTION_FAILED,
                LauncherCardState.REMOVED,
            ),
            LauncherCardState.entries.toSet(),
        )
    }

    @Test
    fun rejectsInvalidTransientContentAndStableSourceValues() {
        assertFailsWith<IllegalArgumentException> { LauncherCardSourceId(" ") }
        assertFailsWith<IllegalArgumentException> { LauncherCardChronology(updatedAtEpochMillis = -1L) }
        assertFailsWith<IllegalArgumentException> {
            LauncherCardContent.Progress(title = "Download", current = 2, maximum = 1)
        }
    }

    private val validSizes =
        listOf(
            LauncherCardSize(columns = MIN_LAUNCHER_CARD_SPAN_COLUMNS, rows = MIN_LAUNCHER_CARD_SPAN_ROWS),
            LauncherCardSize(columns = 2, rows = 3),
            LauncherCardSize(columns = MAX_LAUNCHER_CARD_SPAN_COLUMNS, rows = MAX_LAUNCHER_CARD_SPAN_ROWS),
        )

    private val invalidSizeInputs =
        listOf(
            0 to 1,
            1 to 0,
            -1 to 1,
            1 to -1,
            MAX_LAUNCHER_CARD_SPAN_COLUMNS + 1 to 1,
            1 to MAX_LAUNCHER_CARD_SPAN_ROWS + 1,
        )

    private fun appIdentity(
        packageName: String = "com.riffle.mail",
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
    ): AppIdentity =
        AppIdentity(
            packageName = AppPackageName(packageName),
            activityName = AppActivityName(activityName),
            profile = profile,
        )

    private fun widgetProviderIdentity(
        packageName: String = "com.riffle.clock",
        className: String = ".ClockWidget",
        profile: AppProfile = AppProfile.personal(),
    ): WidgetProviderIdentity =
        WidgetProviderIdentity(
            packageName = AppPackageName(packageName),
            className = WidgetProviderClassName(className),
            profile = profile,
        )

    private fun notificationGroupKey(
        packageName: String = "com.riffle.chat",
        profile: AppProfile = AppProfile.personal(),
    ): AppNotificationGroupKey =
        AppNotificationGroupKey(
            packageName = AppPackageName(packageName),
            profileId = profile.id,
        )

    private fun sourceId(value: String = "source:default") = LauncherCardSourceId(value)
}
