package com.riffle.app.launcher.pairs

import android.content.Context

/**
 * The one [AppPairsRepository] of the process: the Stream shows it, the pool's first read hands the Dock's former
 * pairs to it, and a backup reads and restores it.
 */
internal object AppPairsRepositories {
    @Volatile
    private var instance: AppPairsRepository? = null

    fun get(context: Context): AppPairsRepository =
        instance ?: synchronized(this) {
            instance ?: AppPairsRepository(SharedPreferencesAppPairsStore(context.applicationContext))
                .also { instance = it }
        }
}
