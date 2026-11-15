package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.DOCK_QUICK_ACTIONS_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.QUICK_ACTIONS_ROW_TEST_TAG
import com.riffle.app.launcher.QUICK_ACTIONS_STRIP_TEST_TAG
import com.riffle.app.launcher.STREAM_COLUMN_HOST_TEST_TAG
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.quickActionChipTag
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Quick actions (ADR 0003, Amendment 3), through the real shell, Dock and Stream: an app's launcher shortcuts are not
 * cards of its stack but chips of a row; folded (a bottom Dock) the row is inside the Dock's one body, unfolded with a
 * side Dock it is a strip under the stack.
 */
internal abstract class QuickActionsHarness(
    private val deviceClass: HomeLayoutDeviceClass,
    private val dock: DockPosition?,
) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "quick-actions").apply { isDaemon = true } }
    protected val performed = CopyOnWriteArrayList<Pair<StreamContent, StreamAction>>()
    protected lateinit var stream: StreamHost

    @After
    fun stop() {
        executor.shutdownNow()
    }

    protected val chat = ScreenshotFixtures.chat
    protected val camera = ScreenshotFixtures.camera
    private val chatPackage = chat.identity.packageName.value
    protected val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    protected val cameraPage = "shortcuts/${StreamShortcutPages.appKeyOf(camera.identity)}"

    private val gateway =
        object : StreamActionGateway {
            override fun perform(
                content: StreamContent,
                action: StreamAction,
            ): StreamActionResult {
                performed += content to action
                return StreamActionResult.Performed
            }
        }

    private fun chatNotification() =
        LauncherNotification(
            key = LauncherNotificationKey("0|chat|1"),
            packageName = AppPackageName(chatPackage),
            canDismiss = true,
            title = "Sam",
            text = "Are we still on for lunch tomorrow?",
            postedAtEpochMillis = StreamFixtures.NOW - MINUTE,
        )

    private fun state(): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState(deviceClass, dock).copy(
                destination = ShellDestination.STREAM,
                appShortcutsByApp = StreamFixtures.shortcutsOf(chat) + StreamFixtures.shortcutsOf(camera),
            )
        return base.copy(
            launcherSettings =
                base.launcherSettings.copy(
                    motion = base.launcherSettings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
                ),
        )
    }

    protected fun show(landOn: String) {
        stream =
            realWiringStreamHost(
                executor = executor,
                activeNotifications = listOf(chatNotification()),
                installedApps = ScreenshotFixtures.installedApps,
                canReply = { true },
                canSnooze = { true },
                actions = gateway,
            ).also { it.pages.remember(deviceClass, landOn) }
        val shell = state()
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = shell,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    onAction = { _: LauncherShellAction -> },
                )
            }
        }
        composeRule.waitForIdle()
    }

    protected fun waitFor(tag: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    protected fun has(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    protected fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    protected fun cameraDockItem() =
        dockItemTestTag(
            composeRule.let {
                ScreenshotFixtures.homeState(deviceClass, dock).homeLayout.dock.items
                    .filterIsInstance<AppShortcutItem>().first { it.appIdentity == camera.identity }.id
            },
        )

    protected fun shoot() {
        composeRule.captureScreen()
    }

    protected companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class QuickActionsFoldedTest : QuickActionsHarness(HomeLayoutDeviceClass.PHONE, null) {
    @Test
    fun theRowIsInsideTheDocksBodyAboveItsIconsAndTheChipsLaunchThroughTheGateway() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        val dock = bounds(HOME_DOCK_TEST_TAG)
        val row = bounds(DOCK_QUICK_ACTIONS_TEST_TAG)
        assertTrue("row $row is not inside the Dock body $dock", row.top >= dock.top && row.bottom <= dock.bottom)
        assertTrue(row.left >= dock.left - 1f && row.right <= dock.right + 1f)
        val chip = bounds(quickActionChipTag("recent"))
        // A 48 dp target, with a hairline below the chips in the row (the row is a little taller than its chips).
        assertTrue(chip.height >= 48f * composeRule.density.density - 1f)
        assertTrue(row.height > chip.height)
        // The row is above the icons, in the same glass body.
        assertTrue(row.bottom <= bounds(dockItemTestTag(dockItemId())).top + 1f)
        composeRule.onNodeWithTag(quickActionChipTag("recent")).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.isNotEmpty() }
        val (content, action) = performed.first()
        assertEquals(StreamAction.Open, action)
        assertEquals("recent", (content as AppShortcutContent).shortcutId)
        // The disabled shortcut is not a chip.
        assertFalse(has(quickActionChipTag("scan")))
    }

    private fun dockItemId() =
        ScreenshotFixtures.homeState().homeLayout.dock.items.filterIsInstance<AppShortcutItem>().first().id

    @Test
    fun theLivePagesStackHoldsItsNotificationsOnlyAndNoShortcutCard() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithText("Sam").assertExists()
        assertTrue(composeRule.onAllNodesWithTag("stream-shortcut:recent").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun openingACardReplacesTheChipsWithItsActionsAndClosingBringsThemBack() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithText("Sam").performClick()
        waitFor(DOCK_CARD_ACTIONS_TEST_TAG)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { !has(DOCK_QUICK_ACTIONS_TEST_TAG) }
        assertTrue(has(STREAM_OPEN_CARD_TEST_TAG))
        composeRule.runOnIdle { stream.dock.openCards.closeByPress() }
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { !has(DOCK_CARD_ACTIONS_TEST_TAG) }
    }

    @Test
    fun changingPageSwapsTheChipsAndANoShortcutPageHasNoRow() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithTag(cameraDockItem()).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { stream.dock.currentKey == cameraPage }
        composeRule.waitForIdle()
        waitFor(quickActionChipTag("search"))
        assertTrue(has(DOCK_QUICK_ACTIONS_TEST_TAG))
    }

    @Test
    fun anAppWithNothingLiveIsOneQuietCardThatSaysNothingNew() {
        show(cameraPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithText("Nothing new").assertExists()
        composeRule.onNodeWithText("Camera").assertExists()
        composeRule.onNodeWithText("Nothing new").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.isNotEmpty() }
        assertTrue((performed.first().first as AppShortcutContent).opensApp)
    }

    @Test
    fun theRowIsALabelledGroupOfButtonsAndTheFrontCardHasTheSameActions() {
        show(cameraPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        val row = composeRule.onAllNodesWithTag(QUICK_ACTIONS_ROW_TEST_TAG).fetchSemanticsNodes().first()
        assertEquals("Quick actions for Camera", row.config.getOrNull(SemanticsProperties.PaneTitle))
        val labels =
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
                .fetchSemanticsNodes()
                .flatMap { it.config.getOrNull(SemanticsActions.CustomActions).orEmpty() }
                .map { it.label }
        assertTrue("the card lacks the quick actions: $labels", "Recent" in labels && "Search" in labels)
    }

    @Test
    fun quickActionsRowLightShot() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        shoot()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun quickActionsRowDarkShot() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        shoot()
    }

    @Test
    fun quickActionsWithACardOpenLightShot() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithText("Sam").performClick()
        waitFor(DOCK_CARD_ACTIONS_TEST_TAG)
        shoot()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun quickActionsWithACardOpenDarkShot() {
        show(chatPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        composeRule.onNodeWithText("Sam").performClick()
        waitFor(DOCK_CARD_ACTIONS_TEST_TAG)
        shoot()
    }

    @Test
    fun nothingLiveWithQuickActionsLightShot() {
        show(cameraPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        shoot()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun nothingLiveWithQuickActionsDarkShot() {
        show(cameraPage)
        waitFor(DOCK_QUICK_ACTIONS_TEST_TAG)
        shoot()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
internal class QuickActionsUnfoldedTest : QuickActionsHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    @Test
    fun theRowIsAStripUnderTheStackInsideTheColumnAndTheDockHoldsNone() {
        show(chatPage)
        waitFor(QUICK_ACTIONS_STRIP_TEST_TAG)
        assertFalse(has(DOCK_QUICK_ACTIONS_TEST_TAG))
        val strip = bounds(QUICK_ACTIONS_STRIP_TEST_TAG)
        val column = bounds(STREAM_COLUMN_HOST_TEST_TAG)
        assertTrue("strip $strip not inside column $column", strip.left >= column.left && strip.right <= column.right)
        assertTrue(strip.bottom <= column.bottom + 1f)
        // The stack's front card stands above the strip.
        val card =
            composeRule.onAllNodes(
                SemanticsMatcher("the front card") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true
                },
            ).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("card $card is not above strip $strip", card.bottom <= strip.top + 1f)
        composeRule.onNodeWithTag(quickActionChipTag("search")).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.isNotEmpty() }
        assertEquals("search", (performed.first().first as AppShortcutContent).shortcutId)
    }

    @Test
    fun theStripIsNotShownWhileACardIsOpen() {
        show(chatPage)
        waitFor(QUICK_ACTIONS_STRIP_TEST_TAG)
        composeRule.onNodeWithText("Sam").performClick()
        waitFor(STREAM_OPEN_CARD_TEST_TAG)
        assertTrue(composeRule.onAllNodesWithTag(QUICK_ACTIONS_ROW_TEST_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun unfoldedQuickActionsLightShot() {
        show(chatPage)
        waitFor(QUICK_ACTIONS_STRIP_TEST_TAG)
        shoot()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedQuickActionsDarkShot() {
        show(chatPage)
        waitFor(QUICK_ACTIONS_STRIP_TEST_TAG)
        shoot()
    }
}
