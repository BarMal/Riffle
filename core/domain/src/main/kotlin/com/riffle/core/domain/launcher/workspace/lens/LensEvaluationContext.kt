package com.riffle.core.domain.launcher.workspace.lens

import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleSet
import java.time.Instant
import java.time.ZoneId

/** Buckets an instant into a stable calendar-day key. Injected so the domain never reads the system zone. */
fun interface DayBucketer {
    fun dayKey(epochMillis: Long): String
}

/** Buckets by calendar day in [zone], keyed as ISO `yyyy-MM-dd`. */
class ZoneDayBucketer(private val zone: ZoneId) : DayBucketer {
    override fun dayKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()
}

/**
 * Everything a lens evaluation needs besides the lens and the items, all injected so evaluation stays
 * a pure function: [nowEpochMillis] drives the age filters, [dayBucketer] drives `ByDay` grouping.
 *
 * [maxInputItems] and [maxFilterDepth] bound work on pathological input (see [LensEvaluator]).
 */
data class LensEvaluationContext(
    val nowEpochMillis: Long,
    val dayBucketer: DayBucketer = ZoneDayBucketer(ZoneId.of("UTC")),
    val maxInputItems: Int = DEFAULT_MAX_INPUT_ITEMS,
    val maxFilterDepth: Int = DEFAULT_MAX_FILTER_DEPTH,
    /** The current layout's source exclusions, applied before every lens; empty means nothing is excluded. */
    val exclusions: ExclusionRuleSet = ExclusionRuleSet.EMPTY,
) {
    init {
        require(maxInputItems > 0) { "maxInputItems must be positive." }
        require(maxFilterDepth > 0) { "maxFilterDepth must be positive." }
    }

    companion object {
        const val DEFAULT_MAX_INPUT_ITEMS = 10_000
        const val DEFAULT_MAX_FILTER_DEPTH = 32
    }
}
