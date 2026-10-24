package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.workspace.LocalWorkspaceHomePages
import com.riffle.app.launcher.workspace.WorkspaceHomePages
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.StaticLensResultProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S9: a workspace lens page drawn after the placed pages is never the selected page, so a Home press, a Return or a
 * menu jump that names the page already selected must still bring the pager back to it (the shell's selection
 * requests, `LocalHomeSelectionRequests`).
 */
@RunWith(AndroidJUnit4::class)
class WorkspaceLensPageReturnTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val app =
        AppShortcutItem(
            id = LauncherItemId("mail"),
            appIdentity = AppIdentity(AppPackageName("com.riffle.mail"), AppActivityName(".MainActivity")),
            label = "Mail",
            placement = GridPlacement(GridCell(0, 0), GridSpan(1, 1)),
        )

    @Test
    fun aSelectionRequestLeavesALensPageForTheSelectedPage() {
        val requests = MutableStateFlow(0)
        val jumps = MutableStateFlow<Int?>(null)
        val lensPage =
            PageContainer(
                ContainerId("lens"),
                PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("test"))), ExpressionKind.LIST)),
            )
        val pages =
            WorkspaceHomePages(
                pages = listOf(lensPage),
                services = ContainerServices(provider = StaticLensResultProvider(LensOutput.Loading)),
                jumpRequest = jumps,
                jumps = jumps,
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(pages = standard.pages.take(1).map { it.copy(items = listOf(app)) })
            }
        val installed = listOf(InstalledApp(identity = app.appIdentity, label = app.label))

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalWorkspaceHomePages provides pages,
                    LocalHomeSelectionRequests provides requests,
                ) {
                    Box(modifier = Modifier.size(420.dp, 720.dp)) {
                        StandardHome(
                            layout = layout,
                            installedApps = installed,
                            interactions = StandardHomeInteractions(),
                            presentation =
                                StandardHomePresentation(
                                    installedApps = installed,
                                    appShortcutsByApp = emptyMap(),
                                ),
                            appIconLoader = EmptyAppIconLoader,
                            onAction = {},
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Mail").assertIsDisplayed()

        // The menu's jump to the lens page: the placed page scrolls away.
        composeRule.runOnIdle { jumps.value = 0 }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Mail").assertDoesNotExist()

        // A Home press (or a Return) names the page that is still selected: the pager comes back.
        composeRule.runOnIdle { requests.update { it + 1 } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Mail").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
