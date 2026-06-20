package com.riffle.core.domain.launcher.home

interface HomeLayoutRepository {
    fun loadHomeLayout(): HomeLayout?

    fun saveHomeLayout(layout: HomeLayout)
}
