package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesAppVisibilityRepository(context: Context) : AppVisibilityRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hiddenAppIdentities(): Set<AppIdentity> =
        preferences.getString(KEY_HIDDEN_APPS, null)
            ?.let { value -> runCatching { decodeHiddenAppIdentities(value) }.getOrDefault(emptySet()) }
            .orEmpty()

    override fun hideApp(identity: AppIdentity) {
        saveHiddenAppIdentities(hiddenAppIdentities() + identity)
    }

    override fun showApp(identity: AppIdentity) {
        saveHiddenAppIdentities(hiddenAppIdentities() - identity)
    }

    private fun saveHiddenAppIdentities(identities: Set<AppIdentity>) {
        preferences.edit()
            .putString(KEY_HIDDEN_APPS, encodeHiddenAppIdentities(identities))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "riffle_app_visibility"
        const val KEY_HIDDEN_APPS = "hidden_apps"
    }
}

fun encodeHiddenAppIdentities(identities: Set<AppIdentity>): String {
    return JSONArray(
        identities.map { identity -> identity.toJson() },
    ).toString()
}

fun decodeHiddenAppIdentities(value: String): Set<AppIdentity> =
    JSONArray(value)
        .let { array ->
            (0 until array.length())
                .map { index -> array.getJSONObject(index).toAppIdentity() }
                .toSet()
        }

private fun AppIdentity.toJson(): JSONObject =
    JSONObject()
        .put("packageName", packageName.value)
        .put("activityName", activityName.value)
        .put("profileId", profile.id.value)
        .put("profileType", profile.type.name)

private fun JSONObject.toAppIdentity(): AppIdentity =
    AppIdentity(
        packageName = AppPackageName(getString("packageName")),
        activityName = AppActivityName(getString("activityName")),
        profile =
            AppProfile(
                id = AppProfileId(getString("profileId")),
                type = AppProfileType.valueOf(getString("profileType")),
            ),
    )
