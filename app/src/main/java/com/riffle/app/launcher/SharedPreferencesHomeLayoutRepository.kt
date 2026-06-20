package com.riffle.app.launcher

import android.content.Context
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesHomeLayoutRepository(context: Context) : HomeLayoutRepository {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadHomeLayout(): HomeLayout? =
        preferences.getString(KEY_HOME_LAYOUT, null)
            ?.let { value -> runCatching { decodeHomeLayout(value) }.getOrNull() }

    override fun saveHomeLayout(layout: HomeLayout) {
        preferences.edit()
            .putString(KEY_HOME_LAYOUT, encodeHomeLayout(layout))
            .apply()
    }

    private fun encodeHomeLayout(layout: HomeLayout): String =
        JSONObject()
            .put("selectedPageId", layout.selectedPageId.value)
            .put("pages", JSONArray(layout.pages.map(::encodePage)))
            .toString()

    private fun decodeHomeLayout(value: String): HomeLayout =
        JSONObject(value).let { json ->
            val defaults = HomeLayoutDefaults.standard()
            val pages = json.getJSONArray("pages").toPages()
            val selectedPageId = LauncherPageId(json.optString("selectedPageId", defaults.selectedPageId.value))
            val safeSelectedPageId =
                pages.firstOrNull { page -> page.id == selectedPageId }?.id
                    ?: pages.firstOrNull()?.id
                    ?: defaults.selectedPageId

            defaults.copy(
                pages = pages.ifEmpty { defaults.pages },
                selectedPageId = safeSelectedPageId,
            )
        }

    private fun encodePage(page: LauncherPage): JSONObject =
        JSONObject()
            .put("id", page.id.value)
            .put("columns", page.grid.columns)
            .put("rows", page.grid.rows)
            .put("items", JSONArray(page.items.filterIsInstance<AppShortcutItem>().map(::encodeShortcut)))

    private fun JSONArray.toPages(): List<LauncherPage> =
        (0 until length())
            .map { index -> getJSONObject(index) }
            .map { page -> page.toPage() }

    private fun JSONObject.toPage(): LauncherPage =
        LauncherPage(
            id = LauncherPageId(getString("id")),
            grid =
                GridDimensions(
                    columns = getInt("columns"),
                    rows = getInt("rows"),
                ),
            items = getJSONArray("items").toShortcuts(),
        )

    private fun encodeShortcut(shortcut: AppShortcutItem): JSONObject =
        JSONObject()
            .put("id", shortcut.id.value)
            .put("label", shortcut.label)
            .put("packageName", shortcut.appIdentity.packageName.value)
            .put("activityName", shortcut.appIdentity.activityName.value)
            .put("column", shortcut.placement?.cell?.column)
            .put("row", shortcut.placement?.cell?.row)
            .put("columns", shortcut.placement?.span?.columns)
            .put("rows", shortcut.placement?.span?.rows)

    private fun JSONArray.toShortcuts(): List<AppShortcutItem> =
        (0 until length())
            .map { index -> getJSONObject(index) }
            .map { shortcut -> shortcut.toShortcut() }

    private fun JSONObject.toShortcut(): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(getString("id")),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName(getString("packageName")),
                    activityName = AppActivityName(getString("activityName")),
                    profile = AppProfile.personal(),
                ),
            label = getString("label"),
            placement =
                GridPlacement(
                    cell =
                        GridCell(
                            column = getInt("column"),
                            row = getInt("row"),
                        ),
                    span =
                        GridSpan(
                            columns = getInt("columns"),
                            rows = getInt("rows"),
                        ),
                ),
        )

    private companion object {
        const val PREFERENCES_NAME = "riffle_home_layout"
        const val KEY_HOME_LAYOUT = "home_layout"
    }
}
