package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeIconPressInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortcutTapAndLongPressWorkWithBothMotionPolicies() {
        val shortcut = shortcut(label = "Camera")

        listOf(false, true).forEach { reducedMotion ->
            val actions = mutableListOf<LauncherShellAction>()
            setContent(item = shortcut, reducedMotion = reducedMotion, actions = actions)

            composeRule.onNodeWithText(shortcut.label).performClick()

            composeRule.runOnIdle {
                assertEquals(listOf(shortcut.launchAction()), actions)
            }

            composeRule.onNodeWithText(shortcut.label).performTouchInput { longClick() }

            composeRule.onNodeWithText("Remove from home").assertExists()
        }
    }

    @Test
    fun folderTapAndLongPressWorkWithBothMotionPolicies() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = emptyList(),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )

        listOf(false, true).forEach { reducedMotion ->
            val openedFolders = mutableListOf<FolderItem>()
            setContent(item = folder, reducedMotion = reducedMotion, openedFolders = openedFolders)

            composeRule.onNodeWithText(folder.label).performClick()

            composeRule.runOnIdle {
                assertEquals(listOf(folder), openedFolders)
            }

            composeRule.onNodeWithText(folder.label).performTouchInput { longClick() }

            composeRule.onNodeWithText("Remove from home").assertExists()
        }
    }

    private fun setContent(
        item: LauncherItem,
        reducedMotion: Boolean,
        actions: MutableList<LauncherShellAction> = mutableListOf(),
        openedFolders: MutableList<FolderItem> = mutableListOf(),
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(160.dp)) {
                    WorkspaceGrid(
                        page =
                            LauncherPage(
                                id = LauncherPageId("home"),
                                grid = GridDimensions(columns = 1, rows = 1),
                                items = listOf(item),
                            ),
                        gridState =
                            HomeGridState(
                                isEditing = false,
                                pageCount = 1,
                                selectedPageIndex = 0,
                                dragSession = null,
                            ),
                        presentation =
                            HomeGridPresentation(
                                notificationGroupsByApp = emptyList(),
                                appShortcutsByApp = emptyMap(),
                                labelSettings = HomeLabelSettings.standard(),
                                reducedMotion = reducedMotion,
                                widgetViewFactory = EmptyHomeWidgetViewFactory,
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        actions =
                            HomeWorkspaceActions(
                                onFolderOpen = openedFolders::add,
                                onDragSessionChanged = {},
                                haptics = NoopLauncherHaptics,
                                onAction = actions::add,
                            ),
                    )
                }
            }
        }
    }

    private fun shortcut(label: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:camera"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.camera"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
        )
}
