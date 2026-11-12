package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspacesSettingsText
import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.exclusions.ExclusionMatchCounter
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.ics.IcsFeedsController
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.settings.InMemoryContentLevelStore
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.SourceContentPrivacy
import com.riffle.core.domain.launcher.workspace.settings.SourceEnablement
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourceStatusMonitor
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceContentPrivacy
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * What the Sources, Privacy, Hidden items and Calendar feeds pages and the Stream's Hide actions need at runtime,
 * assembled once: the stored-workspace [repository] (read, never rewritten by the shell), the [registry] of real item
 * sources (read for descriptors), and the stores behind [enablement] and [contentPrivacy].
 *
 * [prepareExclusions] loads the layout's source exclusion rules; until it has run nothing is excluded. [exclusions] is
 * the repository behind them (the management page edits it).
 *
 * Nothing here starts work: sources subscribe only when a Stream section or a Settings page observes them.
 */
@Suppress("LongParameterList")
internal class SourcesRuntime(
    val repository: CachedWorkspaceRepository,
    val registry: SourceRegistry,
    private val exclusionLoader: suspend () -> Unit = {},
    private val sourceControls: SourceControls = SourceControls(registry),
    val exclusions: CachedExclusionRepository? = null,
    val icsFeeds: IcsFeedsController? = null,
    /** The shared search query holder; Settings reports on it (memory only). */
    val searchQuery: SearchQueryHolder = SearchQueryHolder(),
) {
    /** Loads the per-layout exclusion rules (migrating the legacy ones once). */
    suspend fun prepareExclusions() = exclusionLoader()

    /** Which sources the user turned off; the registries consult it. */
    val enablement: SourceEnablement get() = sourceControls.enablement

    /** How much of each source the Stream may show (Settings > Privacy). */
    val contentPrivacy: SourceContentPrivacy get() = sourceControls.contentPrivacy

    /** A status monitor over every registered source; the caller starts it and stops it when the page closes. */
    fun sourceStatusMonitor(onChange: (Map<SourceId, SourceStatus>) -> Unit): SourceStatusMonitor =
        SourceStatusMonitor(sourceControls.statusRegistry, registry.descriptors().map { it.id }, onChange)

    /**
     * The "hides N items" counter of Settings > Hidden items and rules, over the same shared registry the Sources
     * page reads through (so it adds no upstream). The caller starts and stops it with the page.
     */
    fun exclusionMatchCounter(onCounts: (Map<ExclusionRuleId, Int>) -> Unit): ExclusionMatchCounter =
        ExclusionMatchCounter(sourceControls.statusRegistry, onCounts, countExecutor)

    private val countExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "riffle-exclusion-counts").apply { isDaemon = true } }
    }

    /**
     * The controller behind the Stream's contextual Hide, or null when this runtime has no exclusion rules. It shares
     * [exclusions] with the management page, so a hide changes the rules everywhere at once.
     */
    fun hideController(): ExclusionsSettingsController? =
        exclusions?.let { repository ->
            ExclusionsSettingsController(
                repository = repository,
                counterFor = ::exclusionMatchCounter,
                disabledSources = { enablement.disabledIds() },
                layoutName = WorkspacesSettingsText::layoutName,
            )
        }
}

/**
 * What Settings > Sources needs from the runtime: [enablement] (which sources are turned off; the registry behind
 * the Stream consults it) and the [statusRegistry] the page reads through, so it adds no upstream of its own.
 * [contentPrivacy] holds the per-source content levels (Settings > Privacy) the Stream applies.
 */
internal class SourceControls(
    val statusRegistry: SourceRegistry,
    val enablement: SourceEnablement = StoredSourceEnablement(InMemoryDisabledSourcesStore()),
    val contentPrivacy: SourceContentPrivacy = StoredSourceContentPrivacy(InMemoryContentLevelStore()),
)
