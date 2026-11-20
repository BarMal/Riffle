package com.riffle.app.launcher.search.wider

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.core.domain.launcher.search.wider.CalculatorSource
import com.riffle.core.domain.launcher.search.wider.SearchSource
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.WiderSearchAction
import com.riffle.core.domain.launcher.search.wider.WiderSearchEngine

/**
 * What the wider search needs from the platform, behind one seam so the same search draws in the Dock, the Library
 * and the search stack, and a test can stand in for the device. Nothing here persists anything: not the query, not a
 * result. The activity provides the real one ([AndroidWiderSearchHost]).
 */
internal interface WiderSearchHost {
    /** The sources this device can offer; each is run only while its switch is on. */
    val engine: WiderSearchEngine

    /** Counts up when a permission result arrives, so a composable reading it checks [hasAccess] again. */
    val accessVersion: Int

    /** Whether [source] may read what it needs now; sources that need no permission are always allowed. */
    fun hasAccess(source: SearchSourceId): Boolean

    /** Starts the system permission request for [source]. Only ever called from a tap on an inline "Allow". */
    fun requestAccess(source: SearchSourceId)

    /** Does what a row asked for: copies, opens a screen or a contact, dials, writes a message, opens a file. */
    fun perform(action: WiderSearchAction)

    /** Contact photos, decoded at the size asked for and held in memory only. */
    val contactPhotos: ExpressionImageLoader
}

/** A host with the sources it is given and permissions it is told it has: the default, and what tests use. */
internal class StaticWiderSearchHost(
    sources: List<SearchSource> = listOf(CalculatorSource()),
    private val granted: Set<SearchSourceId> = emptySet(),
    private val onPerform: (WiderSearchAction) -> Unit = {},
    private val onRequest: (SearchSourceId) -> Unit = {},
    override val contactPhotos: ExpressionImageLoader = NoExpressionImageLoader,
) : WiderSearchHost {
    private val version = mutableIntStateOf(0)

    override val engine: WiderSearchEngine = WiderSearchEngine(sources)
    override val accessVersion: Int get() = version.intValue

    override fun hasAccess(source: SearchSourceId): Boolean = !source.needsPermission || source in granted

    override fun requestAccess(source: SearchSourceId) = onRequest(source)

    override fun perform(action: WiderSearchAction) = onPerform(action)
}

/** Contacts and files are the sources that need an optional permission. */
internal val SearchSourceId.needsPermission: Boolean
    get() = this == SearchSourceId.CONTACTS || this == SearchSourceId.FILES

internal val LocalWiderSearchHost = staticCompositionLocalOf<WiderSearchHost> { StaticWiderSearchHost() }
