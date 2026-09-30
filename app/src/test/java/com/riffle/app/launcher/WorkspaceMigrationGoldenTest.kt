package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden fixtures in the stored home layout set format, migrated to workspaces without loss. */
class WorkspaceMigrationGoldenTest {
    private fun layoutEntry(
        viewMode: String,
        vararg pages: String,
    ): String =
        """
        {
          "key": {"viewMode": "$viewMode", "deviceClass": "PHONE"},
          "layout": {
            "viewMode": "$viewMode",
            "selectedPageId": "${pages.first()}",
            "pages": [${pages.joinToString { id -> """{"id": "$id", "columns": 4, "rows": 5, "items": []}""" }}],
            "dock": {"capacity": 5, "items": []}
          }
        }
        """.trimIndent()

    private val threeModeSet =
        """
        {
          "type": "homeLayoutSet",
          "active": {"viewMode": "HOME_SCREEN_LIBRARY", "deviceClass": "PHONE"},
          "layouts": [
            ${layoutEntry("STANDARD_APP_DRAWER", "home", "second")},
            ${layoutEntry("HOME_SCREEN_LIBRARY", "lib-home")},
            ${layoutEntry("CARD_INTERFACE", "cards-home")}
          ]
        }
        """.trimIndent()

    private fun migrate(storedJson: String): WorkspaceSet = WorkspaceMigration.migrate(decodeHomeLayoutSet(storedJson))

    @Test
    fun threeModeInstallBecomesThreeWorkspacesWithTheShownModeActive() {
        val layout = migrate(threeModeSet).workspacesFor(HomeLayoutDeviceClass.PHONE)

        assertEquals(listOf("Standard", "Library", "Cards"), layout.workspaces.map { it.name })
        assertEquals(WorkspaceId("ws:phone:home_screen_library"), layout.activeId)
        assertEquals(layout.activeId, layout.defaultId)
    }

    @Test
    fun everyStoredPageSurvivesAndEveryWorkspaceIsValid() {
        val layout = migrate(threeModeSet).workspacesFor(HomeLayoutDeviceClass.PHONE)

        val pageIds = layout.workspaces.flatMap { workspace -> workspace.pages.map { it.id.value } }
        assertTrue(listOf("page:home", "page:second", "page:lib-home", "page:cards-home").all { it in pageIds })
        layout.workspaces.forEach { workspace ->
            assertEquals(workspace.name, emptyList<Any>(), WorkspaceValidation.validate(workspace))
        }
    }

    @Test
    fun modesMapToPagesFinderAndNotificationsPageSet() {
        val workspaces = migrate(threeModeSet).workspacesFor(HomeLayoutDeviceClass.PHONE).workspaces

        val standard = workspaces.first { it.name == "Standard" }
        assertEquals(LensFilter.GroupKeyIs("second"), standard.boundFilter(1))
        val library = workspaces.first { it.name == "Library" }
        assertEquals(PageRole.FINDER, (library.pages.last() as PageContainer).role)
        assertTrue(workspaces.first { it.name == "Cards" }.pages.first() is PageSetContainer)
    }

    @Test
    fun aLegacySingleLayoutDocumentStillMigrates() {
        val legacy =
            """
            {
              "viewMode": "STANDARD_APP_DRAWER",
              "selectedPageId": "home",
              "pages": [{"id": "home", "columns": 4, "rows": 5, "items": []}],
              "dock": {"capacity": 5, "items": []}
            }
            """.trimIndent()

        val layout = migrate(legacy).workspacesFor(HomeLayoutDeviceClass.PHONE)

        assertEquals(listOf("page:home"), layout.active.pages.map { it.id.value })
    }

    @Test
    fun migrationIsIdempotentAcrossAStoredRoundTrip() {
        val once = migrate(threeModeSet)
        val stored = decodeWorkspaceSet(encodeWorkspaceSet(once))

        assertEquals(once, stored)
        assertEquals(once, WorkspaceMigration.ensureMigrated(stored, decodeHomeLayoutSet(threeModeSet)))
    }

    private fun com.riffle.core.domain.launcher.workspace.Workspace.boundFilter(index: Int): LensFilter =
        ((pages[index] as PageContainer).content as PageContent.Bound).binding.lens.filter
}
