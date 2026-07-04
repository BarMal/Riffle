package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockItemMoveDirection
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellOverlayDockSettingsTest {
    @Test
    fun savesOverlayDockEnabledSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockEnabled(enabled = true),
        )

        assertEquals(true, viewModel.state.value.launcherSettings.overlayDock.enabled)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesOverlayDockSettingsGloballyWithoutRewritingLayout() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val launcherSettingsRepository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )
        val layoutSaveCountAfterDeviceSwitch = homeLayoutRepository.saveLayoutSetCount

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockEnabled(enabled = true),
        )

        assertEquals(true, viewModel.state.value.launcherSettings.overlayDock.enabled)
        assertEquals(viewModel.state.value.launcherSettings, launcherSettingsRepository.savedSettings)
        assertEquals(layoutSaveCountAfterDeviceSwitch, homeLayoutRepository.saveLayoutSetCount)
    }

    @Test
    fun addsFloatingDockShortcutGloballyWithoutRewritingLayout() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val launcherSettingsRepository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )
        val app = InstalledApp(identity = appIdentity, label = "Example")

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.AddAppToFloatingDock(app),
        )
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.AddAppToFloatingDock(app),
        )

        val overlayDock = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(true, overlayDock.enabled)
        assertEquals(1, overlayDock.items.size)
        assertEquals(app.identity, overlayDock.items.single().appIdentity)
        assertEquals(app.label, overlayDock.items.single().label)
        assertEquals("floating-dock:personal:com.example.app/.MainActivity:1", overlayDock.items.single().id.value)
        assertEquals(viewModel.state.value.launcherSettings, launcherSettingsRepository.savedSettings)
        assertEquals(0, homeLayoutRepository.saveLayoutSetCount)
    }

    @Test
    fun addsFloatingDockAppShortcutGloballyWithoutRewritingLayout() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val launcherSettingsRepository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )
        val shortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = appIdentity,
                shortLabel = "Compose",
                longLabel = "Compose message",
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.AddAppShortcutToFloatingDock(shortcut),
        )
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.AddAppShortcutToFloatingDock(shortcut),
        )

        val overlayDock = viewModel.state.value.launcherSettings.overlayDock
        val item = overlayDock.items.single()
        assertEquals(true, overlayDock.enabled)
        assertEquals(shortcut.appIdentity, item.appIdentity)
        assertEquals(shortcut.id, item.appShortcutId)
        assertEquals("Compose message", item.label)
        assertEquals("floating-dock:personal:com.example.app/.MainActivity:compose:1", item.id.value)
        assertEquals(viewModel.state.value.launcherSettings, launcherSettingsRepository.savedSettings)
        assertEquals(0, homeLayoutRepository.saveLayoutSetCount)
    }

    @Test
    fun removesFloatingDockShortcutGloballyWithoutRewritingLayout() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val camera = shortcut(id = "camera", label = "Camera")
        val phone = shortcut(id = "phone", label = "Phone")
        val launcherSettingsRepository =
            FakeLauncherSettingsRepository(
                savedSettings =
                    LauncherSettings(
                        overlayDock = OverlayDockSettings(enabled = true, items = listOf(camera, phone)),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.RemoveFloatingDockShortcut(camera.id),
        )

        val overlayDock = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(listOf(phone), overlayDock.items)
        assertEquals(viewModel.state.value.launcherSettings, launcherSettingsRepository.savedSettings)
        assertEquals(0, homeLayoutRepository.saveLayoutSetCount)
    }

    @Test
    fun movesFloatingDockShortcutsGloballyWithoutRewritingLayout() {
        val homeLayoutRepository = FakeHomeLayoutRepository()
        val camera = shortcut(id = "camera", label = "Camera")
        val phone = shortcut(id = "phone", label = "Phone")
        val maps = shortcut(id = "maps", label = "Maps")
        val launcherSettingsRepository =
            FakeLauncherSettingsRepository(
                savedSettings =
                    LauncherSettings(
                        overlayDock = OverlayDockSettings(enabled = true, items = listOf(camera, phone, maps)),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.MoveFloatingDockShortcut(
                itemId = phone.id,
                direction = OverlayDockItemMoveDirection.UP,
            ),
        )
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.MoveFloatingDockShortcut(
                itemId = phone.id,
                direction = OverlayDockItemMoveDirection.DOWN,
            ),
        )
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.MoveFloatingDockShortcut(
                itemId = maps.id,
                direction = OverlayDockItemMoveDirection.DOWN,
            ),
        )

        val overlayDock = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(listOf(camera, phone, maps), overlayDock.items)
        assertEquals(viewModel.state.value.launcherSettings, launcherSettingsRepository.savedSettings)
        assertEquals(0, homeLayoutRepository.saveLayoutSetCount)
    }

    @Test
    fun savesOverlayDockHandleSettings() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockEdge(OverlayDockEdge.START))
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockHandleThickness(thicknessDp = 24),
        )
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 96))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -48))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 65))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockExpandedIconSize(sizeDp = 64))
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockExpandedOrientation(OverlayDockExpandedOrientation.TALL),
        )
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockShowLabels(showLabels = true))

        val settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(OverlayDockEdge.START, settings.edge)
        assertEquals(24, settings.handleThicknessDp)
        assertEquals(96, settings.handleHeightDp)
        assertEquals(-48, settings.verticalOffsetDp)
        assertEquals(65, settings.handleAlphaPercent)
        assertEquals(64, settings.expandedIconSizeDp)
        assertEquals(OverlayDockExpandedOrientation.TALL, settings.expandedOrientation)
        assertEquals(true, settings.showLabels)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun clampsOverlayDockHandleSettings() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockHandleThickness(thicknessDp = -1),
        )
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = -1))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = -999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = -1))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockExpandedIconSize(sizeDp = -1))

        var settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP, settings.handleThicknessDp)
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP, settings.handleHeightDp)
        assertEquals(MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP, settings.verticalOffsetDp)
        assertEquals(MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, settings.handleAlphaPercent)
        assertEquals(MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP, settings.expandedIconSizeDp)

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectOverlayDockHandleThickness(thicknessDp = 999),
        )
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleHeight(heightDp = 999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockVerticalOffset(offsetDp = 999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockHandleAlpha(alphaPercent = 999))
        viewModel.onLauncherSettingsActionSelected(LauncherShellAction.SelectOverlayDockExpandedIconSize(sizeDp = 999))

        settings = viewModel.state.value.launcherSettings.overlayDock
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP, settings.handleThicknessDp)
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP, settings.handleHeightDp)
        assertEquals(MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP, settings.verticalOffsetDp)
        assertEquals(MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT, settings.handleAlphaPercent)
        assertEquals(MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP, settings.expandedIconSizeDp)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        private var layoutSet: HomeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
    ) : HomeLayoutRepository {
        var saveLayoutSetCount: Int = 0

        override fun loadHomeLayout(): HomeLayout = layoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            layoutSet = layoutSet.withActiveLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet = layoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            saveLayoutSetCount += 1
            this.layoutSet = layoutSet
        }
    }

    private class FakeLauncherSettingsRepository(
        var savedSettings: LauncherSettings? = null,
    ) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }

    private fun shortcut(
        id: String,
        label: String,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
    }
}
