package com.riffle.app.launcher.layouts

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.decodeLauncherBackupDocument
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.layouts.AppLayout
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The layouts in the backup document: carried through, optional, and never able to fail an import. */
class LauncherBackupLayoutsTest {
    private val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private val layouts =
        listOf(AppLayout("a", app("notes"), app("maps"), 9), AppLayout("b", app("maps"), app("notes"), 3))

    private fun document(layouts: List<AppLayout>?) =
        LauncherBackupDocument(homeLayoutSet = layoutSet, launcherSettings = LauncherSettings(), layouts = layouts)

    @Test
    fun layoutsRoundTripInOrder() {
        val original = document(layouts)

        assertEquals(original, decodeLauncherBackupDocument(encodeLauncherBackupDocument(original)))
    }

    @Test
    fun aBackupWrittenBeforeLayoutsStillImports() {
        val json = JSONObject(encodeLauncherBackupDocument(document(null)))
        assertFalse(json.has("layouts"))

        assertNull(decodeLauncherBackupDocument(json.toString()).layouts)
    }

    @Test
    fun aMalformedLayoutsSectionDoesNotFailTheRestore() {
        val json = JSONObject(encodeLauncherBackupDocument(document(layouts))).put("layouts", "nope")

        assertNull(decodeLauncherBackupDocument(json.toString()).layouts)
    }
}
