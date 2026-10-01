package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.workspace.pool.PoolCutover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolStoreJsonCodecTest {
    private val identity = AppIdentity(AppPackageName("com.a"), AppActivityName("com.a.Main"))
    private val state =
        run {
            val standard = HomeLayoutDefaults.standard()
            val page =
                LauncherPage(
                    LauncherPageId("home"),
                    LauncherPageType.Home,
                    GridDimensions(4, 6),
                    listOf(
                        AppShortcutItem(
                            LauncherItemId("a"),
                            identity,
                            "A",
                            placement = GridPlacement(GridCell(0, 0), GridSpan(1, 1)),
                        ),
                        FolderItem(
                            LauncherItemId("f"),
                            "Folder",
                            listOf(AppShortcutItem(LauncherItemId("fa"), identity, "A")),
                            GridPlacement(GridCell(1, 0), GridSpan(1, 1)),
                        ),
                    ),
                )
            val layout = standard.copy(pages = listOf(page), selectedPageId = page.id)
            PoolCutover.ensureMigrated(null, HomeLayoutSet.fromLayout(layout)).state
        }

    @Test
    fun roundTripsThroughJsonText() {
        val text = encodePoolStore(state)
        assertEquals(state, decodePoolStore(text))
    }

    @Test
    fun malformedOrWrongShapedTextReadsAsNothingStored() {
        listOf("", "not json", "[]", "\"x\"", "{}", "{\"version\":99}").forEach {
            assertNull(it, decodePoolStore(it))
        }
    }

    @Test
    fun aKnownVersionWithAnUnreadableLayoutListReadsAsAnEmptyState() {
        assertNotNull(decodePoolStore("{\"version\":1,\"layouts\":\"x\"}"))
    }

    @Test
    fun aTruncatedBlobNeverThrows() {
        val text = encodePoolStore(state)
        (0 until text.length step 7).forEach { cut -> decodePoolStore(text.substring(0, cut)) }
    }

    @Test
    fun theEncodedBlobIsLauncherDataOnly() {
        val text = encodePoolStore(state)
        assertTrue(text.contains("com.a"))
        assertTrue(!text.contains("notification", ignoreCase = true))
    }
}
