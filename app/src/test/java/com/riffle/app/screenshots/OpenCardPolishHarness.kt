package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.QUICK_ACTIONS_STRIP_TEST_TAG
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.stream.QUICK_ACTIONS_RESERVE_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/** The window of the owner's recording: an unfolded inner display with the Dock on the right edge (724 x 784 dp). */
internal const val OWNER_UNFOLDED = "w724dp-h784dp-large-notlong-notround-any-xhdpi-keyshidden-nonav"

/** What one frame of an opening or closing looks like: where the front card's slot, the open card and the strip are. */
internal data class PolishFrame(
    val slot: Rect?,
    val open: Rect?,
    val strip: Rect?,
    val reserve: Rect?,
) {
    /** The stack's rectangle: the open card while it is there (it is the front card grown), else the front slot. */
    val stack: Rect? get() = open ?: slot
}

/**
 * The open card's polish, through the real shell with the test clock stepped by hand: a stack of three chats ("Sam"
 * long, "Notes", "Maps") with quick actions under (unfolded) or in the Dock (folded).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal abstract class OpenCardPolishHarness(
    protected val deviceClass: HomeLayoutDeviceClass,
    protected val dock: DockPosition?,
) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor =
        Executors.newSingleThreadExecutor {
            Thread(
                it,
                "open-card-polish",
            ).apply { isDaemon = true }
        }
    protected val probe = RecompositionProbe()
    protected val sent = mutableListOf<LauncherShellAction>()
    protected lateinit var stream: StreamHost
    protected val chat = ScreenshotFixtures.chat
    private val chatPackage = chat.identity.packageName.value
    protected val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val mailPackage = ScreenshotFixtures.mail.identity.packageName.value

    /** A page whose app has no quick actions (the fixtures give shortcuts to the chat and the camera only). */
    protected val mailPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$mailPackage:personal")
    protected val density get() = composeRule.density.density

    @After
    fun stopExecutor() {
        executor.shutdownNow()
    }

    protected val longText = (1..60).joinToString(" ") { "Line $it of a message that goes on and on." }

    private fun note(
        index: Int,
        title: String,
        text: String,
        pkg: String = chatPackage,
    ) = LauncherNotification(
        key = LauncherNotificationKey("0|$pkg|$index"),
        packageName = AppPackageName(pkg),
        canDismiss = true,
        title = title,
        text = text,
        messages = emptyList(),
        postedAtEpochMillis = StreamFixtures.NOW - index * 60_000L,
    )

    protected fun state(
        reducedMotion: Boolean = false,
        chatShortcuts: AppShortcutsByApp = StreamFixtures.shortcutsOf(chat),
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState(deviceClass, dock).copy(
                destination = ShellDestination.STREAM,
                appShortcutsByApp = chatShortcuts + StreamFixtures.shortcutsOf(ScreenshotFixtures.camera),
            )
        return base.copy(
            launcherSettings =
                base.launcherSettings.copy(
                    motion =
                        base.launcherSettings.motion.copy(
                            reducedMotionPreference =
                                if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                        ),
                ),
        )
    }

    /** Shows the Stream on the chat page: "Sam" (a long text) in front, "Notes" and "Maps" behind it. */
    @Suppress("LongParameterList")
    protected fun show(
        reducedMotion: Boolean = false,
        landOn: String = chatPage,
        sam: String = longText,
        /** The second card's text. */
        notes: String = "Short.",
        /** More chats behind the three, so that a card in the middle has neighbours on both sides. */
        more: Int = 0,
        /** The title the page shows, which the harness waits for. */
        waitFor: String = "Sam",
        chatShortcuts: AppShortcutsByApp = StreamFixtures.shortcutsOf(chat),
        /** How many of the three chats ("Sam", "Notes", "Maps") the page has: fewer cards behind the front one. */
        chats: Int = 3,
    ) {
        stream =
            realWiringStreamHost(
                executor = executor,
                activeNotifications =
                    listOf(note(1, "Sam", sam), note(2, "Notes", notes), note(3, "Maps", "Also short.")).take(chats) +
                        (1..more).map { note(3 + it, "Chat $it", "Another short message.") } +
                        listOf(
                            note(11, "Ledger", "A short one.", mailPackage),
                            note(12, "Receipts", "Another.", mailPackage),
                        ),
                installedApps = ScreenshotFixtures.installedApps,
                canReply = { true },
                canSnooze = { true },
            ).also { it.pages.remember(deviceClass, landOn) }
        val shell = state(reducedMotion, chatShortcuts)
        composeRule.setContent {
            CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                ScreenshotBackdrop {
                    LauncherShellContent(
                        state = shell,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = stream,
                        onAction = { action: LauncherShellAction -> sent += action },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(waitFor).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    protected fun has(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    protected fun boundsOf(tag: String): Rect? =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    /** Every card slot of the stack, by its card tag. */
    protected fun cardSlots(): List<Rect> =
        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("a stack card") { node ->
                val tag = node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                tag != null && tag.startsWith("stream-stack-card:")
            },
        ).fetchSemanticsNodes().map { it.boundsInRoot }

    /** The front card's slot in the collapsed stack: the lowest slot. */
    protected fun slotBounds(): Rect? = cardSlots().maxByOrNull { it.bottom }

    protected fun frame(): PolishFrame {
        val strip = composeRule.onAllNodesWithTag(QUICK_ACTIONS_STRIP_TEST_TAG).fetchSemanticsNodes().firstOrNull()
        return PolishFrame(
            slot = slotBounds(),
            open = boundsOf(STREAM_OPEN_CARD_TEST_TAG),
            strip = strip?.boundsInRoot,
            reserve = reserveBounds(),
        )
    }

    protected fun tapFront(title: String = "Sam") {
        composeRule.onNodeWithText(title).performClick()
    }

    /** The room the page keeps for the quick actions strip: always there, whether or not it has any. */
    protected fun reserveBounds(): Rect? = boundsOf(QUICK_ACTIONS_RESERVE_TEST_TAG)

    /** The front card of the stack at rest, whichever page: the lowest card slot. */
    protected fun settledSlot(): Rect = checkNotNull(slotBounds()) { "no stack card" }

    /** Steps the clock [frames] frames, sampling after each; the first sample is the frame before any step. */
    protected fun record(frames: Int): List<PolishFrame> {
        val seen = mutableListOf(frame())
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            seen += frame()
        }
        return seen
    }

    protected fun Rect.fmt() = "[${left.toInt()},${top.toInt()} ${right.toInt()},${bottom.toInt()}]"
}

/** [count] shortcuts of [app] with long labels, so that their chips are wide. */
internal fun longShortcutsOf(
    app: com.riffle.core.domain.launcher.apps.InstalledApp,
    count: Int,
): AppShortcutsByApp =
    mapOf(
        app.identity to
            (0 until count).map { i ->
                AppShortcut(
                    AppShortcutId("long$i"),
                    app.identity,
                    LongLabels[i % LongLabels.size],
                    null,
                    rank = i,
                )
            },
    )

private val LongLabels =
    listOf(
        "Share with a person",
        "Record a video message",
        "Scan a long document",
        "Open a new group chat",
        "Start a voice call",
    )
