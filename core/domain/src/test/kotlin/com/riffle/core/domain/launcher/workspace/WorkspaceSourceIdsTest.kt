package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals

/** The ids are persisted in stored lenses, so the exact strings are pinned. */
class WorkspaceSourceIdsTest {
    @Test
    fun storedStringsArePinned() {
        assertEquals("apps.frequent", WorkspaceSourceIds.FREQUENT_APPS.value)
        assertEquals("apps.favourite", WorkspaceSourceIds.FAVOURITE_APPS.value)
        assertEquals("home.grid", WorkspaceSourceIds.HOME_GRID.value)
        assertEquals("app.profile", WorkspaceSourceIds.APP_PROFILE_EXT.value)
        assertEquals("work", WorkspaceSourceIds.PROFILE_WORK)
        assertEquals("personal", WorkspaceSourceIds.PROFILE_PERSONAL)
    }

    @Test
    fun migratedLensesUseTheCanonicalAndWs5Strings() {
        assertEquals("apps.all", SourceIds.ALL_APPS.value)
        assertEquals("apps.recent", SourceIds.RECENT_APPS.value)
        assertEquals("notifications", SourceIds.NOTIFICATIONS.value)
    }
}
