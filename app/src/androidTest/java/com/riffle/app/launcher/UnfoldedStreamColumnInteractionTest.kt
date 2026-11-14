package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Stream on an unfolded posture (ADR 0003, Amendment 2) on a device, through the real [HomeDestination]: pressing the
 * Dock on the right edge toward that edge opens the stack in a column attached to the Dock's inner side, with Home still
 * there beside it. The shell answers `OpenStream` the way the real one does, so the Stream shows; the device class
 * comes from the state, so this holds on any screen the device has. Not run in the author's environment.
 */
@RunWith(AndroidJUnit4::class)
class UnfoldedStreamColumnInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val docked = (0 until DOCKED_COUNT).map { index -> shortcut("docked$index") }

    @Test
    fun pressingTheRightDockOpensTheStackInAColumnAttachedToIt() {
        val actions = mutableListOf<LauncherShellAction>()
        var shown by mutableStateOf(false)
        setContent(actions, isShown = { shown }, onShown = { shown = true })

        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(PRESS_PX / STEPS, 0f), delayMillis = STEP_MILLIS) }
            up()
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithTag(STREAM_COLUMN_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { assertEquals(LauncherShellAction.OpenStream, actions.first()) }
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val column = composeRule.onNodeWithTag(STREAM_COLUMN_TEST_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the column is not beside the Dock: $column $dock",
            column.right <= dock.right && column.right > dock.left,
        )
        assertTrue("the column is not about a third: $column in $root", column.width < root.width * MAX_SHARE)
        // Home stays: its grid is still on screen beside the column.
        assertTrue(
            "Home's grid is gone",
            composeRule.onAllNodesWithTag(HOME_WORKSPACE_GRID_TEST_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun setContent(
        actions: MutableList<LauncherShellAction>,
        isShown: () -> Boolean,
        onShown: () -> Unit,
    ) {
        val deviceClass = HomeLayoutDeviceClass.FOLDABLE
        val layout =
            HomeLayoutDefaults.standard(deviceClass).let { standard ->
                standard.copy(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    dock = standard.dock.copy(capacity = DOCKED_COUNT, items = docked),
                )
            }
        val key = HomeLayoutKey(viewMode = layout.viewMode, deviceClass = deviceClass)
        val state =
            LauncherShellState(
                homeLayout = layout,
                homeLayoutSet = HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout)),
                installedApps = docked.map { item -> InstalledApp(identity = item.appIdentity, label = item.label) },
            )
        val host = streamHost()
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    stream = host,
                    streamShown = isShown(),
                    onAction = { action ->
                        actions += action
                        if (action == LauncherShellAction.OpenStream) onShown()
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun streamHost(): StreamHost =
        StreamHost(
            provider =
                StreamContentProvider(
                    object : SourceRegistry {
                        override fun descriptors(): List<SourceDescriptor> = emptyList()

                        override fun source(id: SourceId): ItemSource? = null
                    },
                    context = { StreamContext(nowEpochMillis = NOW) },
                ),
            layouts =
                CachedStreamLayoutRepository(
                    object : StreamLayoutStorePort {
                        override suspend fun read(): StreamLayouts? = null

                        override suspend fun write(layouts: StreamLayouts) = Unit
                    },
                    CoroutineScope(Dispatchers.Unconfined + Job()),
                ),
            actions =
                object : StreamActionGateway {
                    override fun perform(
                        content: StreamContent,
                        action: StreamAction,
                    ): StreamActionResult = StreamActionResult.NotOffered
                },
            imageLoader = NoExpressionImageLoader,
        )

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private companion object {
        const val DOCKED_COUNT = 4
        const val STEPS = 10
        const val STEP_MILLIS = 16L
        const val PRESS_PX = 150f
        const val WAIT_MILLIS = 10_000L
        const val NOW = 1_800_000_000_000L
        const val MAX_SHARE = 0.5f
    }
}
