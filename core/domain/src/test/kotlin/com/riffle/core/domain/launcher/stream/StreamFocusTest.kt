package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamFocusTest {
    private val all = StreamSection.entries

    private fun ready(vararg keys: String): StreamSectionResult =
        StreamSectionResult.Ready(keys.map { key -> StreamGroup(key, key, listOf(note(key))) })

    private fun note(key: String) =
        NotificationContent(
            id = StreamContentId("n:$key"),
            app = StreamAppRef(key.substringBefore(':'), key.substringAfter(':')),
            appLabel = null,
            title = null,
            text = null,
            icon = null,
            image = null,
            postedAtEpochMillis = 1,
            category = null,
            priority = null,
            target = com.riffle.core.domain.launcher.workspace.ItemTarget.None,
            actions = emptySet(),
            privacy = StreamPrivacy.VISIBLE,
        )

    @Test
    fun nowLandsOnTheNowSection() {
        assertEquals(
            StreamFocusResolution(StreamSection.NOW, null),
            StreamFocus.resolve(StreamFocusTarget.Now, all, null),
        )
    }

    @Test
    fun anAppStackLandsOnItsGroupInTheNotificationsSection() {
        val resolved =
            StreamFocus.resolve(
                StreamFocusTarget.AppStack("chat:personal"),
                all,
                ready("mail:personal", "chat:personal"),
            )
        assertEquals(StreamFocusResolution(StreamSection.NOTIFICATIONS, "chat:personal"), resolved)
    }

    @Test
    fun aGroupThatWentAwayLandsOnTheSectionAlone() {
        val resolved = StreamFocus.resolve(StreamFocusTarget.AppStack("gone:personal"), all, ready("mail:personal"))
        assertEquals(StreamFocusResolution(StreamSection.NOTIFICATIONS, null), resolved)
        assertEquals(
            StreamFocusResolution(StreamSection.NOTIFICATIONS, null),
            StreamFocus.resolve(StreamFocusTarget.AppStack("chat:personal"), all, StreamSectionResult.Loading),
        )
    }

    @Test
    fun aSectionTheLayoutHidesResolvesToNothing() {
        assertNull(StreamFocus.resolve(StreamFocusTarget.Now, all - StreamSection.NOW, null))
        assertNull(
            StreamFocus.resolve(StreamFocusTarget.AppStack("a:b"), all - StreamSection.NOTIFICATIONS, ready("a:b")),
        )
    }
}
