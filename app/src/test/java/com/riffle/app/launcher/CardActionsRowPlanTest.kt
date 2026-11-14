package com.riffle.app.launcher

import com.riffle.app.R
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Where its actions sit" offers only what can happen: a side Dock holds no row for an open card's actions. */
class CardActionsRowPlanTest {
    @Test
    fun aBottomDockOffersBothAndKeepsTheStoredChoice() {
        val plan = cardActionsRowPlan(CardActionsPlacement.ON_CARD, DockPosition.BOTTOM)

        assertEquals(listOf(CardActionsPlacement.IN_DOCK, CardActionsPlacement.ON_CARD), plan.options)
        assertEquals(CardActionsPlacement.ON_CARD, plan.selected)
        assertEquals(R.string.settings_stream_actions_position_subtitle, plan.subtitleRes)
    }

    @Test
    fun aSideDockOffersOnlyTheCardWhateverIsStored() {
        listOf(DockPosition.LEFT, DockPosition.RIGHT).forEach { edge ->
            val plan = cardActionsRowPlan(CardActionsPlacement.IN_DOCK, edge)

            assertEquals(listOf(CardActionsPlacement.ON_CARD), plan.options)
            assertEquals(CardActionsPlacement.ON_CARD, plan.selected)
            assertEquals(R.string.settings_stream_actions_position_side_dock_subtitle, plan.subtitleRes)
        }
    }
}
