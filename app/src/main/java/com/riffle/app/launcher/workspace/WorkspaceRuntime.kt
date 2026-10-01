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

/**
 * Everything the workspace preview needs at runtime, assembled once and shared by the preview surface and
 * the editor: the workspace [repository], the [registry] of real item sources (read for descriptors; the
 * provider shares one upstream subscription per source), the [provider] that evaluates lenses off the main
 * thread, the [imageLoader] and the item [actions].
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
    private val sourceControls: SourceControls = SourceControls(registry),
) {
    /** Which sources the user turned off; the registry behind [provider] consults it. */
    val enablement: SourceEnablement get() = sourceControls.enablement

    /** A status monitor over every registered source; the caller starts it and stops it when the page closes. */
    fun sourceStatusMonitor(onChange: (Map<SourceId, SourceStatus>) -> Unit): SourceStatusMonitor =
        SourceStatusMonitor(sourceControls.statusRegistry, registry.descriptors().map { it.id }, onChange)

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
): LensResultProvider =
    SourceBackedLensResultProvider(
        registry = registry,
        evaluator = AsyncLensEvaluator(executor),
        context = { LensEvaluationContext(nowEpochMillis(), ZoneDayBucketer(zone())) },
    )
