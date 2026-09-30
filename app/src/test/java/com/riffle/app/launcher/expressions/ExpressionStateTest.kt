package com.riffle.app.launcher.expressions

import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pure helpers every expression uses to turn a lens result into lazy-list content. */
class ExpressionStateTest {
    @Test
    fun flatResultBecomesOneUnlabelledGroup() {
        val groups = LensResult.Flat(listOf(fakeItem("a"), fakeItem("b"))).asGroups()

        assertEquals(1, groups.size)
        assertNull(groups.single().heading())
        assertEquals(listOf("a", "b"), groups.single().items.map { it.id.value })
    }

    @Test
    fun groupedResultKeepsGroupOrderAndHeadings() {
        val result =
            LensResult.Grouped(
                listOf(
                    ItemGroup("mail", "Mail", listOf(fakeItem("m1"))),
                    ItemGroup("chat", null, listOf(fakeItem("c1"))),
                ),
            )

        val groups = result.asGroups()

        assertEquals(listOf("Mail", "chat"), groups.map { it.heading() })
        assertEquals(listOf("m1", "c1"), result.allItems().map { it.id.value })
    }

    @Test
    fun duplicateItemsAreDroppedSoLazyKeysStayUnique() {
        val result = LensResult.Flat(listOf(fakeItem("a"), fakeItem("a"), fakeItem("a", sourceId = "other")))

        assertEquals(listOf("fake/a", "other/a"), result.allItems().map { it.lazyKey() })
    }

    @Test
    fun redactedTitlesFallBackToANeutralLabel() {
        assertEquals(ExpressionText.HIDDEN_TITLE, fakeItem("a", title = null).displayTitle())
        assertEquals(ExpressionText.HIDDEN_TITLE, fakeItem("a", title = "  ").displayTitle())
        assertEquals("Mail", fakeItem("a", title = "Mail").displayTitle())
    }
}
