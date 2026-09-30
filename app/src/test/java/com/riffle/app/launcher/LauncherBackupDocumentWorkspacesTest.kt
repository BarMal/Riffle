package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherBackupDocumentWorkspacesTest {
    private val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    private fun document(workspaces: Boolean) =
        LauncherBackupDocument(
            homeLayoutSet = layoutSet,
            launcherSettings = LauncherSettings(),
            workspaceSet = if (workspaces) WorkspaceMigration.migrate(layoutSet) else null,
        )

    @Test
    fun roundTripsWorkspaces() {
        val original = document(workspaces = true)

        assertEquals(original, decodeLauncherBackupDocument(encodeLauncherBackupDocument(original)))
    }

    @Test
    fun backupWithoutWorkspacesOmitsTheKeyAndDecodesToNull() {
        val encoded = encodeLauncherBackupDocument(document(workspaces = false))

        assertFalse(JSONObject(encoded).has("workspaces"))
        assertNull(decodeLauncherBackupDocument(encoded).workspaceSet)
    }

    @Test
    fun malformedWorkspacesDoNotFailTheRestore() {
        val json = JSONObject(encodeLauncherBackupDocument(document(workspaces = false))).put("workspaces", "garbage")

        val decoded = decodeLauncherBackupDocument(json.toString())

        assertNull(decoded.workspaceSet)
        assertEquals(layoutSet, decoded.homeLayoutSet)
    }
}
