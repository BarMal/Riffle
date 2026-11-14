package com.riffle.app.launcher.diagnostics

import android.media.session.PlaybackState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPublishModelsTest {
    @Test
    fun playbackActionsDecodeEachSetBitInOrder() {
        val bits = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO
        assertEquals(listOf("play", "skip to next", "seek to"), decodePlaybackActions(bits))
        assertEquals(emptyList<String>(), decodePlaybackActions(0L))
        assertEquals(
            listOf("pause", "set playback speed"),
            decodePlaybackActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SET_PLAYBACK_SPEED),
        )
    }

    @Test
    fun playbackStatesHaveNames() {
        assertEquals("playing", playbackStateName(PlaybackState.STATE_PLAYING))
        assertEquals("no playback state", playbackStateName(null))
        assertEquals("unknown (99)", playbackStateName(99))
    }

    @Test
    fun templateNamesAreShortened() {
        assertEquals("MessagingStyle", templateShortName("android.app.Notification\$MessagingStyle"))
        assertEquals("ProgressStyle", templateShortName("android.app.Notification\$ProgressStyle"))
        assertNull(templateShortName(null))
        assertNull(templateShortName(""))
    }

    @Test
    fun shortcutTypesListEveryKind() {
        val shape =
            ShortcutInput("Compose", " ", isStatic = true, isDynamic = false, isPinned = true, isCached = false)
                .toShape()
        assertEquals("Compose", shape.shortLabel)
        assertNull(shape.longLabel)
        assertEquals(listOf("static", "pinned"), shape.types)
    }

    @Test
    fun notificationShapeKeepsPresenceAndCountsOnly() {
        val shape = secretNotification().toShape()

        assertTrue(shape.hasTitle)
        assertTrue(shape.hasText)
        assertEquals("MessagingStyle", shape.template)
        assertEquals(2, shape.messageCount)
        assertEquals(1, shape.messagesWithPerson)
        assertEquals(1, shape.messagesWithPersonIcon)
        assertEquals(ProgressShape(max = 100, current = 40, indeterminate = false), shape.progress)
        assertEquals(listOf(ActionShape("Reply", true), ActionShape("Mute", false)), shape.actions)
    }

    @Test
    fun noProgressWhenMaxIsZeroAndNotIndeterminate() {
        assertNull(secretNotification().copy(progressMax = 0, progressIndeterminate = false).toShape().progress)
        val indeterminate = secretNotification().copy(progressMax = 0, progressIndeterminate = true).toShape()
        assertTrue(indeterminate.progress!!.indeterminate)
    }

    @Test
    fun theReportNeverContainsNotificationTextOrContactNames() {
        val report =
            AppPublishReport(
                appLabel = "Chat",
                packageName = "com.example.chat",
                shortcuts = PublishSection(),
                notifications = PublishSection(items = listOf(secretNotification().toShape())),
                media = PublishSection(problem = SectionProblem.NOTIFICATION_ACCESS_MISSING),
                widgets = PublishSection(items = listOf(WidgetShape("Chat widget", 110, 40))),
            )
        val text = report.toPlainText()

        listOf(SECRET_TITLE, SECRET_BODY, SECRET_MESSAGE, SECRET_CONTACT).forEach { secret ->
            assertFalse("report leaks \"$secret\"", text.contains(secret))
        }
        assertTrue(text.contains("Has text: yes"))
        assertTrue(text.contains("Messages: 2 (1 with a person, 1 with a person icon)"))
        assertTrue(text.contains("Action: \"Reply\", remote input yes"))
        assertTrue(text.contains("Progress: 40 of 100"))
        assertTrue(text.contains("notification access is off"))
        assertTrue(text.contains("Chat widget"))
    }

    private fun layoutViews() =
        listOf(
            LayoutViewInput(0, "android.widget.LinearLayout", null, clickable = false, visibility = 0),
            LayoutViewInput(
                depth = 1,
                className = "android.widget.TextView",
                resourceEntryName = "stops_away",
                clickable = false,
                visibility = 0,
                text = SECRET_LAYOUT_TEXT,
                textSizeSp = 13.6f,
            ),
            LayoutViewInput(
                depth = 1,
                className = "android.widget.ImageView",
                resourceEntryName = "line_icon",
                clickable = false,
                visibility = 4,
                imageWidthPx = 96,
                imageHeightPx = 48,
            ),
            LayoutViewInput(
                depth = 2,
                className = "android.widget.Button",
                resourceEntryName = null,
                clickable = true,
                visibility = 8,
                text = SECRET_BUTTON_TEXT,
                textSizeSp = 14f,
            ),
        )

    @Test
    fun aLayoutsOutlineKeepsStructureAndLengthsAndNeverTheText() {
        val shapes = layoutViews().map { it.toShape() }

        assertEquals(
            LayoutViewShape(1, "TextView", "stops_away", false, "visible", SECRET_LAYOUT_TEXT.length, 14, null),
            shapes[1],
        )
        assertEquals(
            LayoutViewShape(1, "ImageView", "line_icon", false, "invisible", null, null, "96 x 48 px"),
            shapes[2],
        )
        assertEquals(
            listOf(
                "  LinearLayout, clickable no, visible",
                "    TextView #stops_away, clickable no, visible, text ${SECRET_LAYOUT_TEXT.length} chars, 14 sp",
                "    ImageView #line_icon, clickable no, invisible, image 96 x 48 px",
                "      Button, clickable yes, gone, text ${SECRET_BUTTON_TEXT.length} chars, 14 sp",
            ),
            shapes.map { it.outlineLine() },
        )
    }

    @Test
    fun theReportListsACustomLayoutsOutlineAndStillNeverContainsItsText() {
        val custom =
            secretNotification().copy(
                hasContentView = true,
                hasBigContentView = true,
                contentLayout = layoutViews(),
                // The expanded layout could not be inflated.
                bigContentLayout = null,
            )
        val report =
            AppPublishReport(
                appLabel = "Navigator",
                packageName = "com.example.nav",
                shortcuts = PublishSection(),
                notifications = PublishSection(items = listOf(custom.toShape())),
                media = PublishSection(),
                widgets = PublishSection(),
            )
        val text = report.toPlainText()

        assertTrue(text.contains("Layout (content): 4 views"))
        assertTrue(
            text.contains("TextView #stops_away, clickable no, visible, text ${SECRET_LAYOUT_TEXT.length} chars"),
        )
        assertTrue(text.contains("Layout (big): could not be read"))
        listOf(SECRET_LAYOUT_TEXT, SECRET_BUTTON_TEXT, SECRET_TITLE, SECRET_BODY, SECRET_MESSAGE, SECRET_CONTACT)
            .forEach { secret -> assertFalse("report leaks \"$secret\"", text.contains(secret)) }
        // The screen draws the same lines as the copied report.
        val lines = custom.toShape().detailLines()
        assertFalse(lines.any { it.contains(SECRET_LAYOUT_TEXT) || it.contains(SECRET_BUTTON_TEXT) })
        // The page's promise is still made, and still true.
        assertTrue(text.contains("Notification text, message bodies and contact names are never included."))
    }

    @Test
    fun aNotificationWithoutALayoutOfItsOwnHasNoOutlineAndALongLayoutIsCapped() {
        assertFalse(secretNotification().toShape().detailLines().any { it.startsWith("Layout") })
        val long =
            secretNotification().copy(
                hasContentView = true,
                contentLayout =
                    List(
                        MAX_OUTLINE_VIEWS + 30,
                    ) { LayoutViewInput(0, "android.view.View", null, false, 0) },
            )
        assertEquals(MAX_OUTLINE_VIEWS, long.toShape().contentLayout?.size)
    }

    @Test
    fun emptySectionsAndProblemsAreStatedClearly() {
        val text =
            AppPublishReport(
                appLabel = "Quiet",
                packageName = "com.example.quiet",
                shortcuts = PublishSection(problem = SectionProblem.LAUNCHER_ROLE_MISSING),
                notifications = PublishSection(),
                media = PublishSection(),
                widgets = PublishSection(),
            ).toPlainText()
        assertTrue(text.contains("Shortcuts:\n  not available: Riffle is not the home app"))
        assertTrue(text.contains("Notifications posted by this app:\n  none"))
    }

    @Test
    fun mediaLinesShowKeysActionsAndQueueIcons() {
        val lines =
            MediaShape(
                metadata = MediaMetadataPresence(true, true, false, true, false, true),
                playbackState = "playing",
                actions = decodePlaybackActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE),
                customActions = listOf("Like"),
                queueSize = 12,
                queueItems = listOf(QueueItemShape("Track one", hasIconBitmap = false, hasIconUri = true)),
            ).detailLines()
        assertTrue(lines.contains("Actions: pause, play"))
        assertTrue(lines.contains("Custom actions: \"Like\""))
        assertTrue(lines.contains("Queue size: 12"))
        assertTrue(lines.contains("Queue 1: \"Track one\", icon bitmap no, icon URI yes"))
        assertTrue(lines.any { it.contains("album no") && it.contains("art URI yes") })
    }

    @Test
    fun appsAreFilteredAndLiveAppsComeFirst() {
        val apps = listOf(app("Zebra", "z.app"), app("Alpha", "a.app"), app("Mail", "m.app"), app("Mail", "m.app"))

        assertEquals(listOf("Mail", "Alpha", "Zebra"), orderPublishApps(apps, setOf("m.app"), "").map { it.label })
        assertEquals(listOf("Alpha"), orderPublishApps(apps, emptySet(), "ALP").map { it.label })
        assertEquals(listOf("Zebra"), orderPublishApps(apps, emptySet(), "z.app").map { it.label })
    }

    private fun app(
        label: String,
        packageName: String,
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName(packageName), AppActivityName("$packageName.Main")),
        label = label,
    )

    private fun secretNotification() =
        NotificationInput(
            category = "msg",
            isOngoing = false,
            template = "android.app.Notification\$MessagingStyle",
            title = SECRET_TITLE,
            text = SECRET_BODY,
            hasLargeIcon = true,
            hasBigPicture = false,
            hasContentView = false,
            hasBigContentView = false,
            hasHeadsUpContentView = false,
            messages =
                listOf(
                    MessageInput(SECRET_MESSAGE, SECRET_CONTACT, hasPerson = true, hasPersonIcon = true),
                    MessageInput(SECRET_MESSAGE, null, hasPerson = false, hasPersonIcon = false),
                ),
            progressMax = 100,
            progress = 40,
            progressIndeterminate = false,
            actions = listOf(ActionInput("Reply", true), ActionInput("Mute", false)),
        )

    private companion object {
        const val SECRET_TITLE = "Quarterly layoffs"
        const val SECRET_BODY = "Meet me at the hospital"
        const val SECRET_MESSAGE = "my password is hunter2"
        const val SECRET_CONTACT = "Dr Marguerite Okonkwo"
        const val SECRET_LAYOUT_TEXT = "Get off at St Thomas' Hospital"
        const val SECRET_BUTTON_TEXT = "Cancel the oncology visit"
    }
}
