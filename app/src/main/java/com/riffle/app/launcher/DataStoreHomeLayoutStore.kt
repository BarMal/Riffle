package com.riffle.app.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.pool.DataStorePoolStore
import com.riffle.app.launcher.pool.HomeLayoutCutoverStore
import com.riffle.app.launcher.pool.PoolHomeLayoutRepository
import com.riffle.app.launcher.pool.PoolHomePreference
import com.riffle.app.launcher.pool.PoolHomeSwitch
import com.riffle.app.launcher.pool.openHomeLayouts
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

private val Context.homeLayoutDataStore by preferencesDataStore(
    name = "riffle_home_layout",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, HomeLayoutDataStoreKeys.PREFERENCES_NAME))
    },
)

/**
 * The durable home layout set: one JSON blob in DataStore, read and written with suspend calls only.
 *
 * Nothing on the UI path talks to this directly -- [HomeLayoutRepositories] wraps it in a
 * [WriteBehindHomeLayoutRepository] that serves reads from memory and writes here on [Dispatchers.IO].
 * Once the standard home renders from the placed-items pool (S4) the blob holds no placed items, and
 * [readPrePoolSnapshot] keeps the full set as it was at the cutover (see [openHomeLayouts]).
 */
internal class DataStoreHomeLayoutStore(context: Context) : HomeLayoutCutoverStore {
    private val dataStore = context.homeLayoutDataStore

    override suspend fun read(): HomeLayoutSet? =
        dataStore.data.first()[HomeLayoutDataStoreKeys.homeLayout]
            ?.let { value -> runCatching { decodeHomeLayoutSet(value) }.getOrNull() }

    override suspend fun write(layoutSet: HomeLayoutSet) {
        val encoded = encodeHomeLayoutSet(layoutSet)
        dataStore.edit { preferences ->
            preferences[HomeLayoutDataStoreKeys.homeLayout] = encoded
        }
    }

    override suspend fun readPrePoolSnapshot(): HomeLayoutSet? =
        dataStore.data.first()[HomeLayoutDataStoreKeys.prePoolSnapshot]
            ?.let { value -> runCatching { decodeHomeLayoutSet(value) }.getOrNull() }

    override suspend fun writePrePoolSnapshot(snapshot: HomeLayoutSet?) {
        val encoded = snapshot?.let(::encodeHomeLayoutSet)
        dataStore.edit { preferences ->
            if (encoded == null) {
                preferences.remove(HomeLayoutDataStoreKeys.prePoolSnapshot)
            } else {
                preferences[HomeLayoutDataStoreKeys.prePoolSnapshot] = encoded
            }
        }
    }
}

/**
 * The home layout repositories of the process, opened once. [repository] is what the shell talks to: the
 * [PoolHomeLayoutRepository] when the standard home renders from the placed-items pool, else [writeBehind] itself.
 * As a lifecycle observer it makes both durable on `onStop` (the pool first).
 */
internal class HomeLayoutRuntime(
    val writeBehind: WriteBehindHomeLayoutRepository,
    val poolHome: PoolHomeLayoutRepository?,
    val pool: CachedPoolRepository,
    val poolHomeSwitch: PoolHomeSwitch,
) : DefaultLifecycleObserver {
    val repository: HomeLayoutRepository = poolHome ?: writeBehind

    override fun onStop(owner: LifecycleOwner) {
        poolHome?.let { flushPoolHomeBlocking(it) }
        flushHomeLayoutBlocking(writeBehind)
    }
}

/**
 * One home layout repository per process, so every activity and view model instance shares the same
 * in-memory layout set (and the same pending write) instead of re-reading a store that may lag it.
 * The placed-items pool repository is shared the same way (the standard home, the Workspaces preview and
 * backup all read and write one in-memory pool).
 */
internal object HomeLayoutRepositories {
    @Volatile
    private var instance: HomeLayoutRuntime? = null

    @Volatile
    private var poolInstance: CachedPoolRepository? = null

    fun runtime(context: Context): HomeLayoutRuntime =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { runtime -> instance = runtime }
        }

    fun pool(context: Context): CachedPoolRepository =
        poolInstance ?: synchronized(this) {
            poolInstance
                ?: CachedPoolRepository(DataStorePoolStore(context.applicationContext)).also { poolInstance = it }
        }

    private fun create(context: Context): HomeLayoutRuntime {
        val store = DataStoreHomeLayoutStore(context)
        val pool = pool(context)
        val preference = PoolHomePreference(context)
        val providerOf = appWidgetProviderLookup(context)
        val opening = openHomeLayoutsAtStartup(store, pool, preference.isEnabled(), providerOf)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val writeBehind =
            WriteBehindHomeLayoutRepository(
                initialLayoutSet = opening.stored,
                persist = { layoutSet -> store.write(layoutSet) },
                scope = scope,
                onWriteFailed = { failure -> Log.w(HOME_LAYOUT_STORE_LOG_TAG, "Home layout write failed", failure) },
            )
        val poolHome =
            if (opening.poolHome) {
                PoolHomeLayoutRepository(legacy = writeBehind, pool = pool, scope = scope, providerOf = providerOf)
                    .also { repository -> repository.open(opening.stored) }
            } else {
                null
            }
        return HomeLayoutRuntime(writeBehind, poolHome, pool, PoolHomeSwitch(preference, opening.poolHome))
    }

    /** The provider a widget host id is bound to (read-only), for migrating widgets into the pool. */
    private fun appWidgetProviderLookup(context: Context): (HostedWidgetId) -> WidgetProviderIdentity? {
        val manager = AppWidgetManager.getInstance(context)
        return { hostedWidgetId ->
            runCatching { manager.getAppWidgetInfo(hostedWidgetId.value)?.provider }.getOrNull()?.let { provider ->
                WidgetProviderIdentity(
                    AppPackageName(provider.packageName),
                    WidgetProviderClassName(provider.className),
                )
            }
        }
    }
}

private const val HOME_LAYOUT_STORE_LOG_TAG = "RiffleHomeLayout"

private object HomeLayoutDataStoreKeys {
    const val PREFERENCES_NAME = "riffle_home_layout"
    val homeLayout = stringPreferencesKey("home_layout")
    val prePoolSnapshot = stringPreferencesKey("home_layout_pre_pool")
}
