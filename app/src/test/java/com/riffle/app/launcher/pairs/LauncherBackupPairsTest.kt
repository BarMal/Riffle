package com.riffle.app.launcher.pairs

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.decodeLauncherBackupDocument
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The pairs in the backup document: carried through, optional, and never able to fail an import. */
class LauncherBackupPairsTest {
    private val pairSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    private fun app(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private val pairs =
        listOf(AppPair("a", app("notes"), app("maps"), 9), AppPair("b", app("maps"), app("notes"), 3))

    private fun document(pairs: List<AppPair>?) =
        LauncherBackupDocument(homeLayoutSet = pairSet, launcherSettings = LauncherSettings(), pairs = pairs)

    @Test
    fun pairsRoundTripInOrder() {
        val original = document(pairs)

        assertEquals(original, decodeLauncherBackupDocument(encodeLauncherBackupDocument(original)))
    }

    @Test
    fun aBackupWrittenBeforePairsStillImports() {
        val json = JSONObject(encodeLauncherBackupDocument(document(null)))
        assertFalse(json.has("pairs"))

        assertNull(decodeLauncherBackupDocument(json.toString()).pairs)
    }

    @Test
    fun aMalformedPairsSectionDoesNotFailTheRestore() {
        val json = JSONObject(encodeLauncherBackupDocument(document(pairs))).put("pairs", "nope")

        assertNull(decodeLauncherBackupDocument(json.toString()).pairs)
    }
}
