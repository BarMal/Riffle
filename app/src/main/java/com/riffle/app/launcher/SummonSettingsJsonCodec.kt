package com.riffle.app.launcher

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.summon.SUMMON_ICON_SIZE_DP_RANGE
import com.riffle.core.domain.launcher.summon.SummonCollection
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonKeepGesture
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonTimings
import org.json.JSONArray
import org.json.JSONObject

// The `kind` strings and the glance widgets' stored ids are stored contracts: never rename one, only add.
private const val KIND_APP = "app"
private const val KIND_FOLDER = "folder"
private const val KIND_GLANCE = "glance"
private const val KIND_FAVOURITES = "favourites"
private const val KIND_COLLECTION = "collection"
private const val KIND_NONE = "none"
private const val KIND_CLOCK = "clock"
private const val KIND_CLOCK_AND_DATE = "clock_and_date"
private const val KIND_HOME_WIDGET = "home_widget"

internal fun encodeSummonSettings(settings: SummonSettings): JSONObject =
    JSONObject()
        .put("slotCount", settings.slotCount)
        .put("slots", JSONArray().apply { settings.slots.forEach { put(encodeSummonSlot(it)) } })
        .put("collections", JSONArray().apply { settings.collections.forEach { put(encodeCollection(it)) } })
        .put("header", encodeSummonHeader(settings.header))
        .put("showHints", settings.showHints)
        .put("iconSizeDp", settings.iconSizeDp)
        .put("keepGesture", settings.keepGesture.name)
        .put(
            "timings",
            JSONObject()
                .put("armMillis", settings.timings.armMillis)
                .put("previewMillis", settings.timings.previewMillis)
                .put("acceptMillis", settings.timings.acceptMillis)
                .put("slideMillis", settings.timings.slideMillis),
        )

/** Unknown slot kinds (from a newer build) are skipped rather than failing the whole ring. */
internal fun JSONObject.toSummonSettings(defaults: SummonSettings): SummonSettings {
    val slotArray = optJSONArray("slots")
    val slots =
        if (slotArray == null) {
            defaults.slots
        } else {
            (0 until slotArray.length()).mapNotNull { index -> slotArray.optJSONObject(index)?.toSummonSlot() }
        }
    val timings = optJSONObject("timings")
    val collectionArray = optJSONArray("collections")
    return SummonSettings(
        slots = slots,
        collections =
            collectionArray
                ?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toCollection() } }
                ?: defaults.collections,
        header = optJSONObject("header")?.toSummonHeader() ?: defaults.header,
        showHints = optBoolean("showHints", defaults.showHints),
        iconSizeDp = optInt("iconSizeDp", defaults.iconSizeDp).coerceIn(SUMMON_ICON_SIZE_DP_RANGE),
        keepGesture =
            SummonKeepGesture.entries.firstOrNull { it.name == optString("keepGesture") } ?: defaults.keepGesture,
        timings =
            SummonTimings(
                armMillis = timings?.optInt("armMillis", defaults.timings.armMillis) ?: defaults.timings.armMillis,
                previewMillis =
                    timings?.optInt("previewMillis", defaults.timings.previewMillis) ?: defaults.timings.previewMillis,
                acceptMillis =
                    timings?.optInt("acceptMillis", defaults.timings.acceptMillis) ?: defaults.timings.acceptMillis,
                slideMillis =
                    timings?.optInt("slideMillis", defaults.timings.slideMillis) ?: defaults.timings.slideMillis,
            ).clamped(),
    ).withSlotCount(optInt("slotCount", defaults.slotCount))
}

private fun encodeSummonSlot(slot: SummonSlot): JSONObject =
    when (slot) {
        is SummonSlot.App -> JSONObject().put("kind", KIND_APP).put("identity", encodeAppIdentity(slot.identity))
        is SummonSlot.Folder -> JSONObject().put("kind", KIND_FOLDER).put("id", slot.folderId.value)
        is SummonSlot.Glance -> JSONObject().put("kind", KIND_GLANCE).put("widget", slot.kind.storedId)
        SummonSlot.Favourites -> JSONObject().put("kind", KIND_FAVOURITES)
        is SummonSlot.Collection -> JSONObject().put("kind", KIND_COLLECTION).put("id", slot.id.value)
    }

private fun encodeCollection(collection: SummonCollection): JSONObject =
    JSONObject()
        .put("id", collection.id.value)
        .put("name", collection.name)
        .put("items", JSONArray().apply { collection.items.forEach { put(encodeSummonSlot(it)) } })
        .apply {
            collection.icon?.let { put("iconPack", it.packPackage).put("drawable", it.drawableName) }
        }

private fun JSONObject.toCollection(): SummonCollection? {
    val id = optString("id").takeIf(String::isNotBlank) ?: return null
    val items = optJSONArray("items")
    val icon =
        optString("iconPack").takeIf(String::isNotBlank)?.let { pack ->
            optString("drawable").takeIf(String::isNotBlank)?.let { IconPackDrawableRef(pack, it) }
        }
    return SummonCollection(
        id = SummonCollectionId(id),
        name = optString("name"),
        items =
            items?.let {
                    array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toSummonSlot() }
            }.orEmpty(),
        icon = icon,
    )
}

private fun JSONObject.toSummonSlot(): SummonSlot? =
    when (optString("kind")) {
        KIND_APP -> optJSONObject("identity")?.toAppIdentityOrNull()?.let(SummonSlot::App)
        KIND_FOLDER -> optString("id").takeIf(String::isNotBlank)?.let { SummonSlot.Folder(LauncherItemId(it)) }
        KIND_GLANCE -> glanceKind(optString("widget"))?.let(SummonSlot::Glance)
        KIND_FAVOURITES -> SummonSlot.Favourites
        KIND_COLLECTION ->
            optString(
                "id",
            ).takeIf(String::isNotBlank)?.let { SummonSlot.Collection(SummonCollectionId(it)) }
        else -> null
    }

private fun encodeSummonHeader(header: SummonHeader): JSONObject =
    when (header) {
        SummonHeader.None -> JSONObject().put("kind", KIND_NONE)
        SummonHeader.Clock -> JSONObject().put("kind", KIND_CLOCK)
        SummonHeader.ClockAndDate -> JSONObject().put("kind", KIND_CLOCK_AND_DATE)
        is SummonHeader.Glance -> JSONObject().put("kind", KIND_GLANCE).put("widget", header.kind.storedId)
        is SummonHeader.HomeWidget -> JSONObject().put("kind", KIND_HOME_WIDGET).put("id", header.itemId.value)
    }

private fun JSONObject.toSummonHeader(): SummonHeader? =
    when (optString("kind")) {
        KIND_NONE -> SummonHeader.None
        KIND_CLOCK -> SummonHeader.Clock
        KIND_CLOCK_AND_DATE -> SummonHeader.ClockAndDate
        KIND_GLANCE -> glanceKind(optString("widget"))?.let(SummonHeader::Glance)
        KIND_HOME_WIDGET ->
            optString("id").takeIf(String::isNotBlank)?.let { SummonHeader.HomeWidget(LauncherItemId(it)) }
        else -> null
    }

private fun glanceKind(storedId: String): GlanceWidgetKind? =
    GlanceWidgetKind.entries.firstOrNull {
        it.storedId == storedId
    }
