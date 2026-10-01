package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules

/** Suspend storage for the per-layout exclusion rules; the DataStore-backed store implements it, tests fake it. */
internal interface ExclusionStorePort {
    suspend fun read(): LayoutExclusionRules?

    suspend fun write(rules: LayoutExclusionRules)
}
