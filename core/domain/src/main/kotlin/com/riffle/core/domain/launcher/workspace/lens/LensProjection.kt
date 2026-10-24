package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SENSITIVE_ITEM_FIELDS

/**
 * Projection and privacy redaction. This is the only place an [Item] is redacted; Expressions only ever
 * see the output. Identity (`id`, `sourceId`, `target`) and `privacy` itself are always retained.
 *
 * Three limits meet here, each of which can only remove fields: the lens's `project`, the item's source
 * [SourceContentLevel] ceiling (`levels`) and, for a SENSITIVE item, [SENSITIVE_ITEM_FIELDS].
 */
internal object LensProjection {
    /** The fields that survive for [item]: the lens projection within its source's ceiling, minus sensitive fields. */
    fun visibleFields(
        item: Item,
        project: Set<ItemField>,
        levels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
    ): Set<ItemField> {
        val ceiled = project intersect levels.ceilingOf(item.sourceId)
        return if (item.privacy == ItemPrivacy.SENSITIVE) ceiled - SENSITIVE_ITEM_FIELDS else ceiled
    }

    fun redact(
        item: Item,
        project: Set<ItemField>,
        levels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
    ): Item {
        val keep = visibleFields(item, project, levels)
        return item.copy(
            title = item.title.takeIf { ItemField.TITLE in keep },
            subtitle = item.subtitle.takeIf { ItemField.SUBTITLE in keep },
            body = item.body.takeIf { ItemField.BODY in keep },
            icon = item.icon.takeIf { ItemField.ICON in keep },
            image = item.image.takeIf { ItemField.IMAGE in keep },
            timeEpochMillis = item.timeEpochMillis.takeIf { ItemField.TIME in keep },
            groupKey = item.groupKey.takeIf { ItemField.GROUP in keep },
            groupLabel = item.groupLabel.takeIf { ItemField.GROUP in keep },
            actions = if (ItemField.ACTIONS in keep) item.actions else emptyList(),
            ext = if (ItemField.EXT in keep) item.ext else emptyMap(),
        )
    }
}
