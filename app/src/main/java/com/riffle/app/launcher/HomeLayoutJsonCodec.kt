package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONArray
import org.json.JSONObject

fun encodeHomeLayout(layout: HomeLayout): String = encodeHomeLayoutObject(layout).toString()

fun decodeHomeLayout(value: String): HomeLayout =
    JSONObject(value).let { json ->
        when {
            json.isHomeLayoutSetJson -> json.toHomeLayoutSet().activeLayout
            else -> json.toHomeLayout()
        }
    }

internal fun encodeHomeLayoutObject(layout: HomeLayout): JSONObject =
    JSONObject()
        .put("viewMode", layout.viewMode.name)
        .put("selectedPageId", layout.selectedPageId.value)
        .put("settings", encodeSettings(layout.settings))
        .put("pages", JSONArray(layout.pages.map(::encodePage)))
        .put("dock", encodeDock(layout.dock))

internal fun JSONObject.toHomeLayout(defaults: HomeLayout = HomeLayoutDefaults.standard()): HomeLayout =
    let { json ->
        val settings = json.optJSONObject("settings")?.toSettings(defaults.settings) ?: defaults.settings
        val pages =
            json.optJSONArray("pages")
                ?.toPages(defaultGrid = settings.grid.dimensions)
                .orEmpty()
                .map { page -> page.copy(grid = settings.grid.dimensions) }
        val selectedPageId = LauncherPageId(json.optString("selectedPageId", defaults.selectedPageId.value))
        val safeSelectedPageId =
            pages.firstOrNull { page -> page.id == selectedPageId }?.id
                ?: pages.firstOrNull()?.id
                ?: defaults.selectedPageId

        defaults.copy(
            viewMode = json.optViewMode(defaults.viewMode),
            pages = pages.ifEmpty { defaults.pages },
            selectedPageId = safeSelectedPageId,
            dock = json.optJSONObject("dock")?.toDock(defaults.dock) ?: defaults.dock,
            settings = settings,
        )
    }

private fun JSONObject.optViewMode(default: LauncherViewMode): LauncherViewMode =
    optString("viewMode", "")
        .takeIf(String::isNotBlank)
        ?.let { value -> runCatching { LauncherViewMode.valueOf(value) }.getOrNull() }
        ?: default

private fun encodeDock(dock: DockModel): JSONObject =
    JSONObject()
        .put("isEnabled", dock.isEnabled)
        .put("showNotificationCards", dock.showNotificationCards)
        .put("iconSizeDp", dock.iconSizeDp)
        .put("backgroundAlphaPercent", dock.backgroundAlphaPercent)
        .put("backgroundSizing", dock.backgroundSizing.name)
        .put("itemSpacingDp", dock.itemSpacingDp)
        .put("capacity", dock.capacity)
        .put("items", JSONArray(dock.items.map(::encodeLauncherItem)))

private fun JSONObject.toDock(defaults: DockModel): DockModel =
    DockModel(
        isEnabled = optBoolean("isEnabled", defaults.isEnabled),
        showNotificationCards = optBoolean("showNotificationCards", defaults.showNotificationCards),
        iconSizeDp = optInt("iconSizeDp", defaults.iconSizeDp),
        backgroundAlphaPercent =
            optInt(
                "backgroundAlphaPercent",
                defaults.backgroundAlphaPercent,
            ),
        backgroundSizing =
            optString("backgroundSizing", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockBackgroundSizing.valueOf(value) }.getOrNull() }
                ?: defaults.backgroundSizing,
        itemSpacingDp = optInt("itemSpacingDp", defaults.itemSpacingDp),
        capacity = optInt("capacity", defaults.capacity),
        items = optJSONArray("items")?.toLauncherItems().orEmpty(),
    )

private fun encodePage(page: LauncherPage): JSONObject =
    JSONObject()
        .put("id", page.id.value)
        .put("type", page.type.typeName)
        .apply {
            val pageType = page.type
            if (pageType is LauncherPageType.Generated) {
                put("generatedKind", pageType.kind.name)
            }
        }
        .put("columns", page.grid.columns)
        .put("rows", page.grid.rows)
        .put("items", JSONArray(page.items.map(::encodeLauncherItem)))

private fun JSONArray.toPages(defaultGrid: GridDimensions): List<LauncherPage> =
    (0 until length())
        .mapNotNull { index ->
            optJSONObject(index)?.let { page ->
                runCatching { page.toPage(defaultGrid = defaultGrid) }.getOrNull()
            }
        }

private fun JSONObject.toPage(defaultGrid: GridDimensions): LauncherPage =
    LauncherPage(
        id = LauncherPageId(getString("id")),
        type = optPageType(),
        grid =
            GridDimensions(
                columns = optInt("columns", defaultGrid.columns),
                rows = optInt("rows", defaultGrid.rows),
            ),
        items = optJSONArray("items")?.toLauncherItems().orEmpty(),
    )
