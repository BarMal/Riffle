package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GridPlacementEngineDuplicateItemIdTest {
    private val engine = GridPlacementEngine()
    private val page =
        LauncherPage(
            id = LauncherPageId("home"),
            grid = GridDimensions(columns = 4, rows = 5),
            items =
                listOf(
                    appItem(
                        id = "camera",
                        placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                    ),
                ),
        )

    @Test
    fun rejectsDuplicateItemIdPlacementEvenWhenCellsDoNotCollide() {
        val result =
            engine.placeItem(
                page = page,
                item =
                    appItem(
                        id = "camera",
                        placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
                    ),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_ITEM_ID, rejected.reason)
    }

    @Test
    fun rejectsDuplicateItemIdWhenPlacingFirstAvailableCell() {
        val result =
            engine.placeItemInFirstAvailableCell(
                page = page,
                item = appItem(id = "camera"),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_ITEM_ID, rejected.reason)
    }

    private fun appItem(
        id: String,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
            placement = placement,
        )
}
