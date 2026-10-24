package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelPlanner
import com.riffle.core.domain.launcher.workspace.settings.ContentLevelRow
import com.riffle.core.domain.launcher.workspace.settings.SourceContentPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The state holder behind the content-level controls of Settings > Privacy and the source pages. No Android types.
 *
 * [levels] follows [privacy] while a page is open ([open] to [close]); a change made anywhere (the other page, a
 * second window) shows at once. Setting a level writes through [privacy], which the lens provider also watches, so
 * every observed page re-evaluates immediately. Holds levels only, never items.
 */
internal class SourceContentLevelsController(
    private val privacy: SourceContentPrivacy,
    private val descriptors: () -> List<SourceDescriptor>,
) {
    private val mutableLevels = MutableStateFlow(privacy.levels())
    private var observing: SourceSubscription? = null

    val levels: StateFlow<SourceContentLevels> = mutableLevels.asStateFlow()

    /** Starts following the stored levels. Idempotent. */
    fun open() {
        if (observing != null) return
        mutableLevels.value = privacy.levels()
        observing = privacy.observe { mutableLevels.value = privacy.levels() }
    }

    /** Stops following. Idempotent. */
    fun close() {
        observing?.cancel()
        observing = null
    }

    /** The Privacy page's rows: every registered privacy-sensitive source with its level. */
    fun rows(levels: SourceContentLevels): List<ContentLevelRow> = ContentLevelPlanner.plan(descriptors(), levels)

    /** Whether the page of source [id] shows the control. */
    fun supports(id: SourceId): Boolean = ContentLevelPlanner.supports(descriptors(), id)

    fun setLevel(
        id: SourceId,
        level: SourceContentLevel,
    ) {
        privacy.setLevel(id, level)
        mutableLevels.value = privacy.levels()
    }
}
