package com.riffle.app.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.riffle.app.launcher.PAIR_WITH_PICK_TEST_TAG_PREFIX
import com.riffle.app.launcher.PAIR_WITH_SEARCH_TEST_TAG
import com.riffle.app.launcher.PairWithPickerContent
import com.riffle.app.launcher.pairWithCandidates
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app picker behind "Pair with...". Its content is tested outside a dialog window: a text field that a dialog
 * focuses keeps Robolectric's Compose from ever going idle, so the dialog itself is only covered on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PairWithPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val mail = ScreenshotFixtures.mail
    private val maps = ScreenshotFixtures.maps
    private val camera = ScreenshotFixtures.camera

    private fun tag(app: InstalledApp) = PAIR_WITH_PICK_TEST_TAG_PREFIX + app.identity.packageName.value

    @Test
    fun listsEveryOtherAppFiltersBySearchAndPicksOne() {
        var picked: InstalledApp? = null
        composeRule.setContent {
            PairWithPickerContent(
                first = ScreenshotFixtures.shortcut(mail),
                apps = ScreenshotFixtures.installedApps,
                iconLoader = SolidColorAppIconLoader(),
                onPick = { picked = it },
            )
        }

        composeRule.onNodeWithTag(tag(maps)).assertIsDisplayed()
        composeRule.onNodeWithTag(tag(mail)).assertDoesNotExist()
        composeRule.onNodeWithTag(PAIR_WITH_SEARCH_TEST_TAG).performTextInput("map")
        composeRule.onNodeWithTag(tag(camera)).assertDoesNotExist()
        composeRule.onNodeWithTag(tag(maps)).performClick()

        assertEquals(maps.identity, picked?.identity)
    }

    @Test
    fun candidatesAreEveryVisibleLaunchableAppExceptTheOneBeingPaired() {
        val apps =
            ScreenshotFixtures.installedApps +
                ScreenshotFixtures.notes.copy(enabled = false) +
                ScreenshotFixtures.clock.copy(visibility = AppVisibility.HIDDEN)

        val shown = pairWithCandidates(apps, ScreenshotFixtures.shortcut(mail), query = "")

        assertFalse(shown.any { it.identity == mail.identity })
        assertTrue(shown.any { it.identity == maps.identity })
        assertEquals(shown.map { it.label.lowercase() }, shown.map { it.label.lowercase() }.sorted())
        assertEquals(
            listOf("Maps"),
            pairWithCandidates(apps, ScreenshotFixtures.shortcut(mail), "MAP").map { it.label },
        )
    }
}
