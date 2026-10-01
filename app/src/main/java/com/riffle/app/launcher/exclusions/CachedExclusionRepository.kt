package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionMigration
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import kotlinx.coroutines.CancellationException

/**
 * The synchronous view of the per-layout exclusion rules that lens evaluation reads, over a suspend
 * [ExclusionStorePort]. [rules] is empty until [initialize] has run, so before then (and while the workspace
 * preview is off, because nothing calls [initialize]) nothing is excluded.
 *
 * [initialize] reads what is stored, copies the legacy global hidden apps and hide rules into every layout
 * once ([ExclusionMigration]; a second call changes nothing) and persists the result only if it changed. The
 * legacy stores are only read. A read that throws (as opposed to a blob that cannot be decoded, which reads
 * as nothing stored) disables persistence for this process, so a transient storage failure can never replace
 * rules that exist on disk.
 */
internal class CachedExclusionRepository(
    private val store: ExclusionStorePort,
) {
    @Volatile
    private var cached: LayoutExclusionRules? = null

    suspend fun initialize(
        hiddenApps: Set<AppIdentity>,
        hideRules: List<NotificationHideRule>,
    ) {
        if (cached != null) return
        val read = runCatching { store.read() }.onFailure { if (it is CancellationException) throw it }
        val stored = read.getOrNull()
        val migrated = ExclusionMigration.migrate(stored ?: LayoutExclusionRules(), hiddenApps, hideRules)
        cached = migrated
        if (read.isSuccess && migrated != stored) {
            runCatching { store.write(migrated) }.onFailure { if (it is CancellationException) throw it }
        }
    }

    /** The rule set of [deviceClass]; empty before [initialize]. */
    fun rules(deviceClass: HomeLayoutDeviceClass): ExclusionRuleSet =
        cached?.forLayout(deviceClass) ?: ExclusionRuleSet.EMPTY
}
