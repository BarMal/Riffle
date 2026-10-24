package com.riffle.app.launcher.wizard

import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool

/**
 * [WizardStores] over the process's repositories: the cached workspace repository (the home and the menu re-read it)
 * and the one pool repository (the pool-backed home sees the change as an external one and re-projects). [flushPool]
 * writes the pool behind (the home's own flush when there is one, so released host ids and the recovery snapshot
 * follow). [readOnly] and [holding] read the home's state at the moment of the write.
 */
internal class HomeWizardStores(
    private val workspaceRepository: WorkspaceRepository,
    private val poolRepository: CachedPoolRepository,
    private val readOnly: () -> Boolean,
    private val holding: () -> Boolean,
    private val flushPool: () -> Unit,
) : WizardStores {
    override fun workspaces(): WorkspaceSet? = workspaceRepository.load()

    override fun saveWorkspaces(set: WorkspaceSet) = workspaceRepository.save(set)

    override fun pool(layout: HomeLayoutDeviceClass): PlacedItemPool? =
        if (poolRepository.current() == null) null else poolRepository.pool(layout) ?: PlacedItemPool()

    override fun savePool(
        layout: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean = poolRepository.update(layout, pool).also { updated -> if (updated) flushPool() }

    override val homeReadOnly: Boolean get() = readOnly()

    override val holdingItemEdits: Boolean get() = holding()
}

/** [WizardStores] with the workspace store only (a host without the pool): picked apps are refused, nothing else. */
internal class WorkspaceOnlyWizardStores(
    private val workspaceRepository: WorkspaceRepository,
) : WizardStores {
    override fun workspaces(): WorkspaceSet? = workspaceRepository.load()

    override fun saveWorkspaces(set: WorkspaceSet) = workspaceRepository.save(set)

    override fun pool(layout: HomeLayoutDeviceClass): PlacedItemPool? = null

    override fun savePool(
        layout: HomeLayoutDeviceClass,
        pool: PlacedItemPool,
    ): Boolean = false

    override val homeReadOnly: Boolean get() = false

    override val holdingItemEdits: Boolean get() = false
}
