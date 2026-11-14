package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.riffle.app.launcher.LocalStreamLayoutRepository
import com.riffle.app.launcher.LocalTasksSettings
import com.riffle.app.launcher.SettingAnchorKey
import com.riffle.app.launcher.SettingKey
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.settingsSurfaceState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every control drawn on every page of Settings belongs to a [SettingKey] (so it is searchable and search scrolls to
 * it), or is listed here as deliberately not one. The pages are drawn through the real [SettingsSurface]; a control is
 * a switch, a radio option, a slider, a stepper's button or a button row, and it is covered when it, or the row or
 * group around it, carries the key's anchor. Controls that only open another page of Settings are the page's own
 * entries in the search index, not settings, and are exempt by their text, as are the few rows below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SettingsControlsHaveKeysTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var page by mutableStateOf(SettingsPage.HOME)

    private fun controlsWithoutAnAnchor(pages: List<SettingsPage>): List<String> {
        val state =
            ScreenshotFixtures.cardsState().settingsSurfaceState(
                appVersionLabel = "Riffle 1.0 (test)",
                appBuildIdentityLabel = "Build identity for screenshots",
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalStreamLayoutRepository provides settingsStreamLayoutRepository(),
                    LocalTasksSettings provides tasksSettings(),
                ) {
                    // A new surface per page: it keeps its own current page, started from the one given.
                    key(page) { SettingsSurface(state = state, initialPage = page, onAction = {}) }
                }
            }
        }
        val missing = mutableListOf<String>()
        pages.forEach { shown ->
            composeRule.runOnUiThread { page = shown }
            composeRule.waitForIdle()

            fun visit(node: SemanticsNode) {
                if (node.isControl() && !node.hasAnchorAbove() && !node.isExempt(shown)) {
                    missing += "${node.describe()} on $shown"
                }
                node.children.forEach(::visit)
            }
            visit(composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        }
        return missing
    }

    @Test
    fun everyControlOnEveryPageHasASettingKeyOrIsListedAsExempt() {
        val missing = controlsWithoutAnAnchor(SettingsPage.entries.filter { it != SettingsPage.MAIN })

        assertTrue("Controls without a SettingKey:\n${missing.joinToString("\n")}", missing.isEmpty())
    }

    private fun SemanticsNode.isControl(): Boolean {
        val role = config.getOrNull(SemanticsProperties.Role)
        val slider =
            config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null &&
                config.getOrNull(SemanticsActions.SetProgress) != null
        val stepperButton =
            config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.startsWith("Increase ") || it.startsWith("Decrease ") } == true
        return role == Role.Switch || role == Role.RadioButton || role == Role.Button || slider || stepperButton
    }

    private fun SemanticsNode.hasAnchorAbove(): Boolean {
        var node: SemanticsNode? = this
        while (node != null) {
            if (node.config.contains(SettingAnchorKey)) return true
            node = node.parent
        }
        return false
    }

    private fun SemanticsNode.texts(): List<String> =
        (
            config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
                (config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
        ) +
            children.flatMap { it.texts() }

    private fun SemanticsNode.describe(): String = texts().joinToString(" / ").ifBlank { "(no text)" }

    /**
     * The controls that are deliberately not settings: the way back, the device tabs that choose which device class a
     * page shows, rows that only open another page (each is the page's own entry in the search index), the bulk reset
     * of the gestures, and the read-only diagnostics page.
     */
    private fun SemanticsNode.isExempt(shown: SettingsPage): Boolean {
        val text = describe()
        return shown == SettingsPage.APP_PUBLISH ||
            text in setOf("Back", "Home", "Folded", "Unfolded") ||
            text.startsWith("Open / ") ||
            text.startsWith("Reset / ")
    }
}
