package com.riffle.core.domain.launcher.glance

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.EventKind
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * Riffle's own widgets (ADR 0003, Glance widgets): a fixed catalogue, placed from the widget picker like any other.
 *
 * A glance widget is a `PoolWidget` whose provider is [GlanceWidgets.identity] (Riffle's own package and a
 * `glance.` class name) and whose host id is always null: nothing is bound through the platform's widget host, the
 * app draws it. [storedClass] and [storedId] are stored contracts (the class name is in every pool blob): never
 * rename one, only add kinds. [section] is the Stream section the widget reads (through the shared provider) and
 * the one tapping its body opens.
 *
 * Spans are in grid cells. A widget below [fullMinSpan] draws its compact layout ([GlanceLayout]).
 */
@Suppress("LongParameterList")
enum class GlanceWidgetKind(
    val storedId: String,
    val storedClass: String,
    val section: StreamSection,
    val defaultLabel: String,
    val minSpan: GridSpan,
    val defaultSpan: GridSpan,
    val maxSpan: GridSpan,
    val fullMinSpan: GridSpan,
) {
    NOW_PLAYING(
        storedId = "now-playing",
        storedClass = "glance.NowPlaying",
        section = StreamSection.NOW,
        defaultLabel = "Now playing",
        minSpan = GridSpan(2, 1),
        defaultSpan = GridSpan(4, 2),
        maxSpan = GridSpan(4, 4),
        fullMinSpan = GridSpan(3, 3),
    ),
    NEXT_EVENT(
        storedId = "next-event",
        storedClass = "glance.NextEvent",
        section = StreamSection.AGENDA,
        defaultLabel = "Next event",
        minSpan = GridSpan(2, 1),
        defaultSpan = GridSpan(4, 2),
        maxSpan = GridSpan(4, 3),
        fullMinSpan = GridSpan(3, 2),
    ),
    TOP_STORY(
        storedId = "top-story",
        storedClass = "glance.TopStory",
        section = StreamSection.FEEDS,
        defaultLabel = "Top story",
        minSpan = GridSpan(2, 1),
        defaultSpan = GridSpan(4, 2),
        maxSpan = GridSpan(4, 4),
        fullMinSpan = GridSpan(3, 3),
    ),
    ;

    val resizeConstraints: WidgetResizeConstraints
        get() = WidgetResizeConstraints(minSpan = minSpan, maxSpan = maxSpan)

    /** The layout the widget draws when it covers [span] cells. */
    fun layoutFor(span: GridSpan): GlanceLayout =
        when {
            span.columns >= fullMinSpan.columns && span.rows >= fullMinSpan.rows -> GlanceLayout.FULL
            span.columns >= COMPACT_MIN_COLUMNS -> GlanceLayout.COMPACT
            else -> GlanceLayout.MINIMAL
        }

    private companion object {
        const val COMPACT_MIN_COLUMNS = 3
    }
}

/**
 * How much a glance widget can draw at its span. [FULL] is the Stream's own card; [COMPACT] is a thumbnail, the text
 * and the main controls in one row; [MINIMAL] is a thumbnail, one line of text and at most one control (the rest
 * are in a menu and in accessibility actions).
 */
enum class GlanceLayout {
    MINIMAL,
    COMPACT,
    FULL,
    ;

    /** Whether previous, play or pause, and next each get their own 48 dp button; below it they go to a menu. */
    val showsFullTransport: Boolean get() = this != MINIMAL
}

/** What a glance widget draws: the Stream's own result for its section, reduced to one item or one honest state. */
sealed interface GlanceState {
    data object Loading : GlanceState

    /** Already excluded and redacted Stream content: a [MediaContent], [EventContent] or [ArticleContent]. */
    data class Showing(val content: StreamContent) : GlanceState

    data object Empty : GlanceState

    data class PermissionRequired(val sources: List<SourceId>) : GlanceState

    /** The user turned this section's sources off in Settings. */
    data object Off : GlanceState

    data object Unavailable : GlanceState
}

object GlanceWidgets {
    /** The reserved provider package: fixed text, not the running application id (a debug build adds a suffix). */
    const val PROVIDER_PACKAGE = "com.riffle.app"

    /** What a glance widget's pool item has instead of a platform host id: the unbound id the grid already uses. */
    val UNBOUND_HOST_ID = HostedWidgetId(0)

    val catalogue: List<GlanceWidgetKind> = GlanceWidgetKind.entries

    fun identity(kind: GlanceWidgetKind): WidgetProviderIdentity =
        WidgetProviderIdentity(
            packageName = AppPackageName(PROVIDER_PACKAGE),
            className = WidgetProviderClassName(kind.storedClass),
            profile = AppProfile.personal(),
        )

    /** The glance kind [provider] names, or null for a platform provider (or a glance class a newer build added). */
    fun kindOf(provider: WidgetProviderIdentity?): GlanceWidgetKind? =
        provider?.takeIf { it.packageName.value == PROVIDER_PACKAGE }
            ?.let { id -> GlanceWidgetKind.entries.firstOrNull { it.storedClass == id.className.value } }

    fun kindOfStoredId(id: String?): GlanceWidgetKind? = GlanceWidgetKind.entries.firstOrNull { it.storedId == id }

    /**
     * What [kind] draws for [result], the Stream's result for [GlanceWidgetKind.section]:
     * now playing is the newest media, next event the first upcoming event (the Agenda's order), top story the newest
     * unread article. A section that is ready but holds nothing of the kind's type is [GlanceState.Empty].
     */
    fun stateFor(
        kind: GlanceWidgetKind,
        result: StreamSectionResult?,
    ): GlanceState =
        when (result) {
            null, StreamSectionResult.Loading -> GlanceState.Loading
            StreamSectionResult.Empty -> GlanceState.Empty
            is StreamSectionResult.PermissionRequired -> GlanceState.PermissionRequired(result.sources)
            is StreamSectionResult.NotConnected, StreamSectionResult.Unavailable -> GlanceState.Unavailable
            StreamSectionResult.Off -> GlanceState.Off
            is StreamSectionResult.Ready -> pick(kind, result.items)?.let(GlanceState::Showing) ?: GlanceState.Empty
        }

    private fun pick(
        kind: GlanceWidgetKind,
        items: List<StreamContent>,
    ): StreamContent? =
        when (kind) {
            GlanceWidgetKind.NOW_PLAYING ->
                items.filterIsInstance<MediaContent>()
                    .maxWithOrNull(
                        compareBy<MediaContent> { it.postedAtEpochMillis ?: 0L }.thenByDescending { it.id.value },
                    )
            GlanceWidgetKind.NEXT_EVENT ->
                items.filterIsInstance<EventContent>()
                    .filter { it.kind != EventKind.ALARM }
                    .minWithOrNull(compareBy<EventContent> { it.startEpochMillis }.thenBy { it.id.value })
            GlanceWidgetKind.TOP_STORY ->
                items.filterIsInstance<ArticleContent>()
                    .filter { !it.read }
                    .maxWithOrNull(
                        compareBy<ArticleContent> { it.publishedAtEpochMillis ?: Long.MIN_VALUE }
                            .thenByDescending { it.id.value },
                    )
        }
}
