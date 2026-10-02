package com.riffle.app.launcher.ics

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Feed URLs can carry secret tokens, so the ICS stores are device-local: absent from the in-app backup document
 * and excluded from Auto Backup and device transfer.
 */
class IcsBackupExclusionTest {
    @Test
    fun theLauncherBackupDocumentHasNoIcsData() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
            )
        val json = encodeLauncherBackupDocument(document)
        assertFalse(json.contains(ICS_FEEDS_DATASTORE_NAME))
        assertEquals(
            setOf("type", "version", "homeLayouts", "settings", "hiddenApps"),
            JSONObject(json).keySet(),
        )
    }

    @Test
    fun theDataStoreFileIsExcludedFromBothBackupMechanisms() {
        val path = "datastore/$ICS_FEEDS_DATASTORE_NAME.preferences_pb"
        val legacy = File("src/main/res/xml/backup_rules.xml").readText()
        val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(legacy.contains("""<exclude domain="file" path="$path" />"""))
        val cloud = modern.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfer = modern.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        assertTrue(cloud.contains(path))
        assertTrue(transfer.contains(path))
    }

    @Test
    fun theStoreNameIsStable() {
        assertEquals("riffle_ics_feeds", ICS_FEEDS_DATASTORE_NAME)
    }
}
