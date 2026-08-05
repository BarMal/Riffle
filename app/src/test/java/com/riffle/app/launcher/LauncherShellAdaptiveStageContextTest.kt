package com.riffle.app.launcher

import androidx.compose.runtime.saveable.SaverScope
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellAdaptiveStageContextTest {
    @Test
    fun saverRestoresDetailOriginWhenSelectionMovedToAnotherStage() {
        val context =
            AdaptiveStageInteractionContext(
                selectedStageKey = "work:calendar",
                detailStageKey = "personal:mail",
                focusedCardKey = "work:calendar:focused",
                detailCardKey = "personal:mail:message",
                templateId = "shared",
                scrollOffsetPx = 64,
            )

        val saved: List<String> =
            with(AdaptiveStageInteractionContextSaver) {
                with(
                    object : SaverScope {
                        override fun canBeSaved(value: Any): Boolean = true
                    },
                ) {
                    save(context)!!
                }
            }

        assertEquals(context, AdaptiveStageInteractionContextSaver.restore(saved))
    }

    @Test
    fun reconciliationClearsUnavailableDetailOriginWithoutChangingSelection() {
        val context =
            AdaptiveStageInteractionContext(
                selectedStageKey = "work:calendar",
                detailStageKey = "personal:mail",
                detailCardKey = "personal:mail:message",
            )

        assertEquals(
            context.copy(detailStageKey = null),
            context.reconcile(
                availableStageKeys = setOf("work:calendar"),
                availableCardKeys = setOf("personal:mail:message"),
            ),
        )
    }

    @Test
    fun saverRestoresLegacyPayloadWithoutShiftingExistingFields() {
        val restored =
            AdaptiveStageInteractionContextSaver.restore(
                listOf("work:calendar", "focused", "detail", "shared", "12"),
            )

        assertEquals(
            AdaptiveStageInteractionContext(
                selectedStageKey = "work:calendar",
                focusedCardKey = "focused",
                detailCardKey = "detail",
                templateId = "shared",
                scrollOffsetPx = 12,
            ),
            restored,
        )
    }
}
