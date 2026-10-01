package com.riffle.app.launcher.workspace

import androidx.compose.foundation.layout.PaddingValues
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.expressions.ExpressionEnvironment
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.container.LensResultProvider
import com.riffle.core.domain.launcher.workspace.container.SourceBackedLensResultProvider
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.editor.SourceChoices
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.lens.ZoneDayBucketer
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import java.time.ZoneId
import java.util.concurrent.Executor

/**
 * Everything the workspace preview needs at runtime, assembled once and shared by the preview surface and
 * the editor: the workspace [repository], the [registry] of real item sources (read for descriptors; the
 * provider shares one upstream subscription per source), the [provider] that evaluates lenses off the main
 * thread, the [imageLoader] and the item [actions].
 *
 * [prepareExclusions] loads the layout's source exclusion rules, which lens evaluation applies before every
 * lens; until it has run nothing is excluded.
 *
 * Nothing here starts work: sources subscribe only when a lens is observed, which only happens while a
 * container is composed, so with the preview off no source runs.
 */
internal class WorkspaceRuntime(
    val repository: CachedWorkspaceRepository,
    val registry: SourceRegistry,
    val provider: LensResultProvider,
    private val imageLoader: ExpressionImageLoader,
    private val itemActions: WorkspaceItemActions,
    private val sourceAccess: () -> Map<SourceId, SourceAccess> = { emptyMap() },
    private val exclusionLoader: suspend () -> Unit = {},
) {
    /** Loads the per-layout exclusion rules (migrating the legacy ones once); called when the preview is on. */
    suspend fun prepareExclusions() = exclusionLoader()

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
            actions = itemActions.containerActions(),
        )
}

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
): LensResultProvider =
    SourceBackedLensResultProvider(
        registry = registry,
        evaluator = AsyncLensEvaluator(executor),
        context = { LensEvaluationContext(nowEpochMillis(), ZoneDayBucketer(zone()), exclusions = exclusions()) },
    )
