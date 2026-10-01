package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

const val CURRENT_EXCLUSION_SCHEMA_VERSION = 1

/**
 * Encodes and decodes [LayoutExclusionRules] to [StoredValue]. A rule holds only identifiers and the text
 * the user chose to match, never item content.
 *
 * Decoding never throws: a value that is not an object is null; a layout with an unknown device class, a
 * rule with an unknown matcher kind or a missing id/source, and a repeated rule id are dropped; unknown enum
 * values fall back to a default. A newer schema version is read on a best-effort basis.
 */
object ExclusionRulesCodec {
    fun encode(rules: LayoutExclusionRules): StoredValue.Obj =
        obj(
            "version" to num(CURRENT_EXCLUSION_SCHEMA_VERSION),
            "legacyMigrated" to StoredValue.Bool(rules.legacyMigrated),
            "layouts" to
                arr(
                    rules.layouts.map { (deviceClass, set) ->
                        obj(
                            "deviceClass" to str(deviceClass.name),
                            "rules" to arr(set.rules.map(::encodeRule)),
                        )
                    },
                ),
        )

    fun decode(value: StoredValue?): LayoutExclusionRules? {
        val root = value as? StoredValue.Obj ?: return null
        val layouts =
            root.array("layouts")
                .mapNotNull { (it as? StoredValue.Obj)?.let(::decodeLayout) }
                .toMap()
        return LayoutExclusionRules(layouts, legacyMigrated = root.bool("legacyMigrated") ?: false)
    }

    private fun decodeLayout(root: StoredValue.Obj): Pair<HomeLayoutDeviceClass, ExclusionRuleSet>? {
        val deviceClass = enumOrNull<HomeLayoutDeviceClass>(root.string("deviceClass")) ?: return null
        val rules = root.array("rules").mapNotNull { (it as? StoredValue.Obj)?.let(::decodeRule) }
        return deviceClass to ExclusionRuleSet(rules.distinctBy { it.id })
    }

    private fun encodeRule(rule: SourceExclusionRule): StoredValue.Obj =
        obj(
            "id" to str(rule.id.value),
            "source" to str(rule.source.value),
            "matcher" to ExclusionMatcherCodec.encode(rule.matcher),
            "enabled" to StoredValue.Bool(rule.enabled),
            "label" to rule.label?.let(::str),
            "origin" to str(rule.origin.name),
            "createdAt" to num(rule.createdAtEpochMillis),
        )

    private fun decodeRule(root: StoredValue.Obj): SourceExclusionRule? =
        guarded {
            SourceExclusionRule(
                id = ExclusionRuleId(root.string("id") ?: return@guarded null),
                source = SourceId(root.string("source") ?: return@guarded null),
                matcher = ExclusionMatcherCodec.decode(root.fields["matcher"]) ?: return@guarded null,
                enabled = root.bool("enabled") ?: true,
                label = root.string("label")?.take(SourceExclusionRule.MAX_LABEL_LENGTH),
                origin = enumOrNull<ExclusionOrigin>(root.string("origin")) ?: ExclusionOrigin.USER,
                createdAtEpochMillis = (root.long("createdAt") ?: 0L).coerceAtLeast(0L),
            )
        }
}
