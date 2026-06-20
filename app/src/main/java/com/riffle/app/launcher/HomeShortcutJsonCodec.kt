package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.json.JSONArray
import org.json.JSONObject

fun encodeShortcut(shortcut: AppShortcutItem): JSONObject =
    JSONObject()
        .put("id", shortcut.id.value)
        .put("label", shortcut.label)
        .put("packageName", shortcut.appIdentity.packageName.value)
        .put("activityName", shortcut.appIdentity.activityName.value)
        .put("column", shortcut.placement?.cell?.column)
        .put("row", shortcut.placement?.cell?.row)
        .put("columns", shortcut.placement?.span?.columns)
        .put("rows", shortcut.placement?.span?.rows)

fun JSONArray.toShortcuts(): List<AppShortcutItem> =
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
        placement = toPlacementOrNull(),
    )

private fun JSONObject.toPlacementOrNull(): GridPlacement? =
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
