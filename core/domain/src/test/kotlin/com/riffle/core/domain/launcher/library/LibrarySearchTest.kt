package com.riffle.core.domain.launcher.library

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LibrarySearchTest {
    private fun app(label: String) =
        InstalledApp(AppIdentity(AppPackageName("com.example.$label"), AppActivityName(".Main")), label)

    private fun shortcut(id: String) =
        AppShortcut(
            appIdentity = AppIdentity(AppPackageName("com.example.cam"), AppActivityName(".Main")),
            id = AppShortcutId(id),
            shortLabel = id,
        )

    @Test
    fun appsComeFirstThenShortcutsInTheirRankedOrder() {
        val hits = LibrarySearch.order(apps = listOf(app("zeta"), app("alpha")), shortcuts = listOf(shortcut("selfie")))

        assertEquals(3, hits.size)
        assertEquals("zeta", (hits[0] as LibraryHit.App).app.label)
        assertEquals("alpha", (hits[1] as LibraryHit.App).app.label)
        assertIs<LibraryHit.Shortcut>(hits[2])
    }

    @Test
    fun theTopHitIsTheFirstAppElseTheFirstOtherResult() {
        val withApps = LibrarySearch.order(apps = listOf(app("one")), shortcuts = listOf(shortcut("s")))
        assertEquals("one", (LibrarySearch.topHit(withApps) as LibraryHit.App).app.label)

        val shortcutsOnly = LibrarySearch.order(apps = emptyList(), shortcuts = listOf(shortcut("s")))
        assertIs<LibraryHit.Shortcut>(LibrarySearch.topHit(shortcutsOnly))

        assertNull(LibrarySearch.topHit(LibrarySearch.order(emptyList())))
    }
}
