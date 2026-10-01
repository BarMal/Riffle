package com.riffle.core.domain.launcher.workspace

internal object LensCodec {
    fun encode(lens: Lens): StoredValue.Obj =
        obj(
            "sources" to arr(lens.sources.map { str(it.value) }),
            "filter" to FilterCodec.encode(lens.filter),
            "group" to encodeGroup(lens.group),
            "sort" to
                obj(
                    "field" to str(lens.sort.field.name),
                    "direction" to str(lens.sort.direction.name),
                    "pinnedFirst" to StoredValue.Bool(lens.sort.pinnedFirst),
                ),
            "limit" to lens.limit?.let(::num),
            "project" to arr(lens.project.sortedBy { it.ordinal }.map { str(it.name) }),
            "params" to encodeParameters(lens),
        )

    /** Only present when the lens has a parameter for one of its sources, so older data is unaffected. */
    private fun encodeParameters(lens: Lens): StoredValue.Arr? =
        lens.sources.distinct().mapNotNull { id ->
            lens.parameterFor(id)?.let { obj("source" to str(id.value), "query" to str(it.text)) }
        }.takeIf { it.isNotEmpty() }?.let(::arr)

    private fun decodeParameters(
        root: StoredValue.Obj,
        sources: List<String>,
    ): Map<SourceId, SourceParameter> =
        buildMap {
            root.array("params").forEach { entry ->
                val item = entry as? StoredValue.Obj ?: return@forEach
                val source = item.string("source")?.takeIf { it in sources } ?: return@forEach
                val parameter = item.string("query")?.let(SourceParameter::query) ?: return@forEach
                putIfAbsent(SourceId(source), parameter)
            }
        }

    /** Returns null when no usable source remains. */
    fun decode(value: StoredValue?): Lens? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            val sources =
                root.array("sources").mapNotNull { (it as? StoredValue.Str)?.value?.takeIf(String::isNotBlank) }
            if (sources.isEmpty()) return@guarded null
            val sort = root.obj("sort")
            Lens(
                sources = sources.map(::SourceId),
                filter = FilterCodec.decode(root.obj("filter")),
                group = decodeGroup(root.obj("group")),
                sort =
                    LensSort(
                        field = enumOrNull<LensSortField>(sort?.string("field")) ?: LensSortField.SOURCE_ORDER,
                        direction = enumOrNull<SortDirection>(sort?.string("direction")) ?: SortDirection.ASCENDING,
                        pinnedFirst = sort?.bool("pinnedFirst") ?: false,
                    ),
                limit = root.long("limit")?.toInt()?.takeIf { it > 0 },
                project = decodeProjection(root),
                parameters = decodeParameters(root, sources),
            )
        }
    }

    fun encodeBinding(binding: LensBinding): StoredValue.Obj =
        obj(
            "lens" to encode(binding.lens),
            "expression" to str(binding.expression.name),
            "ref" to binding.ref?.let { str(it.value) },
        )

    /** An unknown expression falls back to the plainest drawable one rather than losing the container. */
    fun decodeBinding(value: StoredValue.Obj?): LensBinding? {
        val lens = decode(value?.fields?.get("lens")) ?: return null
        val expression = enumOrNull<ExpressionKind>(value?.string("expression")) ?: ExpressionKind.LIST
        return LensBinding(lens, expression, value?.string("ref")?.let(::LensId))
    }

    private fun decodeProjection(root: StoredValue.Obj): Set<ItemField> =
        if (root.fields["project"] == null) {
            ItemField.ALL
        } else {
            root.array("project").mapNotNull { enumOrNull<ItemField>((it as? StoredValue.Str)?.value) }.toSet()
        }

    private fun encodeGroup(group: LensGroup): StoredValue.Obj =
        when (group) {
            LensGroup.None -> obj("type" to str("none"))
            LensGroup.ByGroupKey -> obj("type" to str("group_key"))
            LensGroup.ByDay -> obj("type" to str("day"))
            is LensGroup.ByExt -> obj("type" to str("ext"), "key" to str(group.key.value))
        }

    private fun decodeGroup(value: StoredValue.Obj?): LensGroup =
        guarded {
            when (value?.string("type")) {
                "group_key" -> LensGroup.ByGroupKey
                "day" -> LensGroup.ByDay
                "ext" -> value.string("key")?.let { LensGroup.ByExt(ItemExtKey(it)) }
                else -> null
            }
        } ?: LensGroup.None
}
