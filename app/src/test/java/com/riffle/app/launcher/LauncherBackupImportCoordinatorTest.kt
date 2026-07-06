package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherSettings
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
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherBackupImportCoordinatorTest {
    @Test
    fun turnsImportedDocumentIntoSettingsAction() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(
            LauncherBackupImportOutcome.Imported(
                LauncherShellAction.ImportLauncherBackup(document),
            ),
            result,
        )
    }

    @Test
    fun reportsFailureForFailedImport() {
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Failure)

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }

    @Test
    fun rejectsImportWhenActiveLayoutIsMissing() {
        val document =
            LauncherBackupDocument(
                homeLayoutSet =
                    HomeLayoutSet(
                        activeKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE),
                        layouts = emptyMap(),
                    ),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }

    @Test
    fun rejectsImportWhenPageGridIsInvalid() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage.copy(
                            grid = GridDimensions(columns = 0, rows = 5),
                        ),
                    ),
            )
        val document =
            LauncherBackupDocument(
                homeLayoutSet = HomeLayoutSet.fromLayout(layout),
                launcherSettings = LauncherSettings(),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

        assertEquals(LauncherBackupImportOutcome.Failure, result)
    }

    @Test
    fun acceptsImportWhenOverlayDockSettingsUseBoundaryValues() {
        val document =
            validDocument(
                launcherSettings =
                    LauncherSettings(
                        overlayDock =
                            OverlayDockSettings(
                                handleThicknessDp = MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                                handleHeightDp = MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                                verticalOffsetDp = MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
                                handleAlphaPercent = MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                                expandedIconSizeDp = MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                            ),
                    ),
            )
        val maxDocument =
            validDocument(
                launcherSettings =
                    LauncherSettings(
                        overlayDock =
                            OverlayDockSettings(
                                handleThicknessDp = MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                                handleHeightDp = MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                                verticalOffsetDp = MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP,
                                handleAlphaPercent = MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                                expandedIconSizeDp = MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                            ),
                    ),
            )
        val coordinator = LauncherBackupImportCoordinator()

        val minResult = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))
        val maxResult = coordinator.handleImportResult(LauncherBackupImportResult.Imported(maxDocument))

        assertEquals(
            LauncherBackupImportOutcome.Imported(
                LauncherShellAction.ImportLauncherBackup(document),
            ),
            minResult,
        )
        assertEquals(
            LauncherBackupImportOutcome.Imported(
                LauncherShellAction.ImportLauncherBackup(maxDocument),
            ),
            maxResult,
        )
    }

    @Test
    fun rejectsImportWhenOverlayDockSettingsBypassDecodeBounds() {
        val invalidOverlayDockSettings =
            listOf(
                OverlayDockSettings(handleThicknessDp = MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP - 1),
                OverlayDockSettings(handleThicknessDp = MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP + 1),
                OverlayDockSettings(handleHeightDp = MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP - 1),
                OverlayDockSettings(handleHeightDp = MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP + 1),
                OverlayDockSettings(verticalOffsetDp = MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP - 1),
                OverlayDockSettings(verticalOffsetDp = MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP + 1),
                OverlayDockSettings(handleAlphaPercent = MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT - 1),
                OverlayDockSettings(handleAlphaPercent = MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT + 1),
                OverlayDockSettings(expandedIconSizeDp = MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP - 1),
                OverlayDockSettings(expandedIconSizeDp = MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP + 1),
            )
        val coordinator = LauncherBackupImportCoordinator()

        invalidOverlayDockSettings.forEach { overlayDockSettings ->
            val document =
                validDocument(
                    launcherSettings =
                        LauncherSettings(
                            overlayDock = overlayDockSettings,
                        ),
                )

            val result = coordinator.handleImportResult(LauncherBackupImportResult.Imported(document))

            assertEquals(LauncherBackupImportOutcome.Failure, result)
        }
    }

    private fun validDocument(launcherSettings: LauncherSettings = LauncherSettings()): LauncherBackupDocument =
        LauncherBackupDocument(
            homeLayoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()),
            launcherSettings = launcherSettings,
        )
}
