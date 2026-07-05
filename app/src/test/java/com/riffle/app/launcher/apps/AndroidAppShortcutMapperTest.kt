package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAppShortcutMapperTest {
    private val mapper = AndroidAppShortcutMapper()
    private val identity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.camera"),
            activityName = AppActivityName(".MainActivity"),
        )

    @Test
    fun mapsAndroidShortcutToDomainShortcut() {
        val shortcut =
            mapper.map(
                identity = identity,
                shortcut =
                    AndroidAppShortcut(
                        id = "scan",
                        shortLabel = "Scan",
                        longLabel = "Scan a document",
                        enabled = true,
                        disabledMessage = null,
                    ),
            )

        assertEquals(AppShortcutId("scan"), shortcut.id)
        assertEquals(identity, shortcut.appIdentity)
        assertEquals("Scan", shortcut.shortLabel)
        assertEquals("Scan a document", shortcut.longLabel)
    }

    @Test
    fun fallsBackToShortcutIdWhenShortLabelIsBlank() {
        val shortcut =
            mapper.map(
                identity = identity,
                shortcut =
                    AndroidAppShortcut(
                        id = "scan",
                        shortLabel = "",
                        longLabel = "",
                        enabled = true,
                        disabledMessage = "",
                    ),
            )

        assertEquals("scan", shortcut.shortLabel)
        assertNull(shortcut.longLabel)
        assertNull(shortcut.disabledMessage)
    }

    @Test
    fun normalizesShortcutLabelWhitespace() {
        val shortcut =
            mapper.map(
                identity = identity,
                shortcut =
                    AndroidAppShortcut(
                        id = "scan",
                        shortLabel = "  Scan\nNow  ",
                        longLabel = "\tScan   a\ndocument ",
                        enabled = true,
                        disabledMessage = null,
                    ),
            )

        assertEquals("Scan Now", shortcut.shortLabel)
        assertEquals("Scan a document", shortcut.longLabel)
    }

    @Test
    fun preservesDisabledStateAndMessage() {
        val shortcut =
            mapper.map(
                identity = identity,
                shortcut =
                    AndroidAppShortcut(
                        id = "scan",
                        shortLabel = "Scan",
                        longLabel = null,
                        enabled = false,
                        disabledMessage = "Unavailable",
                    ),
            )

        assertFalse(shortcut.enabled)
        assertEquals("Unavailable", shortcut.disabledMessage)
    }
}
