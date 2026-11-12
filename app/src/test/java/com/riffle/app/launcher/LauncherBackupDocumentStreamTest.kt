package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Stream's layouts in the backup document: carried through, optional, and never able to fail an import. */
class LauncherBackupDocumentStreamTest {
    private val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    private val custom =
        StreamLayouts().with(
            HomeLayoutDeviceClass.PHONE,
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST),
            ),
        )

    private fun document(stream: StreamLayouts?) =
        LauncherBackupDocument(homeLayoutSet = layoutSet, launcherSettings = LauncherSettings(), stream = stream)

    @Test
    fun roundTripsTheStreamLayouts() {
        val original = document(custom)
        assertEquals(original, decodeLauncherBackupDocument(encodeLauncherBackupDocument(original)))
    }

    @Test
    fun aBackupWrittenBeforeTheStreamStillImports() {
        val json = JSONObject(encodeLauncherBackupDocument(document(null)))
        assertFalse(json.has("stream"))

        val decoded = decodeLauncherBackupDocument(json.toString())
        assertNull(decoded.stream)
        assertEquals(layoutSet, decoded.homeLayoutSet)
        assertTrue(decoded.isImportableBackup())
    }

    @Test
    fun layoutsThatAreAllDefaultsAreNotWritten() {
        val defaults = StreamLayouts().with(HomeLayoutDeviceClass.PHONE, StreamLayout.DEFAULT)
        assertFalse(JSONObject(encodeLauncherBackupDocument(document(defaults))).has("stream"))
    }

    @Test
    fun aMalformedStreamSectionDoesNotFailTheRestore() {
        val json = JSONObject(encodeLauncherBackupDocument(document(custom))).put("stream", "garbage")

        val decoded = decodeLauncherBackupDocument(json.toString())

        assertNull(decoded.stream)
        assertEquals(layoutSet, decoded.homeLayoutSet)
    }
}
