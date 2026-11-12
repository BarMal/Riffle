package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.testing.InMemoryWorkspaceRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** WS10 11.4: a home gesture can be bound to "Workspace menu"; it is honoured only while the menu feature is on. */
class WorkspaceMenuGestureTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private val set =
        WorkspaceSet(
            mapOf(
                phone to
                    LayoutWorkspaces.single(
                        Workspace(
                            WorkspaceId("a"),
                            "a",
                            listOf(
                                PageContainer(
                                    ContainerId("a-page"),
                                    PageContent.Bound(
                                        LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST),
                                    ),
                                ),
                            ),
                        ),
                    ),
            ),
        )

    private fun controller(enabled: Boolean) =
        WorkspaceMenuController(
            repository = InMemoryWorkspaceRepository(set),
            deviceClass = { phone },
            isEnabled = { enabled },
        )

    private fun handler(controller: WorkspaceMenuController) =
        LauncherActivityActionHandler(
            requestDefaultHome = {},
            navigate = {},
            editHomePage = {},
            editHomeShortcut = {},
            editDock = {},
            openWorkspaceMenu = { controller.dispatch(WorkspaceMenuAction.Open) },
        )

    @Test
    fun openWorkspaceMenuIsAnActivityRouteThatOpensTheMenuWhileTheFeatureIsOn() {
        val on = controller(enabled = true)

        assertTrue(handler(on).handle(LauncherShellAction.OpenWorkspaceMenu))

        assertTrue(on.state.value.isVisible)
    }

    @Test
    fun withTheFeatureOffTheGestureIsConsumedButNothingOpens() {
        val off = controller(enabled = false)

        assertTrue(handler(off).handle(LauncherShellAction.OpenWorkspaceMenu))

        assertFalse(off.state.value.isVisible)
    }
}
