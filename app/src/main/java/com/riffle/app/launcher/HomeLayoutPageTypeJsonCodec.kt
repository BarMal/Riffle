package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.LauncherPageType
import org.json.JSONObject

internal fun JSONObject.optPageType(): LauncherPageType {
    val pageId = getString("id")
    return when (optString("type", "")) {
        PAGE_TYPE_HOME -> LauncherPageType.Home
        PAGE_TYPE_ALL_APPS -> LauncherPageType.AllApps
        PAGE_TYPE_GENERATED ->
            LauncherPageType.Generated(
                kind =
                    optString("generatedKind", "")
                        .takeIf(String::isNotBlank)
                        ?.let { value -> runCatching { GeneratedLauncherPageKind.valueOf(value) }.getOrNull() }
                        ?: GeneratedLauncherPageKind.APP,
            )

        else ->
            when {
                pageId.startsWith(LIBRARY_PAGE_ID_PREFIX) -> LauncherPageType.AllApps
                else -> LauncherPageType.Home
            }
    }
}

internal val LauncherPageType.typeName: String
    get() =
        when (this) {
            LauncherPageType.Home -> PAGE_TYPE_HOME
            LauncherPageType.AllApps -> PAGE_TYPE_ALL_APPS
            is LauncherPageType.Generated -> PAGE_TYPE_GENERATED
        }

private const val PAGE_TYPE_HOME = "Home"
private const val PAGE_TYPE_ALL_APPS = "AllApps"
private const val PAGE_TYPE_GENERATED = "Generated"
private const val LIBRARY_PAGE_ID_PREFIX = "library:"
