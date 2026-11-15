package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.QUICK_ACTIONS_ROW_TEST_TAG
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.SEARCH_RESULT_ICON_GRID_TEST_TAG
import com.riffle.app.launcher.SEARCH_RESULT_LIST_TEST_TAG
import com.riffle.app.launcher.SEARCH_WEB_PILL_TEST_TAG
import com.riffle.app.launcher.quickActionChipTag
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.search.SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG
import com.riffle.app.launcher.search.SEARCH_STACK_TEST_TAG
import com.riffle.app.launcher.search.searchStackCardTag
import com.riffle.app.launcher.search.searchStackMoreMarkTag
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.SearchResultPresentation
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
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
 * Search as a card stack (the Dock's search), through the real shell content with only the platform services faked:
 * the best match is the front card with its quick actions and newest notification, the next matches are strips behind
 * it, a tap on a strip brings it forward only when it has more to show, Enter opens the front result, the stack stands
 * in one place whatever the query, and "Search web" is the last card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
@Suppress("LargeClass")
class SearchStackBehaviorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "search-stack").apply { isDaemon = true } }
    private val sent = CopyOnWriteArrayList<LauncherShellAction>()
    private val performed = CopyOnWriteArrayList<Pair<StreamContent, StreamAction>>()

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private val maps = ScreenshotFixtures.maps
    private val notes = ScreenshotFixtures.notes
    private val clock = ScreenshotFixtures.clock
    private val weather = ScreenshotFixtures.weather
    private val chat = ScreenshotFixtures.chat
    private val probe = RecompositionProbe()

    private var shell by mutableStateOf(searching("", emptyList()))

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

    private fun searching(
        query: String,
        results: List<InstalledApp>,
        presentation: SearchResultPresentation? = null,
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState().copy(
                destination = ShellDestination.SEARCH,
                searchQuery = query,
                searchResults = results,
                appShortcutsByApp = StreamFixtures.shortcutsOf(maps),
            )
        val settings = base.launcherSettings
        return base.copy(
            launcherSettings =
                settings.copy(
                    search = presentation?.let { settings.search.copy(resultPresentation = it) } ?: settings.search,
                    motion = settings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
                ),
        )
    }

    private fun host(withChatNotification: Boolean = false): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications =
                if (withChatNotification) {
                    listOf(
                        LauncherNotification(
                            key = LauncherNotificationKey("0|chat|1"),
                            packageName = AppPackageName(chat.identity.packageName.value),
                            title = "Sam",
                            text = "Are we still on for lunch tomorrow?",
                            postedAtEpochMillis = StreamFixtures.NOW - MINUTE,
                        ),
                    )
                } else {
                    emptyList()
                },
            installedApps = ScreenshotFixtures.installedApps,
            actions = gateway,
        )

    private fun show(
        state: LauncherShellState,
        stream: StreamHost = host(),
        typing: Boolean = false,
    ) {
        shell = state
        composeRule.setContent {
            CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                ScreenshotBackdrop {
                    LauncherShellContent(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = stream,
                        onAction = { action ->
                            sent += action
                            if (action is LauncherShellAction.SearchQueryChanged && typing) {
                                shell = shell.copy(searchQuery = action.query, searchResults = matching(action.query))
                            }
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun matching(query: String): List<InstalledApp> =
        if (query.isBlank()) {
            emptyList()
        } else {
            ScreenshotFixtures.installedApps.filter { it.label.contains(query, ignoreCase = true) }
        }

    private fun card(app: InstalledApp): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(searchStackCardTag("app:${app.identity.stableKey()}"), useUnmergedTree = true)

    private fun cardNodes() =
        composeRule.onAllNodes(
            SemanticsMatcher("a search stack card") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("search-stack-card:") == true
            },
            useUnmergedTree = true,
        )

    /** The card in front: the stack's slot that is first in the traversal. */
    private fun frontNode() =
        cardNodes().fetchSemanticsNodes().first { it.config.getOrNull(SemanticsProperties.TraversalIndex) == 0f }

    private fun bounds(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun clickHeader(card: SemanticsNodeInteraction) =
        card.performTouchInput { click(Offset(width * 0.5f, 26f * density)) }

    private fun sentLaunches() = sent.filterIsInstance<LauncherShellAction.LaunchApp>().map { it.identity }

    // The best match is the front card

    @Test
    fun theCardStackIsTheDefaultAndTheBestMatchIsTheFrontCardWithItsQuickActions() {
        show(searching("m", listOf(maps, notes, clock)))

        composeRule.onNodeWithTag(SEARCH_STACK_TEST_TAG).assertIsDisplayed()
        assertEquals(
            searchStackCardTag("app:${maps.identity.stableKey()}"),
            frontNode().config[SemanticsProperties.TestTag],
        )
        // The front card shows the app's quick actions (its enabled launcher shortcuts) as the Stream's row.
        composeRule.onNodeWithTag(QUICK_ACTIONS_ROW_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(quickActionChipTag("recent"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(quickActionChipTag("search"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(quickActionChipTag("scan"), useUnmergedTree = true).assertCountEquals(0)
        // The next matches are strips behind it in rank order, then "Search web".
        val order =
            cardNodes().fetchSemanticsNodes().sortedBy { it.config[SemanticsProperties.TraversalIndex] }
                .map { it.config[SemanticsProperties.TestTag] }
        val expected = listOf(maps, notes, clock).map { searchStackCardTag("app:${it.identity.stableKey()}") }
        assertEquals(expected + searchStackCardTag("web"), order)
    }

    @Test
    fun theFrontCardSaysTheNewestNotificationOfItsApp() {
        show(searching("c", listOf(chat, notes)), stream = host(withChatNotification = true))
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodes(hasTestTag(SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes().any { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sam") } == true
                }
        }
        composeRule.onNodeWithText(
            "Sam: Are we still on for lunch tomorrow?",
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun withoutTheStreamACardSaysHowManyNotificationsItsAppHas() {
        show(searching("c", listOf(chat, notes)), stream = host(withChatNotification = false))

        composeRule.onNodeWithText("1 notification", useUnmergedTree = true).assertIsDisplayed()
    }

    // The tap rule

    @Test
    fun aTapOnAStripWithQuickActionsBringsItToTheFrontAndDoesNotOpenTheApp() {
        show(searching("m", listOf(notes, maps, clock)))
        // A strip is one button: the chips under it are not reachable (not in the tree assistive technology reads).
        composeRule.onAllNodesWithTag(quickActionChipTag("recent")).assertCountEquals(0)

        card(maps).performClick()
        composeRule.waitForIdle()

        assertTrue("the app was opened: $sent", sentLaunches().isEmpty())
        assertEquals(
            searchStackCardTag("app:${maps.identity.stableKey()}"),
            frontNode().config[SemanticsProperties.TestTag],
        )
        composeRule.onNodeWithTag(quickActionChipTag("recent"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aTapOnAStripWithNothingMoreThanANameOpensTheApp() {
        show(searching("m", listOf(maps, notes, clock)))

        card(notes).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(notes.identity), sentLaunches())
    }

    @Test
    fun aTapOnTheFrontCardOpensIt() {
        show(searching("n", listOf(notes, clock)))

        clickHeader(card(notes))
        composeRule.waitForIdle()

        assertEquals(listOf(notes.identity), sentLaunches())
    }

    @Test
    fun aTapOnAQuickActionLaunchesItThroughTheOneActionGatewayAndNotTheApp() {
        show(searching("m", listOf(maps, notes)))

        composeRule.onNodeWithTag(quickActionChipTag("recent"), useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { performed.isNotEmpty() }

        val (content, action) = performed.single()
        assertEquals("recent", (content as AppShortcutContent).shortcutId)
        assertEquals(maps.identity, content.appIdentity)
        assertEquals(StreamAction.Open, action)
        assertTrue("the shell was not asked to launch the app: $sent", sentLaunches().isEmpty())
    }

    // Enter and the IME's search action

    @Test
    fun enterOpensTheFrontResult() {
        show(searching("m", listOf(notes, maps, clock)))

        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performImeAction()
        composeRule.waitForIdle()

        assertEquals(listOf(notes.identity), sentLaunches())
    }

    @Test
    fun enterOpensTheCardThatWasBroughtToTheFrontNotTheFirstMatch() {
        show(searching("m", listOf(notes, maps, clock)))
        card(maps).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performImeAction()
        composeRule.waitForIdle()

        assertEquals(listOf(maps.identity), sentLaunches())
    }

    @Test
    fun withNothingFoundSearchWebIsTheOnlyCardAndEnterSearchesTheWeb() {
        show(searching("zzz", emptyList()))

        assertEquals(1, cardNodes().fetchSemanticsNodes().size)
        composeRule.onNodeWithText("No results found for \"zzz\"", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(SEARCH_WEB_PILL_TEST_TAG, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performImeAction()
        composeRule.waitForIdle()
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SearchWeb("zzz")),
            sent.filterIsInstance<LauncherShellAction.SearchWeb>(),
        )
    }

    @Test
    fun searchWebIsTheLastCardAndItsStripOpensTheWebSearchAtOnce() {
        show(searching("m", listOf(notes, clock)))

        composeRule.onNodeWithTag(searchStackCardTag("web"), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SearchWeb("m")),
            sent.filterIsInstance<LauncherShellAction.SearchWeb>(),
        )
    }

    // Fixed geometry

    @Test
    fun theStackAndTheFrontCardStandInTheSamePlaceWhateverTheQuery() {
        show(searching("", emptyList()), typing = true)
        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performTextInput("m")
        composeRule.waitForIdle()
        val stack = bounds(SEARCH_STACK_TEST_TAG)
        val front = frontNode().boundsInRoot

        // More results, a query with one app, a query with none: the content changes and nothing else.
        for (typed in listOf("a", "p", "z", "z")) {
            composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performTextInput(typed)
            composeRule.waitForIdle()
            assertEquals("the stack moved after '${shell.searchQuery}'", stack, bounds(SEARCH_STACK_TEST_TAG))
            assertEquals("the front card changed place after '${shell.searchQuery}'", front, frontNode().boundsInRoot)
        }
        assertEquals("only Search web is left", 1, cardNodes().fetchSemanticsNodes().size)
    }

    @Test
    fun theFrontCardIsTheSameSizeWhetherItHasQuickActionsANotificationOrNothingMore() {
        show(searching("x", listOf(maps)))
        val sizes =
            listOf(maps, notes, chat).map { app ->
                shell = searching("x", listOf(app))
                composeRule.waitForIdle()
                frontNode().boundsInRoot
            }
        assertEquals("all three at one place and size: $sizes", 1, sizes.toSet().size)
    }

    // Cap and paging

    @Test
    fun onlyTheStacksOwnStripsShowAndTheRestAreReachedByStepping() {
        val many = ScreenshotFixtures.installedApps.take(8)
        show(searching("x", many))

        // Folded: the front card and three strips, and the one on its way in; never all nine.
        assertTrue(
            "${cardNodes().fetchSemanticsNodes().size} cards composed",
            cardNodes().fetchSemanticsNodes().size <= 5,
        )
        val first = frontNode().config[SemanticsProperties.TestTag]
        repeat(4) { stepNext() }
        val moved = frontNode().config[SemanticsProperties.TestTag]
        assertFalse("the stack did not move", first == moved)
        assertEquals(
            searchStackCardTag("app:${many[4].identity.stableKey()}"),
            moved,
        )
        repeat(5) { stepNext() }
        assertEquals(searchStackCardTag("web"), frontNode().config[SemanticsProperties.TestTag])
    }

    private fun stepNext() {
        val stack =
            composeRule.onAllNodes(
                SemanticsMatcher("the stack") {
                    it.config.getOrNull(SemanticsActions.CustomActions)?.any {
                            action ->
                        action.label == "Next card"
                    } == true
                },
                useUnmergedTree = true,
            )
        val action =
            stack.fetchSemanticsNodes().first().config[SemanticsActions.CustomActions].first { it.label == "Next card" }
        composeRule.runOnUiThread { action.action() }
        composeRule.waitForIdle()
    }

    // Motion is in layout and draw

    @Test
    fun draggingTheStackRecomposesNoCard() {
        show(searching("x", ScreenshotFixtures.installedApps.take(6)))
        val stack = composeRule.onNodeWithTag(SEARCH_STACK_TEST_TAG)
        stack.performTouchInput {
            down(Offset(width * 0.5f, height * 0.3f))
            moveBy(Offset(0f, 12f * density))
        }
        composeRule.waitForIdle()
        // The cards standing now (a card further back may join the composed range as the stack moves).
        val standing = (0..3)
        val beforeEach = standing.map { probe.count("search-stack-slot:$it") }

        stack.performTouchInput {
            // Still short of half a card, so no card becomes the front one: only layout and draw run.
            moveBy(Offset(0f, 3f * density))
            moveBy(Offset(0f, 3f * density))
            moveBy(Offset(0f, 3f * density))
        }
        composeRule.waitForIdle()
        stack.performTouchInput { up() }

        val duringEach = standing.map { probe.count("search-stack-slot:$it") }
        assertEquals("a drag recomposed the cards", beforeEach, duringEach)
    }

    // The setting

    @Test
    fun aStoredIconsOrListChoiceStillShowsTheEarlierPresentation() {
        show(searching("m", listOf(maps, notes), presentation = SearchResultPresentation.ICONS))
        composeRule.onAllNodesWithTag(SEARCH_RESULT_ICON_GRID_TEST_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SEARCH_STACK_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun aStoredListChoiceStillShowsTheList() {
        show(searching("m", listOf(maps, notes), presentation = SearchResultPresentation.LIST))
        composeRule.onAllNodesWithTag(SEARCH_RESULT_LIST_TEST_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SEARCH_STACK_TEST_TAG).assertCountEquals(0)
    }

    // Accessibility

    @Test
    fun theFrontCardIsOneButtonNamedForTheAppWhoseQuickActionsAreCustomActions() {
        show(searching("m", listOf(maps, notes)))

        val front = frontNode().config
        assertEquals("Maps", front[SemanticsProperties.ContentDescription].single())
        assertTrue(front.contains(SemanticsActions.OnClick))
        assertEquals(androidx.compose.ui.semantics.Role.Button, front[SemanticsProperties.Role])
        assertEquals(listOf("Recent", "Search"), front[SemanticsActions.CustomActions].map { it.label })
    }

    @Test
    fun aStripIsAButtonWithAStateDescriptionOnlyWhenItHasMoreToShow() {
        show(searching("m", listOf(clock, maps, notes)))

        val strips = cardNodes().fetchSemanticsNodes().associateBy { it.config[SemanticsProperties.TestTag] }
        val mapsStrip = strips.getValue(searchStackCardTag("app:${maps.identity.stableKey()}")).config
        val notesStrip = strips.getValue(searchStackCardTag("app:${notes.identity.stableKey()}")).config
        assertEquals(androidx.compose.ui.semantics.Role.Button, mapsStrip[SemanticsProperties.Role])
        assertEquals("Maps", mapsStrip[SemanticsProperties.ContentDescription].single())
        assertTrue(mapsStrip.getOrNull(SemanticsProperties.StateDescription) != null)
        assertEquals(null, notesStrip.getOrNull(SemanticsProperties.StateDescription))
        assertTrue(mapsStrip.contains(SemanticsActions.OnClick))
        // The quiet mark is there for the strip that has more, and only there.
        composeRule.onNodeWithTag(searchStackMoreMarkTag("app:${maps.identity.stableKey()}"), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(
            searchStackMoreMarkTag("app:${notes.identity.stableKey()}"),
            useUnmergedTree = true,
        )
            .assertCountEquals(0)
    }

    @Test
    fun aStripsAccessibilityClickFollowsTheSameTapRule() {
        show(searching("m", listOf(clock, maps, notes)))

        card(notes).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(notes.identity), sentLaunches())

        card(maps).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals("a strip with more only comes forward", listOf(notes.identity), sentLaunches())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}

private fun com.riffle.core.domain.launcher.apps.AppIdentity.stableKey(): String =
    "${packageName.value}/${activityName.value}/${profile.id.value}"
