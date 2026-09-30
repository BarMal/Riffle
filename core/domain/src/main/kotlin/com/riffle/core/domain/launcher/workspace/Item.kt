package com.riffle.core.domain.launcher.workspace

/**
 * The one universal shape every [ItemSource] emits (docs/product/workspaces-sources-lenses.md).
 *
 * Ancestor: `LauncherCard`. Like it, an [Item] is transient and non-durable: its text, icon, image,
 * actions and [ext] may be sensitive, so it must never be serialized into layout, settings, backup or
 * diagnostics. Nothing in this package encodes an [Item]; only lenses and workspaces are stored.
 * Sensitive fields are redacted at the lens `project` step (see [ItemPrivacy]).
 */
data class Item(
    val id: ItemId,
    val sourceId: SourceId,
    val target: ItemTarget,
    val title: String? = null,
    val subtitle: String? = null,
    val body: String? = null,
    val icon: ItemImageHandle? = null,
    val image: ItemImageHandle? = null,
    val timeEpochMillis: Long? = null,
    val groupKey: String? = null,
    val groupLabel: String? = null,
    val actions: List<ItemAction> = emptyList(),
    val privacy: ItemPrivacy = ItemPrivacy.VISIBLE,
    val ext: Map<ItemExtKey, ItemExtValue> = emptyMap(),
) {
    init {
        require(timeEpochMillis == null || timeEpochMillis >= 0L) { "Item time cannot be negative." }
    }
}

@JvmInline
value class ItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Item ids must not be blank." }
    }
}

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Source ids must not be blank." }
    }
}

/**
 * Lazy image reference. Resolving it to pixels is a platform concern and must never happen on the
 * main thread; the domain only carries the opaque [key].
 */
@JvmInline
value class ItemImageHandle(val key: String) {
    init {
        require(key.isNotBlank()) { "Image handle keys must not be blank." }
    }
}

/** What a tap launches. Values are opaque to the domain; a platform launcher resolves them. */
sealed interface ItemTarget {
    data class App(val packageName: String, val profileId: String? = null) : ItemTarget

    data class Shortcut(val packageName: String, val shortcutId: String, val profileId: String? = null) : ItemTarget

    data class DeepLink(val uri: String) : ItemTarget

    /** A source-owned intent, referenced by an opaque token the source can resolve. */
    data class Intent(val token: String) : ItemTarget

    /** Content with no launch behaviour (e.g. a header-only item). */
    data object None : ItemTarget
}

sealed interface ItemAction {
    val id: String

    data class Open(override val id: String = "open") : ItemAction

    data class Dismiss(override val id: String = "dismiss") : ItemAction

    data class Reply(override val id: String = "reply") : ItemAction

    data class Snooze(override val id: String = "snooze") : ItemAction

    data class Custom(override val id: String, val label: String) : ItemAction
}

enum class ItemPrivacy {
    VISIBLE,
    SENSITIVE,
}

/**
 * Namespaced extras key, `namespace.name` (e.g. `media.artist`). Namespacing keeps source-specific
 * structure from leaking into the universal shape.
 */
@JvmInline
value class ItemExtKey(val value: String) {
    init {
        require(NAMESPACED.matches(value)) { "Ext keys must look like 'namespace.name': $value" }
    }

    private companion object {
        val NAMESPACED = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
    }
}

/** Extras are primitives only, so expressions cannot depend on source-specific classes. */
sealed interface ItemExtValue {
    data class Text(val value: String) : ItemExtValue

    data class Number(val value: Long) : ItemExtValue

    data class Flag(val value: Boolean) : ItemExtValue
}

/** Fields of [Item] a lens may expose and an expression may require or use. */
enum class ItemField {
    TITLE,
    SUBTITLE,
    BODY,
    ICON,
    IMAGE,
    TIME,
    GROUP,
    ACTIONS,
    EXT,
    ;

    companion object {
        /** Always available: identity and launch target are never projected away. */
        val ALL: Set<ItemField> = entries.toSet()
    }
}

/** Fields that carry user content and are stripped from [ItemPrivacy.SENSITIVE] items on projection. */
val SENSITIVE_ITEM_FIELDS: Set<ItemField> =
    setOf(ItemField.TITLE, ItemField.SUBTITLE, ItemField.BODY, ItemField.IMAGE, ItemField.ACTIONS, ItemField.EXT)
