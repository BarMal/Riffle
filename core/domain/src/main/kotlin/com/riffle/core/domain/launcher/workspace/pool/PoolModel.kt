package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.WorkspaceId

/** Stable identity of a pool item. Unique per layout pool and never reused for another item. */
@JvmInline
value class PoolItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Pool item ids must not be blank." }
    }
}

/** What an item *is*. Where it sits is an [Arrangement]; how often it is referenced is derived, never stored. */
sealed interface PoolItem {
    val id: PoolItemId
    val label: String
}

/** An app or an app shortcut. Stateless, so it may be referenced from any number of arrangements. */
data class PoolApp(
    override val id: PoolItemId,
    val appIdentity: AppIdentity,
    override val label: String,
    val appShortcutId: AppShortcutId? = null,
) : PoolItem

/** A value entry of a folder (not a pool item; no nested references). [entryId] is stable within the folder. */
data class FolderEntry(
    val entryId: String,
    val appIdentity: AppIdentity,
    val label: String,
    val appShortcutId: AppShortcutId? = null,
)

/** Shared between arrangements only by an explicit action ([PoolPlacement.shareFolder]); copies clone it. */
data class PoolFolder(
    override val id: PoolItemId,
    override val label: String,
    val entries: List<FolderEntry> = emptyList(),
) : PoolItem

/**
 * A hosted widget. At most one placement in the whole layout. [hostedId] null is an unbound placeholder
 * (copied, restored, or provider gone); [provider] null is a legacy widget that cannot be cloned.
 */
data class PoolWidget(
    override val id: PoolItemId,
    override val label: String,
    val resizeConstraints: WidgetResizeConstraints = WidgetResizeConstraints(),
    val provider: WidgetProviderIdentity? = null,
    val hostedId: HostedWidgetId? = null,
) : PoolItem

/** One reference: "this item sits here". */
data class Placement(
    val item: PoolItemId,
    val at: GridPlacement,
)

/** A home page of an arrangement: a `LauncherPage` minus the items, plus references. Always a Home page. */
data class ArrangementPage(
    val id: LauncherPageId,
    val grid: GridDimensions,
    val placements: List<Placement> = emptyList(),
    val generatedContentOverflowCount: Int = 0,
    val isPinned: Boolean = false,
)

/** What a workspace's [NewAppPlacement] means for a newly installed app (N9). */
enum class NewAppPlacement {
    FINDER_ONLY,
    HOME_AND_FINDER,
}

/** One workspace's placed pages. Held beside the pool rather than in `Workspace.pages` (see the doc). */
data class Arrangement(
    val pages: List<ArrangementPage> = emptyList(),
    val newAppPlacement: NewAppPlacement = NewAppPlacement.FINDER_ONLY,
)

/**
 * The placed items of one layout: [items] by id plus every workspace's [arrangements]. Reference counts
 * are derived ([PoolReferences]), never stored. Mutate only through the `Pool*` operation objects.
 */
data class PlacedItemPool(
    val items: Map<PoolItemId, PoolItem> = emptyMap(),
    val arrangements: Map<WorkspaceId, Arrangement> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && arrangements.isEmpty()
}
