package com.riffle.app.screenshots

import android.app.Application
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.CHANGE_ICON_CHOICE_TEST_TAG_PREFIX
import com.riffle.app.launcher.CHANGE_ICON_NAME_TEST_TAG
import com.riffle.app.launcher.CHANGE_ICON_RESET_TEST_TAG
import com.riffle.app.launcher.CHANGE_ICON_SEARCH_TEST_TAG
import com.riffle.app.launcher.CHANGE_ICON_TEST_TAG
import com.riffle.app.launcher.ChangeIconSurface
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.ChangeIconTarget
import com.riffle.app.launcher.icons.FakeIconPackSource
import com.riffle.app.launcher.icons.IconPackStore
import com.riffle.app.launcher.icons.LocalAppIconPipeline
import com.riffle.app.launcher.icons.letterLegacyIcon
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "Change icon" sheet: the pack's icons in a searchable grid, rename, and reset to default. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class ChangeIconSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app = ScreenshotFixtures.notes
    private val actions = mutableListOf<LauncherShellAction>()
    private val names = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa")
    private val packName = "com.example.iconpack.sample"

    private fun show(style: IconStyleSettings = IconStyleSettings(iconPack = packName)) {
        val source =
            FakeIconPackSource(
                mapOf(
                    packName to
                        FakeIconPackSource.FakePack(
                            label = "Example pack",
                            drawableXml = drawableXml(),
                            drawables =
                                names.mapIndexed { index, name ->
                                    name to letterLegacyIcon(PALETTE[index % PALETTE.size], name.take(1).uppercase())
                                }.toMap(),
                        ),
                    "com.example.iconpack.other" to FakeIconPackSource.FakePack(label = "Other pack"),
                ),
            )
        val pipeline =
            AppIconPipeline(
                packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager,
                packs = IconPackStore(source),
                sdkInt = ScreenshotDevices.SDK,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                isMainThread = { false },
            )
        pipeline.packs.watch(setOf(packName), packName)
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalAppIconPipeline provides pipeline) {
                    ChangeIconSurface(
                        target = ChangeIconTarget(app.identity, app.label),
                        style = style,
                        onAction = { actions += it },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.hasChoice("alpha") }
    }

    private fun drawableXml(): String =
        "<resources>" + names.joinToString("") { """<item drawable="$it"/>""" } + "</resources>"

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.hasChoice(name: String): Boolean =
        runCatching { onNodeWithTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + name).assertIsDisplayed() }.isSuccess

    @Test
    fun theSheetListsThePacksIcons() {
        show()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHANGE_ICON_TEST_TAG).captureRoboImage()
    }

    @Test
    fun choosingAnIconStoresAReferenceToItAndTheResetNeedsAChoiceToBeEnabled() {
        show()
        composeRule.onNodeWithTag(CHANGE_ICON_RESET_TEST_TAG).assertIsNotEnabled()

        composeRule.onNodeWithTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + "gamma").performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SetAppIconOverride(app.identity, IconPackDrawableRef(packName, "gamma")),
            ),
            actions,
        )
    }

    @Test
    fun searchingNarrowsTheGrid() {
        show()

        composeRule.onNodeWithTag(CHANGE_ICON_SEARCH_TEST_TAG).performTextInput("ta")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + "beta").assertIsDisplayed()
        composeRule.onNodeWithTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + "zeta").assertIsDisplayed()
        composeRule.onNodeWithTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + "alpha").assertDoesNotExist()
    }

    @Test
    fun resetToDefaultIsOfferedOnceTheAppHasAChosenIcon() {
        show(
            IconStyleSettings(iconPack = packName)
                .withIconOverride(app.identity, IconPackDrawableRef(packName, "beta")),
        )

        composeRule.onNodeWithTag(CHANGE_ICON_RESET_TEST_TAG).assertIsEnabled().performClick()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SetAppIconOverride(app.identity, null)),
            actions,
        )
    }

    @Test
    fun renamingStoresACustomNameAndAnUnchangedNameStoresNone() {
        show()

        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(CHANGE_ICON_NAME_TEST_TAG).performTextReplacement("Jot")
        composeRule.onNodeWithText("Rename").performClick()

        assertEquals(
            listOf<LauncherShellAction>(
                LauncherShellAction.SetAppLabelOverride(app.identity, null),
                LauncherShellAction.SetAppLabelOverride(app.identity, "Jot"),
            ),
            actions,
        )
    }

    private companion object {
        val PALETTE =
            listOf(
                Color.rgb(26, 115, 232),
                Color.rgb(217, 48, 37),
                Color.rgb(24, 128, 56),
                Color.rgb(224, 150, 0),
                Color.rgb(147, 52, 230),
            )
    }
}
