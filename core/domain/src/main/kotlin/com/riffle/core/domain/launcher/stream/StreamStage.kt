package com.riffle.core.domain.launcher.stream

/** How wide the Stage rail draws: with a thumbnail and a name per stage, or as icons only in a narrow pane. */
enum class StreamRailMode { FULL, ICONS }

/**
 * The Stage view's model (ADR 0003, Views; after Stage Manager on macOS): the [selected] stage's stack fills the
 * area, and the [rail] holds a live thumbnail of every other page. Pure, so the order is tested.
 */
data class StreamStageModel(
    val selected: StreamPage?,
    val rail: List<StreamPage>,
)

object StreamStage {
    /** A pane narrower than this many dp collapses the rail to icons only (a split's Stream pane on a foldable). */
    const val RAIL_COLLAPSE_BELOW_DP = 560

    fun railMode(paneWidthDp: Int): StreamRailMode =
        if (paneWidthDp < RAIL_COLLAPSE_BELOW_DP) StreamRailMode.ICONS else StreamRailMode.FULL

    /**
     * The stage for [pages] with [selectedKey] in the centre. A selection that has gone resolves to the nearest page
     * ([StreamPageNavigation.currentAfter], given the previous keys). The rail lists the other pages with the ones the
     * user brought to the centre most recently first ([recentKeys], newest first, as Stage Manager keeps the stage you
     * just left at the top), then the rest in page order: sections as laid out, and within Notifications and Feeds the
     * app or feed with the newest activity first, as the planner delivers them.
     */
    fun model(
        pages: List<StreamPage>,
        selectedKey: String?,
        recentKeys: List<String> = emptyList(),
        previousKeys: List<String> = pages.map { it.key },
    ): StreamStageModel {
        val keys = pages.map { it.key }
        val key = StreamPageNavigation.currentAfter(previousKeys, selectedKey, keys)
        val selected = pages.firstOrNull { it.key == key }
        val others = pages.filter { it.key != key }
        val byKey = others.associateBy { it.key }
        val recent = recentKeys.distinct().mapNotNull { byKey[it] }
        val rail = recent + others.filter { page -> recent.none { it.key == page.key } }
        return StreamStageModel(selected, rail)
    }

    /** The recent keys after the user brought [next] to the centre from [previous]: the stage just left comes first. */
    fun recentAfterSelecting(
        recentKeys: List<String>,
        previous: String?,
        next: String,
    ): List<String> = (listOfNotNull(previous) + recentKeys).filter { it != next }.distinct().take(MAX_RECENT)

    private const val MAX_RECENT = 12
}

/**
 * How many pages share a wide pager: two side by side when the Stream has a whole wide screen, one otherwise (ADR
 * 0003, Views). A split's narrow Stream pane, a phone and a tablet in portrait show one.
 */
object StreamPagesPerView {
    const val TWO_UP_FROM_DP = 840

    fun forWidth(
        widthDp: Int,
        wideView: Boolean,
    ): Int = if (wideView && widthDp >= TWO_UP_FROM_DP) 2 else 1
}
