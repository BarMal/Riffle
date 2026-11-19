package com.riffle.app.launcher.savedhome

import com.riffle.app.launcher.LauncherBackupDocument
import com.riffle.app.launcher.decodeLauncherBackupDocument
import com.riffle.app.launcher.encodeLauncherBackupDocument
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutKind
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHomeLayoutsJsonCodecTest {
    /** Version 1 as stored: written by hand, including a stray host id and a damaged entry that must be skipped. */
    private val stored =
        """
        {"version":1,"layouts":[{"deviceClass":"PHONE","items":[
          {"id":"a","name":"Evening","kind":"saved","savedAt":5,"pool":{
            "items":[
              {"id":"m","kind":"app","label":"Mail","app":{"package":"com.example.mail","activity":"Main",
                "profile":"personal","profileType":"PERSONAL"}},
              {"id":"w","kind":"widget","label":"Clock","host":77,"provider":{"package":"com.example.clock",
                "class":"com.example.clock.W","profile":"personal","profileType":"PERSONAL"},
                "constraints":{"minColumns":2,"minRows":1,"horizontal":true,"vertical":true}}],
            "arrangements":[{"workspace":"saved","newApps":"FINDER_ONLY","pages":[
              {"id":"p1","columns":4,"rows":4,"placements":[
                {"item":"m","column":0,"row":0,"columns":1,"rows":1},
                {"item":"w","column":0,"row":1,"columns":2,"rows":1},
                {"item":"ghost","column":3,"row":3,"columns":1,"rows":1}]}]}]}},
          {"id":"auto","name":"Before Evening","kind":"before","savedAt":6,"pool":{}},
          {"name":"no id"}]}]}
        """.trimIndent()

    @Test
    fun aHandWrittenStoredSampleDecodesAndDropsWhatIsDamaged() {
        val result = decodeSavedHomeLayouts(stored)
        val list = result.layouts.forDeviceClass(HomeLayoutDeviceClass.PHONE)
        assertEquals(listOf("a", "auto"), list.map { it.id })
        val evening = list.first()
        assertEquals(2, evening.itemCount)
        assertEquals(SavedHomeLayoutKind.BEFORE_ACTIVATION, list.last().kind)
        val widget = evening.items.values.filterIsInstance<PoolWidget>().single()
        assertTrue(widget.hostIds.isEmpty())
        assertEquals("com.example.clock", widget.provider?.packageName?.value)
        assertFalse(result.newerThanThisBuild)
    }

    @Test
    fun nonsenseDecodesAsNothing() {
        assertTrue(decodeSavedHomeLayouts("not json").layouts.isEmpty)
        assertTrue(decodeSavedHomeLayouts("[1,2]").layouts.isEmpty)
    }

    @Test
    fun theBackupDocumentCarriesTheLayoutsAndOmitsTheSectionWhenThereAreNone() {
        val layouts = decodeSavedHomeLayouts(stored).layouts
        val base =
            LauncherBackupDocument(HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()), LauncherSettings())
        val withLayouts =
            decodeLauncherBackupDocument(encodeLauncherBackupDocument(base.copy(savedHomeLayouts = layouts)))
        assertEquals(layouts, withLayouts.savedHomeLayouts)
        assertFalse(encodeLauncherBackupDocument(base).contains("savedHomeLayouts"))
        assertNull(decodeLauncherBackupDocument(encodeLauncherBackupDocument(base)).savedHomeLayouts)
    }
}
