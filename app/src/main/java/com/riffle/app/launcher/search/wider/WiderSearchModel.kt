package com.riffle.app.launcher.search.wider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.search.wider.MergedSearch
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.WiderSearchAction
import com.riffle.core.domain.launcher.search.wider.WiderSearchMerger
import com.riffle.core.domain.launcher.search.wider.WiderSearchResult
import com.riffle.core.domain.launcher.search.wider.WiderSearchSnapshot
import com.riffle.core.domain.launcher.settings.SearchSettings

/**
 * The wider search for one query as the surfaces draw it: the merged answers and groups, and what a tap does. Built by
 * [rememberWiderSearch], the one implementation the Dock's search (as a stack, icons or list), the Library's search
 * and every other surface share.
 */
@Stable
internal class WiderSearchModel<P>(
    val merged: MergedSearch<P>,
    private val host: WiderSearchHost,
    private val expand: (SearchSourceId) -> Unit,
) {
    val hasAnything: Boolean get() = merged.answers.isNotEmpty() || merged.groups.isNotEmpty()

    /** Something wider than apps is shown, so "No results" would be untrue. */
    val hasWiderResults: Boolean get() = hasAnything

    val contactPhotos get() = host.contactPhotos

    fun showMore(source: SearchSourceId) = expand(source)

    fun requestAccess(source: SearchSourceId) = host.requestAccess(source)

    fun perform(action: WiderSearchAction) = host.perform(action)

    /**
     * Enter: the calculator's answer is copied; with no app to open, the first wider row does its main action.
     * Returns false when there is nothing here for Enter, so the caller opens the top app as before.
     */
    fun enter(): Boolean {
        val top = merged.topWiderResult ?: return false
        top.primaryAction()?.let(host::perform)
        return top.primaryAction() != null
    }
}

/** What a tap on the row does: copy, open or dial. Null for a result that has no main action. */
internal fun WiderSearchResult.primaryAction(): WiderSearchAction? =
    when (this) {
        is WiderSearchResult.CalculatorAnswer -> WiderSearchAction.Copy(answer)
        is WiderSearchResult.UnitConversion -> WiderSearchAction.Copy(answer)
        is WiderSearchResult.SystemSetting -> WiderSearchAction.OpenSystemSetting(intentAction)
        is WiderSearchResult.Contact -> WiderSearchAction.OpenContact(contactUri)
        is WiderSearchResult.FileHit -> WiderSearchAction.OpenFile(uri, mimeType)
        is WiderSearchResult.FilesApp -> WiderSearchAction.OpenFilesApp(query)
    }

/**
 * Runs the enabled sources for [query] and merges them under [primary] (the apps, already answered). The apps are
 * never waited for: this only adds. A new query cancels the sources still running for the old one, and what an old
 * query found is not shown against a newer one. Expanded groups are forgotten with the query.
 */
@Composable
internal fun <P> rememberWiderSearch(
    query: String,
    search: SearchSettings,
    primary: List<P>,
): WiderSearchModel<P> {
    val host = LocalWiderSearchHost.current
    val accessVersion = host.accessVersion
    val enabled = search.enabledSources
    val missing =
        enabled.filter { source -> source.needsPermission && !host.hasAccess(source) }.toSet()
    val runnable = enabled - setOf(SearchSourceId.CONTACTS).intersect(missing)
    var snapshot by remember { mutableStateOf(WiderSearchSnapshot(query = "")) }
    var expanded by remember(query) { mutableStateOf(emptySet<SearchSourceId>()) }
    LaunchedEffect(query, runnable, search.resultsPerGroup, accessVersion, host) {
        if (query.isBlank()) {
            snapshot = WiderSearchSnapshot(query = query)
        } else {
            host.engine.search(query, runnable, search.resultsPerGroup).collect { snapshot = it }
        }
    }
    // A snapshot for another query is stale: nothing of it is drawn.
    val current = if (snapshot.query == query && query.isNotBlank()) snapshot else WiderSearchSnapshot(query = query)
    val merged =
        WiderSearchMerger.merge(
            primary = primary,
            snapshot = current,
            order = search.groupOrder,
            perGroup = search.resultsPerGroup,
            expanded = expanded,
            needsPermission = if (query.isBlank()) emptySet() else missing,
        )
    return remember(merged, host) { WiderSearchModel(merged, host) { source -> expanded = expanded + source } }
}
