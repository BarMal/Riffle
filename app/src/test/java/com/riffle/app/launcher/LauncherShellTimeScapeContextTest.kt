package com.riffle.app.launcher

import androidx.compose.runtime.saveable.SaverScope
import com.riffle.core.domain.launcher.cards.TimeScapeInteractionContext
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellTimeScapeContextTest {
    @Test
    fun saverRestoresDetailOriginWhenSelectionMovedToAnotherStage() {
        val context =
            TimeScapeInteractionContext(
                selectedStageKey = "work:calendar",
                detailStageKey = "personal:mail",
                focusedCardKey = "work:calendar:focused",
                detailCardKey = "personal:mail:message",
                templateId = "shared",
                scrollOffsetPx = 64,
            )

        val saved: List<String> =
            with(TimeScapeInteractionContextSaver) {
                with(
                    object : SaverScope {
                        override fun canBeSaved(value: Any): Boolean = true
                    },
                ) {
                    save(context)!!
                }
            }

        assertEquals(context, TimeScapeInteractionContextSaver.restore(saved))
    }

    @Test
    fun reconciliationClearsUnavailableDetailOriginWithoutChangingSelection() {
        val context =
            TimeScapeInteractionContext(
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
            TimeScapeInteractionContextSaver.restore(
                listOf("work:calendar", "focused", "detail", "shared", "12"),
            )

        assertEquals(
            TimeScapeInteractionContext(
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
