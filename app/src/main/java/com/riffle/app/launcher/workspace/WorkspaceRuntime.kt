package com.riffle.app.launcher.workspace

import androidx.compose.foundation.layout.PaddingValues
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspacesSettingsText
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.exclusions.CachedExclusionRepository
import com.riffle.app.launcher.exclusions.ExclusionMatchCounter
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.expressions.ExpressionEnvironment
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.container.LensResultProvider
import com.riffle.core.domain.launcher.workspace.container.SourceBackedLensResultProvider
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.lens.ZoneDayBucketer
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.SourceEnablement
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourceStatusMonitor
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Everything the workspace preview needs at runtime, assembled once and shared by the preview surface and
 * the editor: the workspace [repository], the [registry] of real item sources (read for descriptors; the
 * provider shares one upstream subscription per source), the [provider] that evaluates lenses off the main
 * thread, the [imageLoader] and the item [actions].
 *
 * [prepareExclusions] loads the layout's source exclusion rules, which lens evaluation applies before every
 * lens; until it has run nothing is excluded. [exclusions] is the repository behind them (the management page
 * edits it, and the lens provider re-evaluates when it changes).
 *
 * Nothing here starts work: sources subscribe only when a lens is observed, which only happens while a
 * container is composed, so with the preview off no source runs.
 */
@Suppress("LongParameterList")
internal class WorkspaceRuntime(
    val repository: CachedWorkspaceRepository,
    val registry: SourceRegistry,
    val provider: LensResultProvider,
    private val imageLoader: ExpressionImageLoader,
    private val itemActions: WorkspaceItemActions,
    private val sourceAccess: () -> Map<SourceId, SourceAccess> = { emptyMap() },
    private val exclusionLoader: suspend () -> Unit = {},
    private val sourceControls: SourceControls = SourceControls(registry),
    val exclusions: CachedExclusionRepository? = null,
) {
    /** Loads the per-layout exclusion rules (migrating the legacy ones once); called when the preview is on. */
    suspend fun prepareExclusions() = exclusionLoader()

    /** Which sources the user turned off; the registry behind [provider] consults it. */
    val enablement: SourceEnablement get() = sourceControls.enablement

    /** A status monitor over every registered source; the caller starts it and stops it when the page closes. */
    fun sourceStatusMonitor(onChange: (Map<SourceId, SourceStatus>) -> Unit): SourceStatusMonitor =
        SourceStatusMonitor(sourceControls.statusRegistry, registry.descriptors().map { it.id }, onChange)

    /**
     * The "hides N items" counter of Settings > Hidden items and rules, over the same shared registry the containers
     * and the Sources page read through (so it adds no upstream). The caller starts and stops it with the page.
     */
    fun exclusionMatchCounter(onCounts: (Map<ExclusionRuleId, Int>) -> Unit): ExclusionMatchCounter =
        ExclusionMatchCounter(sourceControls.statusRegistry, onCounts, countExecutor)

    private val countExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "riffle-exclusion-counts").apply { isDaemon = true } }
    }

    /**
     * The controller behind the preview's contextual Hide (overflow button, long press, TalkBack action), or null
     * when this runtime has no exclusion rules. It shares [exclusions] with the management page, so a hide changes
     * the rules everywhere at once; it never opens the page's counter.
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

    /** The editor's source choices: every registered source with the access it currently has (never prompts). */
    fun sourceChoices(): List<SourceChoice> = SourceChoices.build(registry.descriptors(), sourceAccess())

    /**
     * Services for a surface: [reducedMotion] is the resolved preference, [contentPadding] the insets the
     * surface wants expressions to respect.
     */
    fun services(
        reducedMotion: Boolean,
        contentPadding: PaddingValues = PaddingValues(),
    ): ContainerServices =
        ContainerServices(
            provider = provider,
            environment =
                ExpressionEnvironment(
                    imageLoader = imageLoader,
                    contentPadding = contentPadding,
                    reducedMotion = reducedMotion,
                ),
            actions =
                itemActions.containerActions().copy(
                    onEnableSources = { lens -> lens.sources.forEach { enablement.setEnabled(it, true) } },
                ),
        )
}

/**
 * What Settings > Sources needs from the runtime: [enablement] (which sources are turned off; the registry behind
 * the lens provider consults it) and the [statusRegistry] the provider reads through, so the page reads each
 * source's status from the same shared stream the containers use and adds no upstream of its own.
 */
internal class SourceControls(
    val statusRegistry: SourceRegistry,
    val enablement: SourceEnablement = StoredSourceEnablement(InMemoryDisabledSourcesStore()),
)

/**
 * The lens provider over [registry]: it shares one subscription per source and evaluates on [executor], so
 * lens work never runs on the main thread. Each evaluation reads the clock and zone afresh.
 */
internal fun workspaceLensProvider(
    registry: SourceRegistry,
    executor: Executor,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    zone: () -> ZoneId = ZoneId::systemDefault,
    exclusions: () -> ExclusionRuleSet = { ExclusionRuleSet.EMPTY },
    exclusionChanges: ContextChanges? = null,
): LensResultProvider =
    SourceBackedLensResultProvider(
        registry = registry,
        evaluator = AsyncLensEvaluator(executor),
        context = { LensEvaluationContext(nowEpochMillis(), ZoneDayBucketer(zone()), exclusions = exclusions()) },
        contextChanges = exclusionChanges,
    )
