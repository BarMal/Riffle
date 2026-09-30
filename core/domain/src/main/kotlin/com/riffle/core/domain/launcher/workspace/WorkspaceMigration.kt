package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode

/**
 * One-time migration from the mode-based [HomeLayoutSet] to workspaces, without loss.
 *
 * Per device class, each mode that has a stored layout (plus the mode the device class currently shows)
 * becomes one workspace; the shown mode's workspace is the active and default one. See
 * docs/product/workspaces-sources-lenses.md ("Migration mapping") for the table. Pure and
 * deterministic, so running it again yields the same result; [ensureMigrated] never overwrites
 * workspaces that already exist.
 */
object WorkspaceMigration {
    /** The default workspace a layout gets when nothing is stored: the Standard (Nova-style) home. */
    fun defaultFor(deviceClass: HomeLayoutDeviceClass): LayoutWorkspaces =
        LayoutWorkspaces.single(
            HomeLayoutWorkspaceMapper.map(
                deviceClass,
                HomeLayoutDefaults.standard(deviceClass),
                LauncherViewMode.STANDARD_APP_DRAWER,
            ),
        )

    fun migrate(layoutSet: HomeLayoutSet): WorkspaceSet =
        WorkspaceSet(deviceClassesOf(layoutSet).associateWith { migrateDeviceClass(layoutSet, it) })

    /**
     * Keeps [stored] workspaces as they are and migrates only the device classes it has none for, so
     * re-running after a partial or complete migration changes nothing already stored.
     */
    fun ensureMigrated(
        stored: WorkspaceSet?,
        layoutSet: HomeLayoutSet,
    ): WorkspaceSet {
        val migrated = migrate(layoutSet)
        return if (stored == null) migrated else WorkspaceSet(migrated.layouts + stored.layouts)
    }

    private fun deviceClassesOf(layoutSet: HomeLayoutSet): List<HomeLayoutDeviceClass> =
        (
            layoutSet.layouts.keys.map { it.deviceClass } +
                layoutSet.activeKey.deviceClass +
                layoutSet.preferredModesByDeviceClass.keys
        ).distinct().sorted()

    private fun migrateDeviceClass(
        layoutSet: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
    ): LayoutWorkspaces {
        val shown = shownMode(layoutSet, deviceClass)
        val stored = layoutSet.layouts.keys.filter { it.deviceClass == deviceClass }.map { it.viewMode }
        val modes = (stored + shown).toSortedSet()
        val workspaces =
            modes.map { mode ->
                HomeLayoutWorkspaceMapper.map(deviceClass, layoutSet.layoutFor(HomeLayoutKey(mode, deviceClass)), mode)
            }
        val shownId = HomeLayoutWorkspaceMapper.workspaceId(deviceClass, shown)
        return LayoutWorkspaces(workspaces, activeId = shownId, defaultId = shownId)
    }

    private fun shownMode(
        layoutSet: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
    ): LauncherViewMode =
        when (deviceClass) {
            layoutSet.activeKey.deviceClass -> layoutSet.activeKey.viewMode
            else -> layoutSet.preferredModesByDeviceClass[deviceClass] ?: LauncherViewMode.STANDARD_APP_DRAWER
        }
}
