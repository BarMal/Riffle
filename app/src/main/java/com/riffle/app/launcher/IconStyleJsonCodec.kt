package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.icons.AppIconOverride
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import org.json.JSONArray
import org.json.JSONObject

/**
 * The `icons` section of the settings blob (Settings > Appearance > Icons). Blobs written before it existed have no
 * section and read as the defaults: every icon as the platform draws it. A per-app override is stored by reference
 * (the pack's package name and a drawable name), never as a bitmap, and kept even when its pack is not installed.
 */
internal fun encodeIconStyle(style: IconStyleSettings): JSONObject =
    JSONObject()
        .put("shape", style.shape.storedId)
        .put("legacyIcons", style.legacyIcons.storedId)
        .put("themedIcons", style.themedIcons)
        .put("themeAllIcons", style.themeAllIcons)
        .put("iconPack", style.iconPack ?: JSONObject.NULL)
        .put("overrides", JSONArray().also { array -> style.overrides.forEach { array.put(encodeOverride(it)) } })

private fun encodeOverride(entry: Map.Entry<AppIdentity, AppIconOverride>): JSONObject {
    val (identity, override) = entry
    return JSONObject()
        .put("package", identity.packageName.value)
        .put("activity", identity.activityName.value)
        .put("profileId", identity.profile.id.value)
        .put("profileType", identity.profile.type.name)
        .also { json ->
            override.icon?.let { json.put("iconPack", it.packPackage).put("drawable", it.drawableName) }
            override.label?.let { json.put("label", it) }
        }
}

internal fun JSONObject.toIconStyle(defaults: IconStyleSettings): IconStyleSettings =
    IconStyleSettings(
        shape = IconShape.decode(optString("shape")),
        legacyIcons = LegacyIconHandling.decode(optString("legacyIcons")),
        themedIcons = optBoolean("themedIcons", defaults.themedIcons),
        themeAllIcons = optBoolean("themeAllIcons", defaults.themeAllIcons),
        iconPack = if (isNull("iconPack")) null else optString("iconPack").takeIf(String::isNotBlank),
        overrides = decodeOverrides(),
    )

/** An entry that is not an object, or lacks its app, is skipped; the rest are kept. Nothing here throws. */
private fun JSONObject.decodeOverrides(): Map<AppIdentity, AppIconOverride> {
    val array = optJSONArray("overrides") ?: return emptyMap()
    return (0 until array.length())
        .mapNotNull { index -> array.optJSONObject(index)?.toOverrideEntry() }
        .toMap(LinkedHashMap())
}

private fun JSONObject.toOverrideEntry(): Pair<AppIdentity, AppIconOverride>? {
    val identity = toOverrideIdentity()
    val icon =
        optString("iconPack").takeIf(String::isNotBlank)?.let { pack ->
            optString("drawable").takeIf(String::isNotBlank)?.let { IconPackDrawableRef(pack, it) }
        }
    val override = AppIconOverride(icon = icon, label = optString("label").takeIf(String::isNotBlank))
    return if (identity == null || override.isEmpty) null else identity to override
}

@Suppress("ReturnCount")
private fun JSONObject.toOverrideIdentity(): AppIdentity? {
    val packageName = optString("package").takeIf(String::isNotBlank) ?: return null
    val activity = optString("activity").takeIf(String::isNotBlank) ?: return null
    val profile =
        optString("profileId").takeIf(String::isNotBlank)?.let { id ->
            val type =
                runCatching { AppProfileType.valueOf(optString("profileType")) }
                    .getOrDefault(AppProfileType.PERSONAL)
            AppProfile(AppProfileId(id), type)
        } ?: AppProfile.personal()
    return AppIdentity(AppPackageName(packageName), AppActivityName(activity), profile)
}
