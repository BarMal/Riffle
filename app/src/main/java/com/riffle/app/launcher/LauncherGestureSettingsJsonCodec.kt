@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import com.riffle.core.domain.launcher.settings.toHomeGestureSettings
import org.json.JSONObject

fun encodeGestures(settings: GestureSettings): JSONObject =
    JSONObject()
        .put("homeGestures", encodeHomeGestures(settings.homeGestures))
        .put("homeSwipe", encodeHomeSwipeGestures(settings.homeSwipe))

fun JSONObject.toGestures(defaults: GestureSettings): GestureSettings =
    defaults.copy(
        homeGestures =
            optJSONObject("homeGestures")?.toHomeGestures(defaults.homeGestures)
                ?: optJSONObject("homeSwipe")?.toHomeSwipeGestures(defaults.homeSwipe)?.toHomeGestureSettings()
                ?: defaults.homeGestures,
    )

private fun encodeHomeGestures(settings: HomeGestureSettings): JSONObject =
    JSONObject().apply {
        HomeGesture.entries.forEach { gesture ->
            put(gesture.name, settings.actionFor(gesture).name)
            settings.launchTargetFor(gesture)?.let { target ->
                put("${gesture.name}Target", encodeLaunchTarget(target))
            }
        }
    }

private fun JSONObject.toHomeGestures(defaults: HomeGestureSettings): HomeGestureSettings =
    HomeGestureSettings(
        actions =
            HomeGesture.entries.associateWith { gesture ->
                optGestureAction(gesture.name, defaults.actionFor(gesture))
            },
        launchTargets =
            HomeGesture.entries.mapNotNull { gesture ->
                optJSONObject("${gesture.name}Target")?.toLaunchTarget()?.let { target -> gesture to target }
            }.toMap(),
    )

private fun encodeLaunchTarget(target: LauncherGestureLaunchTarget): JSONObject =
    when (target) {
        is LauncherGestureLaunchTarget.App ->
            JSONObject()
                .put("type", "app")
                .put("identity", encodeAppIdentity(target.identity))

        is LauncherGestureLaunchTarget.Shortcut ->
            JSONObject()
                .put("type", "shortcut")
                .put("id", target.shortcut.id.value)
                .put("identity", encodeAppIdentity(target.shortcut.appIdentity))
                .put("shortLabel", target.shortcut.shortLabel)
                .put("longLabel", target.shortcut.longLabel)
                .put("enabled", target.shortcut.enabled)
                .put("disabledMessage", target.shortcut.disabledMessage)
    }

private fun JSONObject.toLaunchTarget(): LauncherGestureLaunchTarget? =
    when (optString("type")) {
        "app" -> optJSONObject("identity")?.toAppIdentity()?.let(LauncherGestureLaunchTarget::App)
        "shortcut" ->
            optJSONObject("identity")?.toAppIdentity()?.let { identity ->
                optString("id").takeIf(String::isNotBlank)?.let { id ->
                    LauncherGestureLaunchTarget.Shortcut(
                        AppShortcut(
                            id = AppShortcutId(id),
                            appIdentity = identity,
                            shortLabel = optString("shortLabel"),
                            longLabel = optString("longLabel").takeIf(String::isNotBlank),
                            enabled = optBoolean("enabled", true),
                            disabledMessage = optString("disabledMessage").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }

        else -> null
    }

private fun encodeAppIdentity(identity: AppIdentity): JSONObject =
    JSONObject()
        .put("packageName", identity.packageName.value)
        .put("activityName", identity.activityName.value)
        .put("profileId", identity.profile.id.value)
        .put("profileType", identity.profile.type.name)

private fun JSONObject.toAppIdentity(): AppIdentity? =
    optString("packageName").takeIf(String::isNotBlank)?.let { packageName ->
        optString("activityName").takeIf(String::isNotBlank)?.let { activityName ->
            AppIdentity(
                packageName = AppPackageName(packageName),
                activityName = AppActivityName(activityName),
                profile =
                    AppProfile(
                        id = AppProfileId(optString("profileId", "personal")),
                        type =
                            runCatching { AppProfileType.valueOf(optString("profileType", "PERSONAL")) }
                                .getOrDefault(AppProfileType.PERSONAL),
                    ),
            )
        }
    }

private fun encodeHomeSwipeGestures(settings: HomeSwipeGestureSettings): JSONObject =
    JSONObject()
        .put("up", settings.up.name)
        .put("down", settings.down.name)
        .put("left", settings.left.name)
        .put("right", settings.right.name)

private fun JSONObject.toHomeSwipeGestures(defaults: HomeSwipeGestureSettings): HomeSwipeGestureSettings =
    HomeSwipeGestureSettings(
        up = optGestureAction("up", defaults.up),
        down = optGestureAction("down", defaults.down),
        left = optGestureAction("left", defaults.left),
        right = optGestureAction("right", defaults.right),
    )

private fun JSONObject.optGestureAction(
    name: String,
    default: LauncherGestureAction,
): LauncherGestureAction =
    runCatching { LauncherGestureAction.valueOf(optString(name)) }
        .getOrDefault(default)
