package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.home.restacked
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.json.JSONArray
import org.json.JSONObject

fun encodeWidget(widget: WidgetItem): JSONObject =
    JSONObject()
        .put("type", "widget")
        .put("id", widget.id.value)
        .put("appWidgetId", widget.appWidgetId.value)
        .put("label", widget.label)
        .put("column", widget.placement?.cell?.column)
        .put("row", widget.placement?.cell?.row)
        .put("columns", widget.placement?.span?.columns)
        .put("rows", widget.placement?.span?.rows)
        .put("minColumns", widget.resizeConstraints.minSpan.columns)
        .put("minRows", widget.resizeConstraints.minSpan.rows)
        .put("maxColumns", widget.resizeConstraints.maxSpan?.columns)
        .put("maxRows", widget.resizeConstraints.maxSpan?.rows)
        .put("supportsHorizontalResize", widget.resizeConstraints.supportsHorizontalResize)
        .put("supportsVerticalResize", widget.resizeConstraints.supportsVerticalResize)
        .put("glance", widget.glance?.storedId)
        .also { json -> widget.provider?.let { json.put("provider", encodeWidgetProvider(it)) } }
        .also { json ->
            // A stack (ADR 0003, Amendment 14) adds two keys; a plain widget is stored exactly as before.
            if (widget.isStack) {
                json.put("stack", JSONArray(widget.stack.map(::encodeStackedWidget)))
                json.put("shown", widget.shownIndex)
            }
        }

private fun encodeStackedWidget(member: StackedWidget): JSONObject =
    JSONObject()
        .put("appWidgetId", member.appWidgetId.value)
        .put("label", member.label)
        .put("minColumns", member.resizeConstraints.minSpan.columns)
        .put("minRows", member.resizeConstraints.minSpan.rows)
        .put("maxColumns", member.resizeConstraints.maxSpan?.columns)
        .put("maxRows", member.resizeConstraints.maxSpan?.rows)
        .put("supportsHorizontalResize", member.resizeConstraints.supportsHorizontalResize)
        .put("supportsVerticalResize", member.resizeConstraints.supportsVerticalResize)
        .put("glance", member.glance?.storedId)
        .also { json -> member.provider?.let { json.put("provider", encodeWidgetProvider(it)) } }

/** The provider a placeholder is set up from again; absent for a widget that never had one recorded. */
private fun encodeWidgetProvider(provider: WidgetProviderIdentity): JSONObject =
    JSONObject()
        .put("package", provider.packageName.value)
        .put("class", provider.className.value)
        .put("profile", provider.profile.id.value)
        .put("profileType", provider.profile.type.name)

private fun JSONObject.toWidgetProviderOrNull(): WidgetProviderIdentity? =
    optJSONObject("provider")?.let { json ->
        runCatching {
            WidgetProviderIdentity(
                packageName = AppPackageName(json.getString("package")),
                className = WidgetProviderClassName(json.getString("class")),
                profile =
                    AppProfile(
                        id = AppProfileId(json.getString("profile")),
                        type = AppProfileType.valueOf(json.getString("profileType")),
                    ),
            )
        }.getOrNull()
    }

private fun JSONObject.toStackedWidget(): StackedWidget =
    StackedWidget(
        appWidgetId = HostedWidgetId(getInt("appWidgetId")),
        label = optString("label", ""),
        resizeConstraints = toResizeConstraints(),
        glance = GlanceWidgets.kindOfStoredId(optString("glance").takeIf { it.isNotEmpty() }),
        provider = toWidgetProviderOrNull(),
    )

private fun JSONObject.toResizeConstraints(): WidgetResizeConstraints =
    WidgetResizeConstraints(
        minSpan = GridSpan(optInt("minColumns", 1), optInt("minRows", 1)),
        maxSpan =
            optInt("maxColumns", 0).takeIf { it > 0 }?.let { columns ->
                GridSpan(columns, optInt("maxRows", 0).coerceAtLeast(1))
            },
        supportsHorizontalResize = optBoolean("supportsHorizontalResize", true),
        supportsVerticalResize = optBoolean("supportsVerticalResize", true),
    )

/** The members of a stored stack; fewer than two readable ones is no stack (the widget reads as a plain one). */
private fun JSONObject.toStackOrEmpty(): List<StackedWidget> =
    optJSONArray("stack")
        ?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching { array.getJSONObject(index).toStackedWidget() }.getOrNull()
            }
        }
        ?.takeIf { it.size > 1 }
        .orEmpty()

fun JSONObject.toWidget(): WidgetItem =
    WidgetItem(
        id = LauncherItemId(getString("id")),
        appWidgetId = HostedWidgetId(getInt("appWidgetId")),
        label = optString("label", ""),
        resizeConstraints = toResizeConstraints(),
        placement = toPlacementOrNull(),
        glance = GlanceWidgets.kindOfStoredId(optString("glance").takeIf { it.isNotEmpty() }),
        stack = toStackOrEmpty(),
        shownIndex = optInt("shown", 0).coerceAtLeast(0),
        provider = toWidgetProviderOrNull(),
    ).let { widget ->
        // A stack's members are the truth: the item's own fields are the first member's, whatever else was stored.
        if (widget.isStack) widget.restacked(widget.stack, widget.shownIndex) else widget.copy(shownIndex = 0)
    }
