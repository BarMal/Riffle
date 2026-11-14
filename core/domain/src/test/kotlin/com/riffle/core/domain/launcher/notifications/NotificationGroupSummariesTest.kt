package com.riffle.core.domain.launcher.notifications

import com.riffle.core.domain.launcher.notifications.NotificationGroupSummaries.Posted
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationGroupSummariesTest {
    @Test
    fun aSummaryWithChildrenIsRedundantAndEachChildStays() {
        val posted =
            listOf(
                Posted("summary", "0|chat|g:all", isSummary = true),
                Posted("maya", "0|chat|g:all", isSummary = false),
                Posted("sam", "0|chat|g:all", isSummary = false),
            )
        assertEquals(setOf("summary"), NotificationGroupSummaries.redundant(posted))
    }

    @Test
    fun aSummaryOnItsOwnStays() {
        assertEquals(
            emptySet(),
            NotificationGroupSummaries.redundant(listOf(Posted("summary", "0|mail|g:x", isSummary = true))),
        )
    }

    @Test
    fun aSummaryOfAnotherGroupOrAnotherAppIsNotTouched() {
        val posted =
            listOf(
                Posted("a-summary", "0|a|g:x", isSummary = true),
                Posted("b-child", "0|b|g:x", isSummary = false),
                Posted("loose", null, isSummary = false),
            )
        assertEquals(emptySet(), NotificationGroupSummaries.redundant(posted))
    }
}
