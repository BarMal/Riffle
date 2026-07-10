package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.json.JSONArray
import org.json.JSONObject

fun encodeLauncherItem(item: LauncherItem): JSONObject =
    when (item) {
        is AppShortcutItem -> encodeShortcut(item)
        is FolderItem -> encodeFolder(item)
        is WidgetItem -> encodeWidget(item)
    }

fun encodeShortcut(shortcut: AppShortcutItem): JSONObject =
    JSONObject()
        .put("type", "shortcut")
        .put("id", shortcut.id.value)
        .put("label", shortcut.label)
        .put("appShortcutId", shortcut.appShortcutId?.value)
        .put("packageName", shortcut.appIdentity.packageName.value)
        .put("activityName", shortcut.appIdentity.activityName.value)
        .put("column", shortcut.placement?.cell?.column)
        .put("row", shortcut.placement?.cell?.row)
        .put("columns", shortcut.placement?.span?.columns)
        .put("rows", shortcut.placement?.span?.rows)

fun JSONArray.toLauncherItems(): List<LauncherItem> =
    (0 until length())
        .map { index -> getJSONObject(index) }
        .map { item -> item.toLauncherItem() }

fun JSONArray.toShortcuts(): List<AppShortcutItem> =
    (0 until length())
        .map { index -> getJSONObject(index) }
        .map { shortcut -> shortcut.toShortcut() }

private fun encodeFolder(folder: FolderItem): JSONObject =
    JSONObject()
        .put("type", "folder")
        .put("id", folder.id.value)
        .put("label", folder.label)
        .put("items", JSONArray(folder.items.map(::encodeShortcut)))
        .put("column", folder.placement?.cell?.column)
        .put("row", folder.placement?.cell?.row)
        .put("columns", folder.placement?.span?.columns)
        .put("rows", folder.placement?.span?.rows)

private fun JSONObject.toLauncherItem(): LauncherItem =
    when (optString("type", "shortcut")) {
        "folder" -> toFolder()
        "widget" -> toWidget()
        else -> toShortcut()
    }

private fun JSONObject.toFolder(): FolderItem =
    FolderItem(
        id = LauncherItemId(getString("id")),
        label = getString("label"),
        items = getJSONArray("items").toShortcuts(),
        placement = toPlacementOrNull(),
    )

internal fun JSONObject.toShortcut(): AppShortcutItem =
    AppShortcutItem(
        id = LauncherItemId(getString("id")),
        appIdentity =
            AppIdentity(
                packageName = AppPackageName(getString("packageName")),
                activityName = AppActivityName(getString("activityName")),
                profile = AppProfile.personal(),
            ),
        label = getString("label"),
        appShortcutId = optAppShortcutId(),
        placement = toPlacementOrNull(),
    )

private fun JSONObject.optAppShortcutId(): AppShortcutId? =
    optString("appShortcutId", "")
        .takeIf(String::isNotBlank)
        ?.let(::AppShortcutId)

fun JSONObject.toPlacementOrNull(): GridPlacement? =
    when {
        isNull("column") || isNull("row") -> null
        else ->
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
            )
    }
