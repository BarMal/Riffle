package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherTemplateId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.reflowedToWorkspaceGrid
import com.riffle.core.domain.launcher.home.workspaceGridFor
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
        .apply { layout.templateId?.let { templateId -> put("templateId", templateId.value) } }
        .put("selectedPageId", layout.selectedPageId.value)
        .put("settings", encodeSettings(layout.settings))
        .put("pages", JSONArray(layout.pages.map(::encodePage)))
        .put("dock", encodeDock(layout.dock))

internal fun JSONObject.toHomeLayout(defaults: HomeLayout = HomeLayoutDefaults.standard()): HomeLayout =
    let { json ->
        val settings = json.optJSONObject("settings")?.toSettings(defaults.settings) ?: defaults.settings
        val dock =
            json.optJSONObject("dock")
                ?.toDock(defaults = defaults.dock, defaultGrid = settings.grid.dimensions)
                ?: defaults.dock
        // A side dock takes a column, so the grid the pages are held to is the one it leaves --
        // otherwise a reload would put them back to full width underneath it.
        val workspaceGrid = settings.grid.dimensions.workspaceGridFor(dock)
        // The grid each page is held to is settled by the reflow below; this is only the fallback
        // for a stored page that never recorded its own dimensions.
        val pages = json.optJSONArray("pages")?.toPages(defaultGrid = workspaceGrid).orEmpty()
        val selectedPageId = LauncherPageId(json.optString("selectedPageId", defaults.selectedPageId.value))
        val safeSelectedPageId =
            pages.firstOrNull { page -> page.id == selectedPageId }?.id
                ?: pages.firstOrNull()?.id
                ?: defaults.selectedPageId

        defaults.copy(
            viewMode = json.optViewMode(defaults.viewMode),
            templateId =
                json
                    .optString("templateId", "")
                    .takeIf(String::isNotBlank)
                    ?.let(::LauncherTemplateId),
            pages = pages.ifEmpty { defaults.pages },
            selectedPageId = safeSelectedPageId,
            dock = dock,
            settings = settings,
        ).reflowedToWorkspaceGrid()
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
        .put("isExpandable", dock.isExpandable)
        .put("expandAffordance", dock.expandAffordance.name)
        .put("position", dock.position?.name)
        .apply { dock.panel?.let { panel -> put("panel", encodePage(panel)) } }
        .put("iconSizeDp", dock.iconSizeDp)
        .put("backgroundAlphaPercent", dock.backgroundAlphaPercent)
        .put("visualEffect", dock.visualEffect.name)
        .put("backgroundSizing", dock.backgroundSizing.name)
        .put("alignment", dock.alignment.name)
        .put("itemSpacingDp", dock.itemSpacingDp)
        .put("cornerRadiusDp", dock.cornerRadiusDp)
        .put("homeControlsSpacingDp", dock.homeControlsSpacingDp)
        .put("capacity", dock.capacity)
        .put("items", JSONArray(dock.items.map(::encodeLauncherItem)))

private fun JSONObject.toDock(
    defaults: DockModel,
    defaultGrid: GridDimensions,
): DockModel =
    DockModel(
        isEnabled = optBoolean("isEnabled", defaults.isEnabled),
        showNotificationCards = optBoolean("showNotificationCards", defaults.showNotificationCards),
        isExpandable = optBoolean("isExpandable", defaults.isExpandable),
        expandAffordance =
            optString("expandAffordance", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockExpandAffordance.valueOf(value) }.getOrNull() }
                ?: defaults.expandAffordance,
        position =
            optString("position", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockPosition.valueOf(value) }.getOrNull() }
                ?: defaults.position,
        panel =
            optJSONObject("panel")
                ?.let { panel -> runCatching { panel.toPage(defaultGrid = defaultGrid) }.getOrNull() }
                ?: defaults.panel,
        iconSizeDp = optInt("iconSizeDp", defaults.iconSizeDp),
        backgroundAlphaPercent =
            optInt(
                "backgroundAlphaPercent",
                defaults.backgroundAlphaPercent,
            ),
        visualEffect =
            optString("visualEffect", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockVisualEffect.valueOf(value) }.getOrNull() }
                ?: defaults.visualEffect,
        backgroundSizing =
            optString("backgroundSizing", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockBackgroundSizing.valueOf(value) }.getOrNull() }
                ?: defaults.backgroundSizing,
        alignment =
            optString("alignment", "")
                .takeIf(String::isNotBlank)
                ?.let { value -> runCatching { DockAlignment.valueOf(value) }.getOrNull() }
                ?: defaults.alignment,
        itemSpacingDp = optInt("itemSpacingDp", defaults.itemSpacingDp),
        cornerRadiusDp = optInt("cornerRadiusDp", defaults.cornerRadiusDp),
        homeControlsSpacingDp = optInt("homeControlsSpacingDp", defaults.homeControlsSpacingDp),
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
        .put("isPinned", page.isPinned)
        .put("generatedContentOverflowCount", page.generatedContentOverflowCount)
        .put("items", JSONArray(page.items.map(::encodeLauncherItem)))

private fun JSONArray.toPages(defaultGrid: GridDimensions): List<LauncherPage> =
    (0 until length())
        .mapNotNull { index ->
            optJSONObject(index)?.let { page ->
                runCatching { page.toPage(defaultGrid = defaultGrid) }.getOrNull()
            }
        }

private fun JSONObject.toPage(defaultGrid: GridDimensions): LauncherPage =
    optPageType().let { type ->
        LauncherPage(
            id = LauncherPageId(getString("id")),
            type = type,
            grid =
                GridDimensions(
                    columns = optInt("columns", defaultGrid.columns),
                    rows = optInt("rows", defaultGrid.rows),
                ),
            items = optJSONArray("items")?.toLauncherItems().orEmpty(),
            generatedContentOverflowCount = optInt("generatedContentOverflowCount", 0),
            isPinned = optBoolean("isPinned", false) && type is LauncherPageType.Generated,
        )
    }
