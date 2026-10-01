package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuEffect
import com.riffle.core.domain.launcher.workspace.menu.WorkspacePageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePreviewControllerTest {
    private class FakePreference(var stored: Boolean = false) : WorkspacePreviewPreference {
        var writes = 0

        override fun isEnabled(): Boolean = stored

        override fun setEnabled(enabled: Boolean) {
            stored = enabled
            writes++
        }
    }

    private val preference = FakePreference()
    private val flagChanges = mutableListOf<Boolean>()
    private val controller = WorkspacePreviewController(preference) { flagChanges += it }

    @Test
    fun startsOffAndClosedWithTheFlagOff() {
        assertFalse(controller.enabled.value)
        assertFalse(controller.isOpen.value)
        assertEquals(listOf(false), flagChanges)
    }

    @Test
    fun startsFromThePersistedSetting() {
        val enabled = WorkspacePreviewController(FakePreference(stored = true)) { flagChanges += it }

        assertTrue(enabled.enabled.value)
        assertEquals(false, enabled.isOpen.value)
        assertEquals(true, flagChanges.last())
    }

    @Test
    fun turningItOnPersistsAndSyncsTheMenuFlagBeforeObserversChange() {
        controller.setEnabled(true)

        assertTrue(controller.enabled.value)
        assertTrue(preference.stored)
        assertEquals(listOf(false, true), flagChanges)
    }

    @Test
    fun opensOnlyWhileEnabled() {
        controller.open()
        assertFalse(controller.isOpen.value)

        controller.setEnabled(true)
        controller.open()
        assertTrue(controller.isOpen.value)
    }

    @Test
    fun turningItOffClosesThePreviewAndTheEditor() {
        controller.setEnabled(true)
        controller.open()
        controller.onEffect(WorkspaceMenuEffect.EditWorkspace(WorkspaceId("w")))

        controller.setEnabled(false)

        assertFalse(controller.isOpen.value)
        assertNull(controller.editing.value)
        assertFalse(preference.stored)
    }

    @Test
    fun closeIsAlwaysAvailableAndClearsPendingState() {
        controller.setEnabled(true)
        controller.onEffect(WorkspaceMenuEffect.OpenFinderPage(ContainerId("finder")))

        controller.close()

        assertFalse(controller.isOpen.value)
        assertNull(controller.navigation.value)
        assertTrue(controller.enabled.value)
    }

    @Test
    fun jumpAndFinderEffectsOpenThePreviewWithANavigationToConsume() {
        controller.setEnabled(true)

        controller.onEffect(WorkspaceMenuEffect.NavigateToPage(WorkspacePageKey.Page(ContainerId("p2"))))
        assertTrue(controller.isOpen.value)
        assertEquals(PreviewNavigation.ToPage(ContainerId("p2")), controller.navigation.value)

        controller.navigationConsumed(PreviewNavigation.ToPage(ContainerId("p2")))
        assertNull(controller.navigation.value)

        controller.onEffect(WorkspaceMenuEffect.OpenFinderPage(ContainerId("finder")))
        assertEquals(PreviewNavigation.ToPage(ContainerId("finder")), controller.navigation.value)
    }

    @Test
    fun aStaleConsumeDoesNotClearANewerNavigation() {
        controller.setEnabled(true)
        controller.onEffect(WorkspaceMenuEffect.OpenFinderPage(ContainerId("finder")))

        controller.navigationConsumed(PreviewNavigation.ToPage(ContainerId("old")))

        assertEquals(PreviewNavigation.ToPage(ContainerId("finder")), controller.navigation.value)
    }

    @Test
    fun editOpensThePreviewOnThatWorkspaceAndCanBeClosed() {
        controller.setEnabled(true)

        controller.onEffect(WorkspaceMenuEffect.EditWorkspace(WorkspaceId("w")))
        assertEquals(WorkspaceId("w"), controller.editing.value)
        assertTrue(controller.isOpen.value)

        controller.closeEditor()
        assertNull(controller.editing.value)
        assertTrue(controller.isOpen.value)
    }

    @Test
    fun effectsAreIgnoredWhileTheSettingIsOff() {
        controller.onEffect(WorkspaceMenuEffect.EditWorkspace(WorkspaceId("w")))
        controller.onEffect(WorkspaceMenuEffect.OpenFinderPage(ContainerId("finder")))

        assertFalse(controller.isOpen.value)
        assertNull(controller.editing.value)
        assertNull(controller.navigation.value)
    }

    @Test
    fun settingTheSameValueWritesNothing() {
        controller.setEnabled(false)

        assertEquals(0, preference.writes)
    }

    @Test
    fun pageIndexFindsContainersByIdOrMinusOne() {
        val workspace = Workspace(WorkspaceId("w"), "W", listOf(boundPage("a"), boundPage("b")))

        assertEquals(1, workspace.pageIndexOf(ContainerId("b")))
        assertEquals(-1, workspace.pageIndexOf(ContainerId("zzz")))
    }

    @Test
    fun homeGridPagesAreRecognisedButOtherSourcesAreNot() {
        val homeLens = Lens(sources = listOf(WorkspaceSourceIds.HOME_GRID))
        val appsLens = Lens(sources = listOf(SourceId("apps.all")))

        assertTrue(boundPage("h", homeLens).referencesHomeGrid())
        assertFalse(boundPage("a", appsLens).referencesHomeGrid())
        assertTrue(PageSetContainer(ContainerId("s"), LensBinding(homeLens, ExpressionKind.LIST)).referencesHomeGrid())
        assertFalse(widgetGrid(emptyList()).referencesHomeGrid())
        assertTrue(widgetGrid(listOf(homeLens)).referencesHomeGrid())
        assertFalse(widgetGrid(listOf(homeLens, appsLens)).referencesHomeGrid())
    }

    private fun boundPage(
        id: String,
        lens: Lens = Lens(sources = listOf(SourceId("apps.all"))),
    ) = PageContainer(ContainerId(id), PageContent.Bound(LensBinding(lens, ExpressionKind.LIST)))

    private fun widgetGrid(lenses: List<Lens>) =
        PageContainer(
            ContainerId("grid"),
            PageContent.WidgetGrid(
                columns = 4,
                rows = 4,
                placements =
                    lenses.mapIndexed { index, lens ->
                        WidgetPlacement(
                            WidgetContainer(
                                ContainerId("w$index"),
                                WidgetSpan(1, 1),
                                LensBinding(lens, ExpressionKind.LIST),
                            ),
                            column = index,
                            row = 0,
                        )
                    },
            ),
        )
}
