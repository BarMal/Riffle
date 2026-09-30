package com.riffle.core.domain.launcher.workspace

enum class ExpressionKind {
    ICON_ROW,
    ICON_GRID,
    LIST,
    INDEX,
    CARD,
    CARD_STACK,
    CATEGORIES,
    ALPHA_LIST,
}

/** Gesture axes a container can own. The arbitration layer consumes these declarations. */
enum class GestureAxis {
    VERTICAL_SCROLL,
    HORIZONTAL_SCROLL,
    HORIZONTAL_PAGER,
}

/**
 * What an expression needs from a lens. [requires] fields must be projected; [uses] are optional
 * enrichments; [accepts] are the result shapes it can draw; [axes] are the gesture axes it owns.
 */
data class ExpressionDescriptor(
    val kind: ExpressionKind,
    val requires: Set<ItemField>,
    val uses: Set<ItemField>,
    val accepts: Set<ResultShape>,
    val axes: Set<GestureAxis>,
)

/** Built-in descriptors. Rendering lives in the UI layer; this is only the contract. */
object ExpressionCatalog {
    private val descriptors: Map<ExpressionKind, ExpressionDescriptor> =
        listOf(
            ExpressionDescriptor(
                ExpressionKind.ICON_ROW,
                requires = setOf(ItemField.ICON),
                uses = setOf(ItemField.TITLE),
                accepts = setOf(ResultShape.FLAT, ResultShape.SINGLE),
                axes = setOf(GestureAxis.HORIZONTAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.ICON_GRID,
                requires = setOf(ItemField.ICON),
                uses = setOf(ItemField.TITLE),
                accepts = setOf(ResultShape.FLAT),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.LIST,
                requires = setOf(ItemField.TITLE),
                uses = setOf(ItemField.ICON, ItemField.SUBTITLE, ItemField.TIME),
                accepts = setOf(ResultShape.FLAT),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.INDEX,
                requires = setOf(ItemField.TITLE),
                uses = setOf(ItemField.BODY, ItemField.TIME, ItemField.ICON),
                accepts = setOf(ResultShape.FLAT, ResultShape.GROUPED),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.CARD,
                requires = setOf(ItemField.TITLE),
                uses = setOf(ItemField.SUBTITLE, ItemField.BODY, ItemField.IMAGE, ItemField.ICON, ItemField.ACTIONS),
                accepts = setOf(ResultShape.SINGLE),
                axes = emptySet(),
            ),
            ExpressionDescriptor(
                ExpressionKind.CARD_STACK,
                requires = setOf(ItemField.TITLE),
                uses =
                    setOf(
                        ItemField.SUBTITLE,
                        ItemField.BODY,
                        ItemField.IMAGE,
                        ItemField.ICON,
                        ItemField.ACTIONS,
                        ItemField.TIME,
                    ),
                accepts = setOf(ResultShape.FLAT),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.CATEGORIES,
                requires = setOf(ItemField.ICON, ItemField.TITLE, ItemField.GROUP),
                uses = emptySet(),
                accepts = setOf(ResultShape.GROUPED),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
            ExpressionDescriptor(
                ExpressionKind.ALPHA_LIST,
                requires = setOf(ItemField.TITLE),
                uses = setOf(ItemField.ICON),
                accepts = setOf(ResultShape.FLAT),
                axes = setOf(GestureAxis.VERTICAL_SCROLL),
            ),
        ).associateBy { it.kind }

    val all: List<ExpressionDescriptor> = ExpressionKind.entries.map(::descriptorFor)

    fun descriptorFor(kind: ExpressionKind): ExpressionDescriptor =
        checkNotNull(descriptors[kind]) { "No descriptor declared for $kind." }
}
