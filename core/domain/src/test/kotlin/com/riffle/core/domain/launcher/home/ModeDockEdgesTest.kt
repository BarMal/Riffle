package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ModeDockEdgesTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET

    @Test
    fun libraryModeIsTheLibrarySurfaceAndEveryOtherModeIsHome() {
        assertEquals(ModeSurface.LIBRARY, LauncherViewMode.HOME_SCREEN_LIBRARY.modeSurface)
        assertEquals(ModeSurface.HOME, LauncherViewMode.CARD_INTERFACE.modeSurface)
        assertEquals(ModeSurface.HOME, LauncherViewMode.STANDARD_APP_DRAWER.modeSurface)
    }

    @Test
    fun eachSurfaceLeadsToTheOther() {
        assertEquals(ModeSurface.LIBRARY, ModeSurface.HOME.other)
        assertEquals(ModeSurface.HOME, ModeSurface.LIBRARY.other)
    }

    @Test
    fun horizontalEdgesRunAStripAndSideEdgesAColumn() {
        assertEquals(DockOrientation.HORIZONTAL, DockPosition.BOTTOM.dockOrientation)
        assertEquals(DockOrientation.HORIZONTAL, DockPosition.TOP.dockOrientation)
        assertEquals(DockOrientation.VERTICAL, DockPosition.LEFT.dockOrientation)
        assertEquals(DockOrientation.VERTICAL, DockPosition.RIGHT.dockOrientation)
    }

    @Test
    fun anUnconfiguredDeviceClassTakesTheTemplateEdgeAtHomeAndTheBottomInLibrary() {
        val set = HomeLayoutSet.standard()

        assertNull(set.storedDockEdgeFor(phone, ModeSurface.HOME))
        assertNull(set.storedDockEdgeFor(phone, ModeSurface.LIBRARY))
        assertEquals(
            DockPosition.RIGHT,
            set.dockEdgeFor(phone, ModeSurface.HOME, templateEdge = DockPosition.RIGHT),
        )
        assertEquals(FALLBACK_HOME_DOCK_EDGE, set.dockEdgeFor(phone, ModeSurface.HOME))
        assertEquals(
            DockPosition.BOTTOM,
            set.dockEdgeFor(phone, ModeSurface.LIBRARY, templateEdge = DockPosition.RIGHT),
        )
    }

    @Test
    fun homeEdgeIsTheSharedDocksOwnPosition() {
        val set = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.LEFT)

        assertEquals(DockPosition.LEFT, set.dockFor(phone).position)
        assertEquals(DockPosition.LEFT, set.activeLayout.dock.position)
        assertEquals(
            DockPosition.LEFT,
            set.dockEdgeFor(phone, ModeSurface.HOME, templateEdge = DockPosition.BOTTOM),
        )
        // Library is not moved by Home's edge.
        assertEquals(DockPosition.BOTTOM, set.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun libraryEdgeIsStoredBesideTheDockWithoutMovingHome() {
        val base = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.BOTTOM)
        val set = base.withDockEdge(phone, ModeSurface.LIBRARY, DockPosition.RIGHT)

        assertEquals(DockPosition.RIGHT, set.dockEdgeFor(phone, ModeSurface.LIBRARY))
        assertEquals(DockPosition.BOTTOM, set.dockEdgeFor(phone, ModeSurface.HOME))
        assertEquals(base.dockFor(phone), set.dockFor(phone))
        assertEquals(base.layouts, set.layouts)
    }

    @Test
    fun bothSurfacesMayShareAnEdge() {
        val set =
            HomeLayoutSet.standard()
                .withDockEdge(phone, ModeSurface.HOME, DockPosition.TOP)
                .withDockEdge(phone, ModeSurface.LIBRARY, DockPosition.TOP)

        assertEquals(
            ModeDockEdges(home = DockPosition.TOP, library = DockPosition.TOP),
            set.modeDockEdgesFor(phone),
        )
    }

    @Test
    fun edgesArePerDeviceClass() {
        val set =
            HomeLayoutSet.standard()
                .withDockEdge(tablet, ModeSurface.LIBRARY, DockPosition.LEFT)
                .withDockEdge(tablet, ModeSurface.HOME, DockPosition.RIGHT)

        assertEquals(
            ModeDockEdges(home = DockPosition.RIGHT, library = DockPosition.LEFT),
            set.modeDockEdgesFor(tablet),
        )
        assertEquals(
            ModeDockEdges(home = DockPosition.BOTTOM, library = DockPosition.BOTTOM),
            set.modeDockEdgesFor(phone, templateEdge = DockPosition.BOTTOM),
        )
    }

    @Test
    fun resolvedEdgesAnswerPerSurface() {
        val edges = ModeDockEdges(home = DockPosition.LEFT, library = DockPosition.BOTTOM)

        assertEquals(DockPosition.LEFT, edges.edgeFor(ModeSurface.HOME))
        assertEquals(DockPosition.BOTTOM, edges.edgeFor(ModeSurface.LIBRARY))
        assertEquals(DockOrientation.VERTICAL, edges.orientationFor(ModeSurface.HOME))
        assertEquals(DockOrientation.HORIZONTAL, edges.orientationFor(ModeSurface.LIBRARY))
    }

    @Test
    fun migrationGivesLibraryTheEdgeTheUserChose() {
        val legacy = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.RIGHT)

        val migrated = legacy.withLibraryDockEdgesMigrated()

        assertEquals(mapOf(phone to DockPosition.RIGHT), migrated.libraryDockEdgesByDeviceClass)
        assertEquals(
            ModeDockEdges(home = DockPosition.RIGHT, library = DockPosition.RIGHT),
            migrated.modeDockEdgesFor(phone),
        )
        assertEquals(legacy.docks, migrated.docks)
    }

    @Test
    fun migrationLeavesATemplateFollowingDockOnTheLibraryDefault() {
        val legacy = HomeLayoutSet.standard()

        val migrated = legacy.withLibraryDockEdgesMigrated()

        assertSame(legacy, migrated)
        assertEquals(DEFAULT_LIBRARY_DOCK_EDGE, migrated.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun migrationKeepsLibraryEdgesAlreadyStoredAndIsIdempotent() {
        val set =
            HomeLayoutSet.standard()
                .withDockEdge(phone, ModeSurface.HOME, DockPosition.LEFT)
                .withDockEdge(phone, ModeSurface.LIBRARY, DockPosition.BOTTOM)
                .withDockEdge(tablet, ModeSurface.HOME, DockPosition.RIGHT)

        val migrated = set.withLibraryDockEdgesMigrated()

        assertEquals(
            mapOf(phone to DockPosition.BOTTOM, tablet to DockPosition.RIGHT),
            migrated.libraryDockEdgesByDeviceClass,
        )
        assertEquals(migrated, migrated.withLibraryDockEdgesMigrated())
    }

    @Test
    fun modeAndLayoutChangesKeepTheLibraryEdge() {
        val set = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.LIBRARY, DockPosition.LEFT)

        val edited =
            set.withActiveLayout(set.activeLayout)
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                .selectDeviceClass(tablet)

        assertEquals(DockPosition.LEFT, edited.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }
}
