package com.riffle.core.domain.launcher.home

interface HomeLayoutRepository {
    fun loadHomeLayout(): HomeLayout?

    fun saveHomeLayout(layout: HomeLayout)

    fun loadHomeLayoutSet(): HomeLayoutSet? = loadHomeLayout()?.let(HomeLayoutSet::fromLayout)

    fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) = saveHomeLayout(layoutSet.activeLayout)
}
