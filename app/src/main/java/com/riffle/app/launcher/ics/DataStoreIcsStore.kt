package com.riffle.app.launcher.ics

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * DataStore file holding the ICS feed list (which contains feed URLs, possibly with secret tokens) and the
 * parsed-event cache. It must never be part of Android Auto Backup / device transfer: see
 * `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`, which exclude it, and the in-app JSON
 * backup, which has no field for it.
 */
internal const val ICS_FEEDS_DATASTORE_NAME = "riffle_ics_feeds"

private val Context.icsFeedsDataStore by preferencesDataStore(name = ICS_FEEDS_DATASTORE_NAME)

private val SETTINGS_KEY = stringPreferencesKey("ics_feed_settings")
private val CACHE_KEY = stringPreferencesKey("ics_feed_cache")

internal class DataStoreIcsStore(context: Context) : IcsStorePort {
    private val dataStore = context.applicationContext.icsFeedsDataStore

    override suspend fun read(): IcsStoredText {
        val preferences = dataStore.data.first()
        return IcsStoredText(settings = preferences[SETTINGS_KEY], cache = preferences[CACHE_KEY])
    }

    override suspend fun writeSettings(json: String) {
        dataStore.edit { preferences -> preferences[SETTINGS_KEY] = json }
    }

    override suspend fun writeCache(json: String) {
        dataStore.edit { preferences -> preferences[CACHE_KEY] = json }
    }
}
