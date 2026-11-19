package com.riffle.app.launcher.layouts

import android.content.Context

/**
 * The one [AppLayoutsRepository] of the process: the Stream shows it, the pool's first read hands the Dock's former
 * pairs to it, and a backup reads and restores it.
 */
internal object AppLayoutsRepositories {
    @Volatile
    private var instance: AppLayoutsRepository? = null

    fun get(context: Context): AppLayoutsRepository =
        instance ?: synchronized(this) {
            instance ?: AppLayoutsRepository(SharedPreferencesAppLayoutsStore(context.applicationContext))
                .also { instance = it }
        }
}
