package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile

interface HomeLayoutRepository {
    fun loadHomeLayout(): HomeLayout?

    fun saveHomeLayout(layout: HomeLayout)

    fun loadHomeLayoutSet(): HomeLayoutSet? = loadHomeLayout()?.let(HomeLayoutSet::fromLayout)

    fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) = saveHomeLayout(layoutSet.activeLayout)

    /**
     * Saves [layoutSet] and returns the set the shell keeps in memory from now on. A plain store returns [layoutSet]
     * itself; a store that renders the home from somewhere else (the placed-items pool, S4) returns its view of
     * [layoutSet], with the placed items as that store now holds them. Every layout write of the shell goes through
     * here, so the shell never holds a set the store would show differently.
     */
    fun commitHomeLayoutSet(layoutSet: HomeLayoutSet): HomeLayoutSet = layoutSet.also(::saveHomeLayoutSet)

    /**
     * A confirmed *package removed* event (never "absent from a snapshot", never a paused profile): returns the set to
     * keep in memory once stores beyond [layoutSet] have dropped that package. A plain store holds nothing else and
     * returns [layoutSet] itself (the same instance).
     */
    fun withoutRemovedPackage(
        layoutSet: HomeLayoutSet,
        packageName: AppPackageName,
        profile: AppProfile,
    ): HomeLayoutSet = layoutSet
}
