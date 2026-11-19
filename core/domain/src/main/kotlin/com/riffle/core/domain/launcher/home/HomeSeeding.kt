package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryCategories

/** How Home starts on a fresh install ("How should Home start?" in the set-up). */
enum class HomeStart {
    /** Every visible app A to Z (the Arrange rule, with the stored Compact choice); the default and recommended. */
    ALL_APPS,

    /** One empty page; the apps live in the Library. */
    EMPTY,
}

/**
 * The apps the Dock starts with, resolved by role or intent (never by package name): the default dialer, SMS app and
 * browser, and the camera, in that order. A role with no launchable app is simply left out. The Android side answers
 * with the packages the system resolved; tests use a fake.
 */
fun interface DockDefaultsResolver {
    fun resolve(): List<AppPackageName>
}

/**
 * The first-run Home (`places-views-plan.md`, "First run"): seeds the Dock with [DockDefaultsResolver]'s apps in both
 * cases and, for [HomeStart.ALL_APPS], places every other visible app A to Z. Pure and never throws.
 */
object HomeSeeding {
    const val MAX_DOCK_DEFAULTS = 4

    /** A fresh Home is one with nothing placed on any page and nothing in the Dock. */
    fun isFresh(layout: HomeLayout): Boolean = layout.pages.all { it.items.isEmpty() } && layout.dock.items.isEmpty()

    /** The Dock's apps for [packages]: one launchable app per package (the personal profile's first), no repeats. */
    fun dockApps(
        apps: List<InstalledApp>,
        packages: List<AppPackageName>,
    ): List<InstalledApp> =
        packages
            .distinct()
            .mapNotNull { pkg ->
                apps
                    .filter { it.identity.packageName == pkg }
                    .sortedWith(
                        compareBy<InstalledApp> { it.identity.profile.type != AppProfileType.PERSONAL }
                            .then(LibraryCategories.appOrder),
                    ).firstOrNull()
            }.distinctBy { it.identity }
            .take(MAX_DOCK_DEFAULTS)

    /**
     * [layout] seeded, or [layout] itself when it is not [isFresh] (a seed never replaces what a user placed). The
     * apps that go to the Dock are not placed on Home as well.
     */
    fun seed(
        layout: HomeLayout,
        apps: List<InstalledApp>,
        dockPackages: List<AppPackageName>,
        start: HomeStart,
        compact: Boolean,
    ): HomeLayout {
        if (!isFresh(layout)) return layout
        val engine = DockEngine()
        val withDock =
            dockApps(apps, dockPackages).fold(layout) { current, app ->
                (engine.addAppToDock(current, app) as? DockEditResult.Updated)?.layout ?: current
            }
        return when (start) {
            HomeStart.EMPTY -> withDock
            HomeStart.ALL_APPS ->
                HomeArrangement.arrange(withDock, compact, HomeArrangement.missingApps(withDock, apps))
        }
    }
}
