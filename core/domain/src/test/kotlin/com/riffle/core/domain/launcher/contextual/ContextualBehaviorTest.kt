package com.riffle.core.domain.launcher.contextual

import com.riffle.core.domain.launcher.cards.LauncherCardKind
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContextualBehaviorTest {
    @Test
    fun contextualSettingsDefaultOff() {
        val settings = ContextualSettings()

        assertFalse(settings.enabled)
    }

    @Test
    fun plannerDerivesPersonalProfileSignalWhenPersonalAppsArePresent() {
        val signals =
            ContextualSignalPlanner.plan(
                ContextualSignalPlanInput(personalInstalledAppCount = 2),
            )

        assertEquals(setOf(ContextualSignal.PERSONAL_PROFILE_ACTIVE), signals)
    }

    @Test
    fun plannerDerivesWorkProfileSignalWhenWorkAppsArePresent() {
        val signals =
            ContextualSignalPlanner.plan(
                ContextualSignalPlanInput(workInstalledAppCount = 1),
            )

        assertEquals(setOf(ContextualSignal.WORK_PROFILE_ACTIVE), signals)
    }

    @Test
    fun plannerDerivesNotificationActivityFromGroupsOrNotificationCount() {
        assertEquals(
            setOf(ContextualSignal.NOTIFICATION_ACTIVITY),
            ContextualSignalPlanner.plan(
                ContextualSignalPlanInput(notificationGroupCount = 1),
            ),
        )
        assertEquals(
            setOf(ContextualSignal.NOTIFICATION_ACTIVITY),
            ContextualSignalPlanner.plan(
                ContextualSignalPlanInput(notificationCount = 4),
            ),
        )
    }

    @Test
    fun plannerDerivesDayStartSignalWhenFlagged() {
        val signals =
            ContextualSignalPlanner.plan(
                ContextualSignalPlanInput(isDayStart = true),
            )

        assertEquals(setOf(ContextualSignal.DAY_START), signals)
    }

    @Test
    fun plannerReturnsNoSignalsForEmptyInput() {
        val signals = ContextualSignalPlanner.plan()

        assertEquals(emptySet(), signals)
    }

    @Test
    fun plannerReturnsCombinedSignalsInStableSignalOrder() {
        val signals =
            ContextualSignalPlanner
                .plan(
                    ContextualSignalPlanInput(
                        personalInstalledAppCount = 7,
                        workInstalledAppCount = 3,
                        notificationGroupCount = 2,
                        notificationCount = 9,
                        isDayStart = true,
                    ),
                )
                .toList()

        assertEquals(
            listOf(
                ContextualSignal.DAY_START,
                ContextualSignal.WORK_PROFILE_ACTIVE,
                ContextualSignal.PERSONAL_PROFILE_ACTIVE,
                ContextualSignal.NOTIFICATION_ACTIVITY,
            ),
            signals,
        )
    }

    @Test
    fun disabledSelectorSuppressesEveryContextualPageAndCardSignal() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = false),
                signals = ContextualSignal.entries.toSet(),
            )

        assertEquals(ContextualSelection(), selection)
    }

    @Test
    fun enabledSelectorPrioritizesEventDrivenPagesOverAvailableProfiles() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = true),
                signals =
                    setOf(
                        ContextualSignal.NOTIFICATION_ACTIVITY,
                        ContextualSignal.DAY_START,
                        ContextualSignal.APP_ACTIVITY,
                        ContextualSignal.WORK_PROFILE_ACTIVE,
                    ),
            )

        assertEquals(
            listOf(
                GeneratedLauncherPageKind.TODAY,
                GeneratedLauncherPageKind.NOTIFICATION_CARDS,
                GeneratedLauncherPageKind.FREQUENTLY_USED,
                GeneratedLauncherPageKind.WORK,
            ),
            selection.pageKinds,
        )
    }

    @Test
    fun enabledSelectorPrioritizesNotificationCardsWhenBothProfilesAreAvailable() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = true),
                signals =
                    setOf(
                        ContextualSignal.WORK_PROFILE_ACTIVE,
                        ContextualSignal.PERSONAL_PROFILE_ACTIVE,
                        ContextualSignal.NOTIFICATION_ACTIVITY,
                    ),
            )

        assertEquals(
            listOf(
                GeneratedLauncherPageKind.NOTIFICATION_CARDS,
                GeneratedLauncherPageKind.WORK,
                GeneratedLauncherPageKind.PERSONAL,
            ),
            selection.pageKinds,
        )
    }

    @Test
    fun enabledSelectorMapsSimpleSignalsToExistingCardKindsDeterministically() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = true),
                signals =
                    setOf(
                        ContextualSignal.NOTIFICATION_ACTIVITY,
                        ContextualSignal.APP_ACTIVITY,
                        ContextualSignal.DAY_START,
                    ),
            )

        assertEquals(
            listOf(
                LauncherCardKind.NOTIFICATION_GROUP,
                LauncherCardKind.APP,
            ),
            selection.cardKinds,
        )
    }

    @Test
    fun enabledSelectorReturnsNoSelectionWhenThereAreNoSignals() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = true),
                signals = emptySet(),
            )

        assertEquals(ContextualSelection(), selection)
    }
}
