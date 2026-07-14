package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandardTemplateValidationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedAppsRemainAccessibleInDrawerAcrossThemeAndWidthValidationMatrix() {
        val apps = listOf(app("Camera"), app("Calendar"))
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val homePageEditReducer = LauncherHomePageEditReducer(homeLayoutRepository = homeLayoutRepository)
        val appListActionReducer = LauncherAppListActionReducer(InstalledAppCatalog())
        val shellStateReducer = LauncherShellStateReducer()
        var displayedCase by mutableStateOf(validationCases.first())
        var showTemplateSetting by mutableStateOf(true)
        var shellState by mutableStateOf(launcherState(apps))

        fun dispatch(action: LauncherShellAction) {
            when (val route = action.launcherActivityRoute()) {
                LauncherActivityRoute.HomePageEdit -> {
                    shellState = homePageEditReducer.reduce(shellState, action)
                    shellState =
                        checkNotNull(
                            appListActionReducer.reduce(
                                shellState,
                                LauncherShellAction.AppDrawerQueryChanged(""),
                            ),
                        )
                    showTemplateSetting = false
                }

                is LauncherActivityRoute.Navigation ->
                    shellState = shellStateReducer.navigationActionSelected(shellState, route.action)

                else -> Unit
            }
        }

        composeRule.setContent {
            RiffleLauncherTheme(themeMode = displayedCase.themeMode) {
                Box(modifier = Modifier.size(width = displayedCase.widthDp.dp, height = displayedCase.heightDp.dp)) {
                    if (showTemplateSetting) {
                        HomeTemplateSetting(
                            selectedViewMode = shellState.homeLayout.viewMode,
                            selectedTemplateId = shellState.homeLayout.templateId,
                            availableViewModes = listOf(shellState.homeLayout.viewMode),
                            deviceClass = shellState.settingsLayoutDeviceClass,
                            onAction = ::dispatch,
                        )
                    } else {
                        LauncherShellContent(state = shellState, onAction = ::dispatch)
                    }
                }
            }
        }

        composeRule.onNodeWithText("Standard phone app drawer").performClick()
        composeRule.runOnIdle {
            assertEquals(LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId, shellState.homeLayout.templateId)
            assertEquals(1, shellState.homeLayout.pages.size)
            assertEquals(ShellDestination.HOME, shellState.destination)
            shellState =
                (HomeShortcutEngine().addAppToSelectedPage(shellState.homeLayout, apps.first()) as HomeShortcutResult.Updated)
                    .layout
                    .let { layout -> shellState.withHomeLayout(layout, homeLayoutRepository) }
        }

        validationCases.forEach { case ->
            composeRule.runOnIdle {
                displayedCase = case
                shellState =
                    shellState.copy(
                        launcherSettings =
                            shellState.launcherSettings.copy(
                                motion = shellState.launcherSettings.motion.copy(reducedMotion = case.reducedMotion),
                            ),
                    )
                dispatch(LauncherShellAction.OpenHome)
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Camera").assertIsDisplayed().assertHasClickAction()
            assertEquals(
                if (case.reducedMotion) HomeIconPressMotionPolicy.NONE else HomeIconPressMotionPolicy.SHRINK,
                homeIconPressMotionPolicy(shellState.launcherSettings.motion.reducedMotion),
            )

            composeRule.runOnIdle {
                dispatch(LauncherShellAction.OpenAppDrawer)
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Apps").assertIsDisplayed()
            composeRule.onNodeWithText("2 apps available").assertIsDisplayed()
            composeRule.onNodeWithText("Camera").assertIsDisplayed().assertHasClickAction()
            composeRule.onNodeWithText("Calendar").assertIsDisplayed().assertHasClickAction()
            assertEquals(case.reducedMotion, shellState.launcherSettings.motion.reducedMotion)
            assertEquals(ShellDestination.APP_DRAWER, shellState.destination)
        }
    }

    @Test
    fun standardTemplateIsUnavailableForFoldableLayoutState() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val foldableState =
            launcherState(emptyList()).copy(
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableLayoutDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            )
        val reducer = LauncherHomePageEditReducer(homeLayoutRepository = homeLayoutRepository)

        composeRule.setContent {
            HomeTemplateSetting(
                selectedViewMode = foldableState.homeLayout.viewMode,
                selectedTemplateId = foldableState.homeLayout.templateId,
                availableViewModes = listOf(foldableState.homeLayout.viewMode),
                deviceClass = foldableState.settingsLayoutDeviceClass,
                onAction = {},
            )
        }

        composeRule.onAllNodesWithText("Standard phone app drawer").assertCountEquals(0)
        composeRule.onNodeWithText("No compatible template is available").assertIsDisplayed()
        assertEquals(
            foldableState,
            reducer.reduce(
                foldableState,
                LauncherShellAction.SelectLauncherTemplate(
                    templateId = LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId,
                    mode = foldableState.homeLayout.viewMode,
                ),
            ),
        )
    }

    private fun launcherState(apps: List<InstalledApp>): LauncherShellState =
        LauncherShellState(
            firstRunStatus = FirstRunStatus.COMPLETE,
            installedApps = apps,
        )

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private data class ValidationCase(
        val widthDp: Int,
        val heightDp: Int,
        val themeMode: LauncherThemeMode,
        val reducedMotion: Boolean,
    )

    private companion object {
        val validationCases: List<ValidationCase> =
            listOf(
                ValidationCase(360, 640, LauncherThemeMode.LIGHT, reducedMotion = false),
                ValidationCase(360, 640, LauncherThemeMode.DARK, reducedMotion = true),
                ValidationCase(840, 700, LauncherThemeMode.LIGHT, reducedMotion = false),
                ValidationCase(840, 700, LauncherThemeMode.DARK, reducedMotion = true),
            )
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        private var homeLayoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = homeLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            homeLayoutSet = HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = homeLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            homeLayoutSet = layoutSet
        }
    }
}
