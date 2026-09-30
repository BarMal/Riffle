package com.riffle.core.domain.launcher.workspace

internal object FilterCodec {
    private const val MAX_DEPTH = 8

    fun encode(filter: LensFilter): StoredValue.Obj =
        when (filter) {
            LensFilter.All -> obj("type" to str("all"))
            is LensFilter.AllOf -> obj("type" to str("all_of"), "filters" to arr(filter.filters.map(::encode)))
            is LensFilter.AnyOf -> obj("type" to str("any_of"), "filters" to arr(filter.filters.map(::encode)))
            is LensFilter.Not -> obj("type" to str("not"), "filter" to encode(filter.filter))
            is LensFilter.FromSource -> obj("type" to str("from_source"), "source" to str(filter.sourceId.value))
            is LensFilter.GroupKeyIs -> obj("type" to str("group_key_is"), "key" to str(filter.key))
            is LensFilter.HasActions ->
                obj("type" to str("has_actions"), "required" to StoredValue.Bool(filter.required))
            is LensFilter.AgeAtMost -> obj("type" to str("age_at_most"), "millis" to num(filter.millis))
            is LensFilter.AgeAtLeast -> obj("type" to str("age_at_least"), "millis" to num(filter.millis))
            is LensFilter.PrivacyIs -> obj("type" to str("privacy_is"), "privacy" to str(filter.privacy.name))
            is LensFilter.ExtEquals ->
                obj("type" to str("ext_equals"), "key" to str(filter.key.value), "value" to encodeExt(filter.value))
        }

    /** Unknown, malformed or too deeply nested filters decode to [LensFilter.All]. */
    fun decode(
        value: StoredValue.Obj?,
        depth: Int = 0,
    ): LensFilter {
        if (value == null || depth > MAX_DEPTH) return LensFilter.All
        return guarded { decodeComposite(value, depth) ?: decodeSimple(value) ?: decodeValued(value) } ?: LensFilter.All
    }

    private fun decodeComposite(
        value: StoredValue.Obj,
        depth: Int,
    ): LensFilter? {
        val children = value.array("filters").mapNotNull { (it as? StoredValue.Obj)?.let { c -> decode(c, depth + 1) } }
        return when (value.string("type")) {
            "all_of" -> LensFilter.AllOf(children)
            "any_of" -> LensFilter.AnyOf(children)
            "not" -> LensFilter.Not(decode(value.obj("filter"), depth + 1))
            else -> null
        }
    }

    private fun decodeSimple(value: StoredValue.Obj): LensFilter? =
        when (value.string("type")) {
            "from_source" -> value.string("source")?.let { LensFilter.FromSource(SourceId(it)) }
            "group_key_is" -> value.string("key")?.let(LensFilter::GroupKeyIs)
            "has_actions" -> LensFilter.HasActions(value.bool("required") ?: true)
            "privacy_is" -> enumOrNull<ItemPrivacy>(value.string("privacy"))?.let(LensFilter::PrivacyIs)
            else -> null
        }

    private fun decodeValued(value: StoredValue.Obj): LensFilter? =
        when (value.string("type")) {
            "age_at_most" -> value.long("millis")?.takeIf { it >= 0 }?.let(LensFilter::AgeAtMost)
            "age_at_least" -> value.long("millis")?.takeIf { it >= 0 }?.let(LensFilter::AgeAtLeast)
            "ext_equals" -> {
                val key = value.string("key")?.let(::ItemExtKey)
                val ext = decodeExt(value.obj("value"))
                if (key != null && ext != null) LensFilter.ExtEquals(key, ext) else null
            }
            else -> null
        }

    private fun encodeExt(value: ItemExtValue): StoredValue.Obj =
        when (value) {
            is ItemExtValue.Text -> obj("text" to str(value.value))
            is ItemExtValue.Number -> obj("number" to num(value.value))
            is ItemExtValue.Flag -> obj("flag" to StoredValue.Bool(value.value))
        }

    private fun decodeExt(value: StoredValue.Obj?): ItemExtValue? =
        when {
            value == null -> null
            value.string("text") != null -> ItemExtValue.Text(value.string("text").orEmpty())
            value.long("number") != null -> ItemExtValue.Number(value.long("number") ?: 0L)
            else -> value.bool("flag")?.let(ItemExtValue::Flag)
        }
}
