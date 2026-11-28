package com.riffle.app.launcher.summon

import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.summon.SummonEffect
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class SummonAppActionsTest {
    private val mail = AppIdentity(AppPackageName("pkg.mail"), AppActivityName("pkg.mail.Main"))

    private fun shortcut(
        id: String,
        rank: Int,
        enabled: Boolean = true,
    ) = AppShortcut(AppShortcutId(id), mail, shortLabel = id, rank = rank, enabled = enabled)

    private val compose = shortcut("Compose", rank = 1)
    private val inbox = shortcut("Inbox", rank = 0)

    private val model =
        SummonRingModel.from(
            settings = SummonSettings(slots = listOf(SummonSlot.App(mail))),
            layout =
                HomeLayout(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    pages = listOf(LauncherPage(LauncherPageId("p"), grid = GridDimensions(4, 5))),
                    selectedPageId = LauncherPageId("p"),
                    dock = DockModel(capacity = 4),
                ),
            installed = listOf(InstalledApp(mail, "Mail")),
            glanceLabel = { "" },
            favouritesLabel = "Favourites",
            actions =
                SummonAppActionSource(
                    shortcuts = mapOf(mail to listOf(compose, shortcut("Broken", rank = 2, enabled = false), inbox)),
                    appInfoLabel = "App info",
                ),
        )

    @Test
    fun anAppsLevelIsItsEnabledShortcutsByRankThenAppInfo() {
        assertEquals(listOf("Inbox", "Compose", "App info"), model.level(listOf(0)).map { it.label })
    }

    @Test
    fun lettingGoOnAnActionRunsIt() {
        assertEquals(
            LauncherShellAction.LaunchAppShortcut(inbox),
            summonActionFor(SummonEffect.Launch(listOf(0), 0), model),
        )
        assertEquals(LauncherShellAction.OpenAppInfo(mail), summonActionFor(SummonEffect.Launch(listOf(0), 2), model))
    }

    @Test
    fun theReadoutSaysAnAppWithActionsCanBePushedThrough() {
        val phase =
            com.riffle.core.domain.launcher.summon.SummonPhase.Open(
                origin = com.riffle.core.domain.launcher.summon.RingPoint(0f, 0f),
                path = emptyList(),
                layout =
                    com.riffle.core.domain.launcher.summon.RingLayout(
                        com.riffle.core.domain.launcher.summon.RingPoint(0f, 0f),
                        100f,
                        listOf(-90f),
                        90f,
                        true,
                    ),
                finger = com.riffle.core.domain.launcher.summon.RingPoint(0f, 0f),
                aim = 0,
            )
        assertEquals(SummonReadoutHint.OPEN_OR_ACTIONS, summonReadout(phase, model)?.hint)
    }
}
