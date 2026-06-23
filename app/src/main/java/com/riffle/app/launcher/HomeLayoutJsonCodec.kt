package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONArray
import org.json.JSONObject

fun encodeHomeLayout(layout: HomeLayout): String =
    JSONObject()
        .put("viewMode", layout.viewMode.name)
        .put("selectedPageId", layout.selectedPageId.value)
        .put("settings", encodeSettings(layout.settings))
        .put("pages", JSONArray(layout.pages.map(::encodePage)))
        .put("dock", encodeDock(layout.dock))
        .toString()

fun decodeHomeLayout(value: String): HomeLayout =
    JSONObject(value).let { json ->
        val defaults = HomeLayoutDefaults.standard()
        val pages = json.getJSONArray("pages").toPages()
        val selectedPageId = LauncherPageId(json.optString("selectedPageId", defaults.selectedPageId.value))
        val safeSelectedPageId =
            pages.firstOrNull { page -> page.id == selectedPageId }?.id
                ?: pages.firstOrNull()?.id
                ?: defaults.selectedPageId

        defaults.copy(
            viewMode = json.optViewMode(defaults.viewMode),
            pages = pages.ifEmpty { defaults.pages },
            selectedPageId = safeSelectedPageId,
            dock = json.optJSONObject("dock")?.toDock() ?: defaults.dock,
            settings = json.optJSONObject("settings")?.toSettings(defaults.settings) ?: defaults.settings,
        )
    }

private fun JSONObject.optViewMode(default: LauncherViewMode): LauncherViewMode =
    optString("viewMode", "")
        .takeIf(String::isNotBlank)
        ?.let { value -> runCatching { LauncherViewMode.valueOf(value) }.getOrNull() }
        ?: default

private fun encodeDock(dock: DockModel): JSONObject =
    JSONObject()
        .put("capacity", dock.capacity)
        .put("items", JSONArray(dock.items.map(::encodeLauncherItem)))

private fun JSONObject.toDock(): DockModel =
    DockModel(
        capacity = optInt("capacity", HomeLayoutDefaults.standard().dock.capacity),
        items = optJSONArray("items")?.toLauncherItems().orEmpty(),
    )

private fun encodePage(page: LauncherPage): JSONObject =
    JSONObject()
        .put("id", page.id.value)
        .put("columns", page.grid.columns)
        .put("rows", page.grid.rows)
        .put("items", JSONArray(page.items.map(::encodeLauncherItem)))

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
        items = getJSONArray("items").toLauncherItems(),
    )
