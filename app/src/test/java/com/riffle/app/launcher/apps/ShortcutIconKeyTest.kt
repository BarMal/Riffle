package com.riffle.app.launcher.apps

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutIconKeyTest {
    private val identity = AppIdentity(AppPackageName("com.files"), AppActivityName(".Main"))

    @Test
    fun theKeyTheDomainWritesIsReadBackAsProfilePackageAndShortcutId() {
        val handle = ItemImageKeys.shortcutIcon(identity, "recent")
        assertTrue(ShortcutIconKey.isShortcutIcon(handle))
        assertEquals(Triple("personal", "com.files", "recent"), ShortcutIconKey.parse(handle))
    }

    @Test
    fun aShortcutIdKeepsEverythingAfterThePackageEvenSlashes() {
        val handle = ItemImageKeys.shortcutIcon(identity, "folder/recent:1")
        assertEquals(Triple("personal", "com.files", "folder/recent:1"), ShortcutIconKey.parse(handle))
    }

    @Test
    fun anythingElseIsNotAShortcutKey() {
        assertFalse(ShortcutIconKey.isShortcutIcon(ItemImageHandle("app-icon:personal:com.files/.Main")))
        assertNull(ShortcutIconKey.parse(ItemImageHandle("app-icon:personal:com.files/.Main")))
        assertNull(ShortcutIconKey.parse(ItemImageHandle("shortcut-icon:personal/com.files")))
        assertNull(ShortcutIconKey.parse(ItemImageHandle("shortcut-icon:personal//x")))
    }
}
