package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.stream.StreamLayouts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every top-level page of Settings (and the Stream's sub-pages) through the real [SettingsSurface], drawn at compact
 * width. The sources host is absent, as in any non-app host, so pages that need it show their "not loaded" note; the
 * Stream page has a layout repository over fixed values. See [SettingsPagesExpandedScreenshotTest] for the wide ones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SettingsPagesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test fun main() = composeRule.renderSettingsPage(SettingsPage.MAIN)

    @Test fun home() = composeRule.renderSettingsPage(SettingsPage.HOME)

    @Test fun dock() = composeRule.renderSettingsPage(SettingsPage.DOCK)

    @Test fun library() = composeRule.renderSettingsPage(SettingsPage.LIBRARY)

    @Test fun stream() = composeRule.renderSettingsPage(SettingsPage.STREAM)

    @Test fun appearance() = composeRule.renderSettingsPage(SettingsPage.APPEARANCE)

    @Test fun gestures() = composeRule.renderSettingsPage(SettingsPage.GESTURES)

    @Test fun motionAndHaptics() = composeRule.renderSettingsPage(SettingsPage.MOTION)

    @Test fun privacyAndAccess() = composeRule.renderSettingsPage(SettingsPage.PRIVACY)

    @Test fun backup() = composeRule.renderSettingsPage(SettingsPage.BACKUP)

    @Test fun about() = composeRule.renderSettingsPage(SettingsPage.VERSION)

    @Test fun floatingDock() = composeRule.renderSettingsPage(SettingsPage.FLOATING_DOCK)

    @Test fun streamTasks() = composeRule.renderSettingsPage(SettingsPage.STREAM_TASKS)

    @Test fun streamAgenda() = composeRule.renderSettingsPage(SettingsPage.STREAM_AGENDA)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun streamDark() = composeRule.renderSettingsPage(SettingsPage.STREAM)

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun mainLargeFont() = composeRule.renderSettingsPage(SettingsPage.MAIN)
}

/** Renders [page] in the Settings surface and records it; shared by the compact and expanded classes. */
internal fun ComposeContentTestRule.renderSettingsPage(page: SettingsPage) {
    val state =
        ScreenshotFixtures.cardsState().settingsSurfaceState(
            appVersionLabel = "Riffle 1.0 (test)",
            appBuildIdentityLabel = "Build identity for screenshots",
        )
    setContent {
        ScreenshotBackdrop {
            CompositionLocalProvider(LocalStreamLayoutRepository provides settingsStreamLayoutRepository()) {
                SettingsSurface(state = state, initialPage = page, onAction = {})
            }
        }
    }
    captureScreen()
}

/** A Stream layout repository over nothing stored: every posture shows the default layout. */
internal fun settingsStreamLayoutRepository(): CachedStreamLayoutRepository =
    CachedStreamLayoutRepository(
        object : StreamLayoutStorePort {
            override suspend fun read() = null

            override suspend fun write(layouts: StreamLayouts) = Unit
        },
        CoroutineScope(Dispatchers.Unconfined + Job()),
    )
