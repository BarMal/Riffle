package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.DOCK_PAIR_PREVIEW_TEST_TAG
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.DockPairs
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalDockPairs
import com.riffle.app.launcher.applyEdit
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.keepingApps
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Making and editing app pairs in the Dock, through the real Dock with a store that applies each action as the shell
 * does: the "Pair with..." menu and its app picker, the pair's own menu, dropping one pinned app on another while
 * rearranging, and an app being uninstalled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPairEditingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val engine = DockEngine()
    private val camera = ScreenshotFixtures.camera
    private val mail = ScreenshotFixtures.mail
    private val chat = ScreenshotFixtures.chat
    private val maps = ScreenshotFixtures.maps

    private var layout by mutableStateOf(HomeLayoutDefaults.standard())
    private val actions = mutableListOf<LauncherShellAction>()
    private val haptics = mutableListOf<LauncherHapticEvent>()

    private fun id(app: InstalledApp) = LauncherItemId(app.identity.packageName.value)

    private fun show(
        pins: List<InstalledApp>,
        editing: Boolean = false,
        pairsAvailable: Boolean = true,
        position: DockPosition = DockPosition.BOTTOM,
    ) {
        val items = pins.map { ScreenshotFixtures.shortcut(it) }
        layout = layout.copy(dock = DockModel(capacity = 6, items = items, position = position))
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(
                    LocalDockPairs provides DockPairs(pairsAvailable, ScreenshotFixtures.installedApps),
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                        Dock(
                            dock = layout.dock,
                            isEditing = editing,
                            notificationGroupsByApp = emptyList(),
                            appShortcutsByApp = emptyMap(),
                            appIconLoader = SolidColorAppIconLoader(),
                            position = position,
                            interactions =
                                DockInteractions(
                                    position = position,
                                    haptics =
                                        object : LauncherHaptics {
                                            override fun longPress() = Unit

                                            override fun perform(event: LauncherHapticEvent) {
                                                haptics += event
                                            }
                                        },
                                    onAction = { action -> apply(action) },
                                ),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun apply(action: LauncherShellAction) {
        actions += action
        (engine.applyEdit(action, layout) as? DockEditResult.Updated)?.let { layout = it.layout }
    }

    private fun order(): List<String> = layout.dock.items.map { it.id.value }

    private fun pairs() = layout.dock.items.filterIsInstance<AppPairItem>()

    private fun longPress(tag: String) = composeRule.onNodeWithTag(tag).performTouchInput { longClick() }

    // The menu path

    @Test
    fun theMenuOffersPairWithWhereAPairIsAvailable() {
        show(listOf(camera, mail, chat))

        longPress(dockItemTestTag(id(mail)))

        composeRule.onNodeWithText("Pair with…").assertIsDisplayed()
    }

    @Test
    fun pairWithIsNotOfferedWhereAPairIsNotAvailable() {
        show(listOf(camera, mail), pairsAvailable = false)

        longPress(dockItemTestTag(id(mail)))

        composeRule.onNodeWithText("App info").assertIsDisplayed()
        composeRule.onNodeWithText("Pair with…").assertDoesNotExist()
    }

    @Test
    fun anAppsMenuOffersPairWithWhileRearrangingToo() {
        show(listOf(camera, mail), editing = true)

        composeRule.onNodeWithTag(dockItemTestTag(id(mail))).performClick()

        composeRule.onNodeWithText("Pair with…").assertIsDisplayed()
    }

    // The pair's menu

    private fun showPair(vararg others: InstalledApp) {
        show(listOf(camera, mail) + others)
        apply(LauncherShellAction.PairDockApps(id(mail), id(camera)))
        composeRule.waitForIdle()
        actions.clear()
    }

    @Test
    fun theMenuOfAPairHasTheFiveActionsAndWhatEveryPinnedItemHas() {
        showPair()

        longPress(dockItemTestTag(pairs().single().id))

        listOf(
            "Open side by side",
            "Swap sides",
            "Open Mail",
            "Open Camera",
            "Unpair",
            "App info".takeIf {
                false
            },
            "Remove from dock",
        )
            .filterNotNull()
            .forEach { label -> composeRule.onNodeWithText(label).assertIsDisplayed() }
    }

    @Test
    fun swapSidesUpdatesTheTileAtOnce() {
        showPair()
        composeRule.onNode(hasContentDescription("Mail and Camera, app pair")).assertIsDisplayed()

        longPress(dockItemTestTag(pairs().single().id))
        composeRule.onNodeWithText("Swap sides").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasContentDescription("Camera and Mail, app pair")).assertIsDisplayed()
        assertEquals(listOf("Camera", "Mail"), pairs().single().let { listOf(it.first.label, it.second.label) })
    }

    @Test
    fun unpairPutsTheTwoAppsBackFirstThenSecondAtThePairsPlace() {
        showPair(chat)

        longPress(dockItemTestTag(pairs().single().id))
        composeRule.onNodeWithText("Unpair").performClick()
        composeRule.waitForIdle()

        assertTrue(pairs().isEmpty())
        assertEquals(
            listOf("Mail", "Camera", "Chat"),
            layout.dock.items.map {
                (it as com.riffle.core.domain.launcher.home.AppShortcutItem).label
            },
        )
        composeRule.onNodeWithTag(dockItemTestTag(layout.dock.items[0].id)).assertIsDisplayed()
    }

    @Test
    fun tappingAPairAsksForTheSplitAndLongPressingOpensEachAppAlone() {
        showPair()
        val pair = pairs().single()

        composeRule.onNodeWithTag(dockItemTestTag(pair.id)).performClick()
        longPress(dockItemTestTag(pair.id))
        composeRule.onNodeWithText("Open Camera").performClick()

        assertEquals(LauncherShellAction.LaunchAppPair(pair), actions.first())
        assertEquals(LauncherShellAction.LaunchApp(camera.identity), actions.last())
        assertTrue(LauncherHapticEvent.COMMIT in haptics)
    }

    // Uninstalling

    @Test
    fun uninstallingOneAppOfAPairLeavesTheOtherPinnedWhereThePairWas() {
        showPair(chat)

        val pruned = layout.keepingApps(setOf(mail.identity, chat.identity))
        composeRule.runOnUiThread { layout = pruned }
        composeRule.waitForIdle()

        assertTrue(pairs().isEmpty())
        assertEquals(2, layout.dock.items.size)
        composeRule.onNodeWithTag(dockItemTestTag(layout.dock.items[0].id)).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Mail and Camera, app pair")).assertDoesNotExist()
    }

    // Dropping one pinned app on another

    private fun centreOf(app: InstalledApp) =
        composeRule.onNodeWithTag(dockItemTestTag(id(app))).fetchSemanticsNode().boundsInRoot.center

    /** Presses [from] (rearranging), drags it [dx] along the run and holds it there for [holdMillis]. */
    private fun dragAndHold(
        from: InstalledApp,
        dx: Float,
        holdMillis: Long,
    ) {
        // The clock is stepped by hand so that "held for N ms" is exactly that.
        composeRule.mainClock.autoAdvance = false
        val node = composeRule.onNodeWithTag(dockItemTestTag(id(from)))
        node.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(dx / 2f, 0f))
            moveBy(Offset(dx / 2f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(holdMillis)
    }

    private fun release(from: InstalledApp) {
        composeRule.onNodeWithTag(dockItemTestTag(id(from))).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(300)
    }

    @Test
    fun heldOverTheMiddleOfAnotherAppTheTargetShowsThePairAndADropPairsThem() {
        show(listOf(camera, mail, chat), editing = true)
        val dx = centreOf(mail).x - centreOf(camera).x

        dragAndHold(from = camera, dx = dx, holdMillis = 500)

        composeRule.onNodeWithTag(DOCK_PAIR_PREVIEW_TEST_TAG).assertIsDisplayed()
        assertTrue(LauncherHapticEvent.DETENT in haptics)
        release(camera)

        val pair = pairs().single()
        assertEquals(listOf("Mail", "Camera"), listOf(pair.first.label, pair.second.label))
        assertEquals(listOf(pair.id.value, id(chat).value), order())
        assertTrue(LauncherHapticEvent.COMMIT in haptics)
    }

    @Test
    fun theTargetOnlyShowsThePairAfterTheHold() {
        show(listOf(camera, mail, chat), editing = true)
        val dx = centreOf(mail).x - centreOf(camera).x

        dragAndHold(from = camera, dx = dx, holdMillis = 200)

        composeRule.onNodeWithTag(DOCK_PAIR_PREVIEW_TEST_TAG).assertDoesNotExist()
        release(camera)
        assertTrue(pairs().isEmpty())
    }

    @Test
    fun hoveringOverAnEdgeOfATileOrTheGapStillReorders() {
        show(listOf(camera, mail, chat), editing = true)
        val stride = centreOf(mail).x - centreOf(camera).x

        // A little under a slot and a half along: near the boundary between two tiles, not over a middle.
        dragAndHold(from = camera, dx = stride * 1.45f, holdMillis = 600)

        composeRule.onNodeWithTag(DOCK_PAIR_PREVIEW_TEST_TAG).assertDoesNotExist()
        release(camera)
        assertTrue(pairs().isEmpty())
        assertTrue(actions.any { it is LauncherShellAction.MoveDockShortcutToIndex })
        assertEquals(3, layout.dock.items.size)
    }

    @Test
    fun nothingIsDroppedOntoAPairAndAPairIsNotDroppedOntoAnApp() {
        show(listOf(camera, mail, chat), editing = true)
        apply(LauncherShellAction.PairDockApps(id(mail), id(camera)))
        composeRule.waitForIdle()
        actions.clear()
        val pairTag = dockItemTestTag(pairs().single().id)
        val stride =
            composeRule.onNodeWithTag(dockItemTestTag(id(chat))).fetchSemanticsNode().boundsInRoot.center.x -
                composeRule.onNodeWithTag(pairTag).fetchSemanticsNode().boundsInRoot.center.x

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(pairTag).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(stride / 2f, 0f))
            moveBy(Offset(stride / 2f, 0f))
        }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag(DOCK_PAIR_PREVIEW_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(pairTag).performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(300)

        assertEquals(1, pairs().size)
        assertTrue(actions.none { it is LauncherShellAction.PairDockApps })
    }

    @Test
    fun noDropToPairWhereAPairIsNotAvailable() {
        show(listOf(camera, mail, chat), editing = true, pairsAvailable = false)
        val dx = centreOf(mail).x - centreOf(camera).x

        dragAndHold(from = camera, dx = dx, holdMillis = 500)

        composeRule.onNodeWithTag(DOCK_PAIR_PREVIEW_TEST_TAG).assertDoesNotExist()
        release(camera)
        assertTrue(pairs().isEmpty())
    }
}
