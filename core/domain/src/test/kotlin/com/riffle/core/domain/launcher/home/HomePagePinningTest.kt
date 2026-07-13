package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomePagePinningTest {
    private val engine = HomePageEngine()
    private val layout = HomeLayoutDefaults.standard()

    @Test
    fun togglesGeneratedPagePinnedState() {
        val generatedPage =
            layout.selectedPage.copy(
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
            )
        val generatedLayout = layout.copy(pages = listOf(generatedPage))

        val result = engine.togglePagePinned(layout = generatedLayout, pageId = generatedPage.id)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(true, updated.layout.selectedPage.isPinned)
        assertEquals(false, generatedLayout.selectedPage.isPinned)
    }

    @Test
    fun rejectsPinningNonGeneratedPage() {
        val result = engine.togglePagePinned(layout = layout, pageId = layout.selectedPageId)

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.CANNOT_PIN_NON_GENERATED_PAGE, rejected.reason)
    }

    @Test
    fun clearsPinnedStateWhenChangingGeneratedPageToNonGeneratedType() {
        listOf(LauncherPageType.Home, LauncherPageType.AllApps).forEach { type ->
            val generatedPage =
                layout.selectedPage.copy(
                    type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                    isPinned = true,
                )
            val generatedLayout = layout.copy(pages = listOf(generatedPage))

            val result = engine.updatePageType(generatedLayout, generatedPage.id, type)

            val updated = assertIs<HomePageEditResult.Updated>(result)
            assertEquals(type, updated.layout.selectedPage.type)
            assertEquals(false, updated.layout.selectedPage.isPinned)
        }
    }
}
