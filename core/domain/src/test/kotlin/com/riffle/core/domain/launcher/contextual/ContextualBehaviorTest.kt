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
    fun selectorReturnsNoContextualPagesOrCardsWhenDisabled() {
        val selection =
            ContextualBehaviorSelector.select(
                settings = ContextualSettings(enabled = false),
                signals = ContextualSignal.entries.toSet(),
            )

        assertEquals(ContextualSelection(), selection)
    }

    @Test
    fun enabledSelectorMapsSimpleSignalsToExistingPageKindsDeterministically() {
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
                GeneratedLauncherPageKind.WORK,
                GeneratedLauncherPageKind.FREQUENTLY_USED,
                GeneratedLauncherPageKind.NOTIFICATION_CARDS,
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
                LauncherCardKind.APP,
                LauncherCardKind.NOTIFICATION_GROUP,
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
