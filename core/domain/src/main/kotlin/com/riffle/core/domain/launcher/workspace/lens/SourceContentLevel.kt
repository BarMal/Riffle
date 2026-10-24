package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.SENSITIVE_ITEM_FIELDS
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * How much of a source's items any page may show (Settings > Privacy, design section 6.2). A per-source
 * **ceiling** intersected with the lens's own `project` at the projection step: it can only remove fields,
 * never widen a lens, and [SENSITIVE_ITEM_FIELDS] still always win for a SENSITIVE item. Identity, target and
 * privacy are never removed, so taps, counts and lazy keys keep working.
 *
 * [storedId] is the persisted contract: never rename one, only add new levels.
 */
enum class SourceContentLevel(val storedId: String) {
    /** Everything the lens projects (today's behaviour, the default). */
    SHOW("show"),

    /** Title and icon only: the body, the subtitle (a media artist, an event's location) and the image are dropped. */
    HIDE_BODY("hide_body"),

    /**
     * No text at all: every item is drawn as "Hidden content" with its icon, time and group (so counts per app
     * remain). The same fields a SENSITIVE item keeps.
     */
    HIDE_TEXT("hide_text"),
    ;

    /** The fields an item of a source at this level may keep, whatever its lens projects. */
    val ceiling: Set<ItemField>
        get() =
            when (this) {
                SHOW -> ItemField.ALL
                HIDE_BODY -> ItemField.ALL - HIDDEN_BODY_FIELDS
                HIDE_TEXT -> ItemField.ALL - SENSITIVE_ITEM_FIELDS
            }

    companion object {
        val DEFAULT: SourceContentLevel = SHOW

        /** The level stored as [id], or null for an unknown or blank id (callers fall back to [DEFAULT]). */
        fun fromStoredId(id: String): SourceContentLevel? = entries.firstOrNull { it.storedId == id }

        private val HIDDEN_BODY_FIELDS = setOf(ItemField.BODY, ItemField.SUBTITLE, ItemField.IMAGE)
    }
}

/**
 * The content level of every source; a source without an entry is [SourceContentLevel.DEFAULT]. Part of the
 * [LensEvaluationContext], so it applies to every lens (flat, grouped, sorted, search) at the one projection step.
 */
data class SourceContentLevels(val levels: Map<SourceId, SourceContentLevel> = emptyMap()) {
    fun levelOf(id: SourceId): SourceContentLevel = levels[id] ?: SourceContentLevel.DEFAULT

    fun ceilingOf(id: SourceId): Set<ItemField> = levelOf(id).ceiling

    /** These levels with [id] set to [level]; [SourceContentLevel.DEFAULT] removes the entry. */
    fun with(
        id: SourceId,
        level: SourceContentLevel,
    ): SourceContentLevels =
        SourceContentLevels(if (level == SourceContentLevel.DEFAULT) levels - id else levels + (id to level))

    companion object {
        /** Every source at [SourceContentLevel.SHOW]: today's behaviour. */
        val ALL_SHOWN = SourceContentLevels()
    }
}
