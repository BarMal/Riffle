package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.settings.LauncherThemeMode
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
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
        var displayedCase by mutableStateOf(validationCases.first())
        setContent { displayedCase }

        validationCases.forEach { case ->
            composeRule.runOnIdle { displayedCase = case }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Apps").assertIsDisplayed()
            composeRule.onNodeWithText("2 apps available").assertIsDisplayed()
            composeRule.onNodeWithText("Camera").assertIsDisplayed().assertHasClickAction()
            composeRule.onNodeWithText("Calendar").assertIsDisplayed().assertHasClickAction()
            assertEquals(case.motionPolicy, homePageSettleMotionPolicy(case.reducedMotion))
        }
    }

    private fun setContent(case: () -> ValidationCase) {
        val apps = listOf(app("Camera"), app("Calendar"))

        composeRule.setContent {
            val displayedCase = case()
            RiffleLauncherTheme(themeMode = displayedCase.themeMode) {
                Box(
                    modifier =
                        Modifier.size(
                            width = displayedCase.widthDp.dp,
                            height = displayedCase.heightDp.dp,
                        ),
                ) {
                    AppDrawer(
                        query = "",
                        profileFilter = AppDrawerProfileFilter.ALL,
                        installedApps = apps,
                        apps = apps,
                        appListContext =
                            AppListContext(
                                homeLayout = HomeLayoutDefaults.standard(),
                                overlayDock = OverlayDockSettings(),
                                notificationGroupsByApp = emptyList(),
                                appIconLoader = EmptyAppIconLoader,
                                onAction = {},
                            ),
                        onAction = {},
                    )
                }
            }
        }
    }

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
    ) {
        val motionPolicy: HomePageSettleMotionPolicy
            get() =
                if (reducedMotion) {
                    HomePageSettleMotionPolicy.ReducedShortTween
                } else {
                    HomePageSettleMotionPolicy.StandardSpring
                }
    }

    private companion object {
        val validationCases: List<ValidationCase> =
            listOf(
                ValidationCase(360, 640, LauncherThemeMode.LIGHT, reducedMotion = false),
                ValidationCase(360, 640, LauncherThemeMode.DARK, reducedMotion = true),
                ValidationCase(840, 700, LauncherThemeMode.LIGHT, reducedMotion = false),
                ValidationCase(840, 700, LauncherThemeMode.DARK, reducedMotion = true),
            )
    }
}
