package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.stream.NotificationOwnActions.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationOwnActionsTest {
    private fun action(
        index: Int,
        title: String?,
        semantic: Int? = null,
        needsInput: Boolean = false,
    ) = Candidate("provider:$index", title, semantic, needsInput)

    @Test
    fun theInlineReplyIsLeftOutAndTheOthersKeepTheNotificationsOrder() {
        val shown =
            NotificationOwnActions.select(
                listOf(
                    action(0, "Reply", needsInput = true),
                    action(1, "Mark as read"),
                    action(2, "Archive"),
                ),
            )
        assertEquals(
            listOf(NotificationOwnAction("provider:1", "Mark as read"), NotificationOwnAction("provider:2", "Archive")),
            shown,
        )
    }

    @Test
    fun aSecondReplyAndADeleteAreLeftOutBecauseTheCardAlreadyHasThem() {
        val shown =
            NotificationOwnActions.select(
                listOf(
                    action(0, "Quick reply", semantic = NotificationOwnActions.SEMANTIC_REPLY),
                    action(1, "Delete", semantic = NotificationOwnActions.SEMANTIC_DELETE),
                    action(2, "Open thread"),
                ),
            )
        assertEquals(listOf(NotificationOwnAction("provider:2", "Open thread")), shown)
    }

    @Test
    fun markAsReadArchiveThumbsMuteAndCallStay() {
        // The platform's semantic actions: 2 mark as read, 5 archive, 6 mute, 8 and 9 thumbs, 10 call.
        for (semantics in listOf(listOf(2, 5, 6), listOf(8, 9, 10))) {
            val shown =
                NotificationOwnActions.select(semantics.mapIndexed { i, s -> action(i, "Action $s", semantic = s) })
            assertEquals(semantics.map { "Action $it" }, shown.map { it.title })
        }
    }

    @Test
    fun anActionThatAsksForTextTheCardCannotGiveIsLeftOut() {
        assertEquals(
            emptyList(),
            NotificationOwnActions.select(listOf(action(0, "Answer", semantic = 2, needsInput = true))),
        )
    }

    @Test
    fun atMostThreeAreShownTheFirstThreeInOrder() {
        val shown = NotificationOwnActions.select((0..5).map { action(it, "Action $it") })
        assertEquals(3, NotificationOwnActions.MAX)
        assertEquals(listOf("Action 0", "Action 1", "Action 2"), shown.map { it.title })
        assertEquals(listOf("provider:0", "provider:1", "provider:2"), shown.map { it.id })
    }

    @Test
    fun theCapCountsWhatIsShownNotWhatWasLeftOut() {
        val shown =
            NotificationOwnActions.select(
                listOf(
                    action(0, "Reply", needsInput = true),
                    action(1, "A"),
                    action(2, "B"),
                    action(3, "C"),
                    action(4, "D"),
                ),
            )
        assertEquals(listOf("A", "B", "C"), shown.map { it.title })
    }

    @Test
    fun anActionWithNoTitleIsLeftOutAndATitleIsTrimmed() {
        val shown = NotificationOwnActions.select(listOf(action(0, null), action(1, "   "), action(2, "  Like  ")))
        assertEquals(listOf(NotificationOwnAction("provider:2", "Like")), shown)
    }

    @Test
    fun noActionsGiveNothing() {
        assertEquals(emptyList(), NotificationOwnActions.select(emptyList()))
    }
}
