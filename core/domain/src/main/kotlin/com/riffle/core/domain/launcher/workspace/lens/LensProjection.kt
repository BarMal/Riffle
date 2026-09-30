package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SENSITIVE_ITEM_FIELDS

/**
 * Projection and privacy redaction. This is the only place an [Item] is redacted; Expressions only ever
 * see the output. Identity (`id`, `sourceId`, `target`) and `privacy` itself are always retained.
 */
internal object LensProjection {
    /** The fields that survive for [item]: the lens projection, minus sensitive fields for SENSITIVE items. */
    fun visibleFields(
        item: Item,
        project: Set<ItemField>,
    ): Set<ItemField> = if (item.privacy == ItemPrivacy.SENSITIVE) project - SENSITIVE_ITEM_FIELDS else project

    fun redact(
        item: Item,
        project: Set<ItemField>,
    ): Item {
        val keep = visibleFields(item, project)
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
