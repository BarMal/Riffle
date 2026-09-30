package com.riffle.core.domain.launcher.workspace

const val CURRENT_WORKSPACE_SCHEMA_VERSION = 1

/**
 * Encodes and decodes [Lens] and [Workspace] to [StoredValue].
 *
 * Decoding is safe: unknown enum values and unknown filter or group types fall back to a default,
 * malformed containers and unrecoverable entries are dropped, and it never throws. There is
 * deliberately no codec for [Item]: item content is transient and must never be persisted.
 */
object WorkspaceCodec {
    private const val MAX_FILTER_DEPTH = 8

    // ---- encode ----

    fun encode(workspace: Workspace): StoredValue.Obj =
        obj(
            "version" to num(CURRENT_WORKSPACE_SCHEMA_VERSION),
            "id" to str(workspace.id.value),
            "name" to str(workspace.name),
            "pages" to StoredValue.Arr(workspace.pages.map(::encodePage)),
            "dock" to
                obj(
                    *listOfNotNull(
                        workspace.dock.dynamicSection?.let { "dynamic" to encodeBinding(it) },
                    ).toTypedArray(),
                ),
            "gestures" to obj(*workspace.gestureBindings.map { (k, v) -> k to str(v) }.toTypedArray()),
            *listOfNotNull(workspace.skinOverrideId?.let { "skin" to str(it) }).toTypedArray(),
        )

    fun encode(lens: Lens): StoredValue.Obj =
        obj(
            "sources" to StoredValue.Arr(lens.sources.map { str(it.value) }),
            "filter" to encodeFilter(lens.filter),
            "group" to encodeGroup(lens.group),
            "sort" to
                obj(
                    "field" to str(lens.sort.field.name),
                    "direction" to str(lens.sort.direction.name),
                    "pinnedFirst" to StoredValue.Bool(lens.sort.pinnedFirst),
                ),
            *listOfNotNull(lens.limit?.let { "limit" to num(it) }).toTypedArray(),
            "project" to StoredValue.Arr(lens.project.sortedBy { it.ordinal }.map { str(it.name) }),
        )

    private fun encodeBinding(binding: LensBinding): StoredValue.Obj =
        obj("lens" to encode(binding.lens), "expression" to str(binding.expression.name))

    private fun encodePage(page: PageHost): StoredValue.Obj =
        when (page) {
            is PageSetContainer ->
                obj("type" to str("page_set"), "id" to str(page.id.value), "binding" to encodeBinding(page.binding))
            is PageContainer ->
                obj(
                    "type" to str("page"),
                    "id" to str(page.id.value),
                    "role" to str(page.role.name),
                    "content" to
                        when (val content = page.content) {
                            is PageContent.Bound ->
                                obj("type" to str("bound"), "binding" to encodeBinding(content.binding))
                            is PageContent.WidgetGrid ->
                                obj(
                                    "type" to str("grid"),
                                    "columns" to num(content.columns),
                                    "rows" to num(content.rows),
                                    "widgets" to StoredValue.Arr(content.placements.map(::encodePlacement)),
                                )
                        },
                )
        }

    private fun encodePlacement(placement: WidgetPlacement): StoredValue.Obj =
        obj(
            "id" to str(placement.widget.id.value),
            "column" to num(placement.column),
            "row" to num(placement.row),
            "columns" to num(placement.widget.span.columns),
            "rows" to num(placement.widget.span.rows),
            "binding" to encodeBinding(placement.widget.binding),
        )

    private fun encodeFilter(filter: LensFilter): StoredValue.Obj =
        when (filter) {
            LensFilter.All -> obj("type" to str("all"))
            is LensFilter.AllOf ->
                obj(
                    "type" to str("all_of"),
                    "filters" to StoredValue.Arr(filter.filters.map(::encodeFilter)),
                )
            is LensFilter.AnyOf ->
                obj(
                    "type" to str("any_of"),
                    "filters" to StoredValue.Arr(filter.filters.map(::encodeFilter)),
                )
            is LensFilter.Not -> obj("type" to str("not"), "filter" to encodeFilter(filter.filter))
            is LensFilter.FromSource -> obj("type" to str("from_source"), "source" to str(filter.sourceId.value))
            is LensFilter.GroupKeyIs -> obj("type" to str("group_key_is"), "key" to str(filter.key))
            is LensFilter.HasActions ->
                obj(
                    "type" to str("has_actions"),
                    "required" to StoredValue.Bool(filter.required),
                )
            is LensFilter.AgeAtMost -> obj("type" to str("age_at_most"), "millis" to num(filter.millis))
            is LensFilter.AgeAtLeast -> obj("type" to str("age_at_least"), "millis" to num(filter.millis))
            is LensFilter.PrivacyIs -> obj("type" to str("privacy_is"), "privacy" to str(filter.privacy.name))
            is LensFilter.ExtEquals ->
                obj("type" to str("ext_equals"), "key" to str(filter.key.value), "value" to encodeExt(filter.value))
        }

    private fun encodeExt(value: ItemExtValue): StoredValue.Obj =
        when (value) {
            is ItemExtValue.Text -> obj("text" to str(value.value))
            is ItemExtValue.Number -> obj("number" to StoredValue.Num(value.value))
            is ItemExtValue.Flag -> obj("flag" to StoredValue.Bool(value.value))
        }

    private fun encodeGroup(group: LensGroup): StoredValue.Obj =
        when (group) {
            LensGroup.None -> obj("type" to str("none"))
            LensGroup.ByGroupKey -> obj("type" to str("group_key"))
            LensGroup.ByDay -> obj("type" to str("day"))
            is LensGroup.ByExt -> obj("type" to str("ext"), "key" to str(group.key.value))
        }

    // ---- decode ----

    /** Returns null only when the value has no usable identity (missing or blank id). */
    fun decodeWorkspace(value: StoredValue?): Workspace? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            Workspace(
                id = WorkspaceId(root.string("id") ?: return@guarded null),
                name = root.string("name").orEmpty(),
                pages = root.array("pages").mapNotNull(::decodePage),
                dock = WorkspaceDock(dynamicSection = root.obj("dock")?.obj("dynamic")?.let(::decodeBinding)),
                gestureBindings =
                    root.obj("gestures")?.fields.orEmpty()
                        .mapNotNull { (k, v) -> (v as? StoredValue.Str)?.let { k to it.value } }
                        .toMap(),
                skinOverrideId = root.string("skin"),
            )
        }
    }

    /** Returns null when no usable source remains. */
    fun decodeLens(value: StoredValue?): Lens? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            val sources =
                root.array(
                    "sources",
                ).mapNotNull { (it as? StoredValue.Str)?.value?.takeIf(String::isNotBlank) }
            if (sources.isEmpty()) return@guarded null
            val sort = root.obj("sort")
            Lens(
                sources = sources.map(::SourceId),
                filter = decodeFilter(root.obj("filter"), 0),
                group = decodeGroup(root.obj("group")),
                sort =
                    LensSort(
                        field = sort.enum("field", LensSortField.SOURCE_ORDER),
                        direction = sort.enum("direction", SortDirection.ASCENDING),
                        pinnedFirst =
                            sort?.fields?.get(
                                "pinnedFirst",
                            ).let { (it as? StoredValue.Bool)?.value ?: false },
                    ),
                limit = root.long("limit")?.toInt()?.takeIf { it > 0 },
                project =
                    if (root.fields["project"] == null) {
                        ItemField.ALL
                    } else {
                        root.array("project").mapNotNull {
                                v ->
                            (v as? StoredValue.Str)?.value?.let {
                                    n ->
                                ItemField.entries.firstOrNull { it.name == n }
                            }
                        }.toSet()
                    },
            )
        }
    }

    private fun decodeBinding(value: StoredValue.Obj): LensBinding? {
        val lens = decodeLens(value.fields["lens"]) ?: return null
        // An unknown expression falls back to the plainest drawable one rather than losing the container.
        return LensBinding(lens, value.enum("expression", ExpressionKind.LIST))
    }

    private fun decodePage(value: StoredValue): PageHost? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            val id = ContainerId(root.string("id") ?: return@guarded null)
            when (root.string("type")) {
                "page_set" ->
                    PageSetContainer(
                        id,
                        decodeBinding(root.obj("binding") ?: return@guarded null) ?: return@guarded null,
                    )
                "page" -> {
                    val content = root.obj("content") ?: return@guarded null
                    PageContainer(
                        id = id,
                        role = root.enum("role", PageRole.STANDARD),
                        content =
                            when (content.string("type")) {
                                "bound" ->
                                    PageContent.Bound(
                                        decodeBinding(content.obj("binding") ?: return@guarded null)
                                            ?: return@guarded null,
                                    )
                                "grid" ->
                                    PageContent.WidgetGrid(
                                        columns = content.long("columns")?.toInt() ?: return@guarded null,
                                        rows = content.long("rows")?.toInt() ?: return@guarded null,
                                        placements = content.array("widgets").mapNotNull(::decodePlacement),
                                    )
                                else -> return@guarded null
                            },
                    )
                }
                else -> null
            }
        }
    }

    private fun decodePlacement(value: StoredValue): WidgetPlacement? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            WidgetPlacement(
                widget =
                    WidgetContainer(
                        id = ContainerId(root.string("id") ?: return@guarded null),
                        span = WidgetSpan(root.long("columns")?.toInt() ?: 1, root.long("rows")?.toInt() ?: 1),
                        binding = decodeBinding(root.obj("binding") ?: return@guarded null) ?: return@guarded null,
                    ),
                column = root.long("column")?.toInt() ?: return@guarded null,
                row = root.long("row")?.toInt() ?: return@guarded null,
            )
        }
    }

    private fun decodeFilter(
        value: StoredValue.Obj?,
        depth: Int,
    ): LensFilter {
        if (value == null || depth > MAX_FILTER_DEPTH) return LensFilter.All

        fun children() =
            value.array(
                "filters",
            ).mapNotNull { (it as? StoredValue.Obj)?.let { c -> decodeFilter(c, depth + 1) } }
        return guarded {
            when (value.string("type")) {
                "all_of" -> LensFilter.AllOf(children())
                "any_of" -> LensFilter.AnyOf(children())
                "not" -> LensFilter.Not(decodeFilter(value.obj("filter"), depth + 1))
                "from_source" -> LensFilter.FromSource(SourceId(value.string("source") ?: return@guarded null))
                "group_key_is" -> LensFilter.GroupKeyIs(value.string("key") ?: return@guarded null)
                "has_actions" ->
                    LensFilter.HasActions((value.fields["required"] as? StoredValue.Bool)?.value ?: true)
                "age_at_most" -> LensFilter.AgeAtMost(value.long("millis")?.takeIf { it >= 0 } ?: return@guarded null)
                "age_at_least" -> LensFilter.AgeAtLeast(value.long("millis")?.takeIf { it >= 0 } ?: return@guarded null)
                "privacy_is" ->
                    LensFilter.PrivacyIs(enumOrNull<ItemPrivacy>(value.string("privacy")) ?: return@guarded null)
                "ext_equals" ->
                    LensFilter.ExtEquals(
                        ItemExtKey(value.string("key") ?: return@guarded null),
                        decodeExt(value.obj("value")) ?: return@guarded null,
                    )
                else -> null
            }
        } ?: LensFilter.All
    }

    private fun decodeExt(value: StoredValue.Obj?): ItemExtValue? =
        when {
            value == null -> null
            value.string("text") != null -> ItemExtValue.Text(value.string("text").orEmpty())
            value.long("number") != null -> ItemExtValue.Number(value.long("number") ?: 0L)
            else -> (value.fields["flag"] as? StoredValue.Bool)?.let { ItemExtValue.Flag(it.value) }
        }

    private fun decodeGroup(value: StoredValue.Obj?): LensGroup =
        guarded {
            when (value?.string("type")) {
                "group_key" -> LensGroup.ByGroupKey
                "day" -> LensGroup.ByDay
                "ext" -> LensGroup.ByExt(ItemExtKey(value.string("key") ?: return@guarded null))
                else -> null
            }
        } ?: LensGroup.None

    // ---- helpers ----

    /** Constructors validate with require(); a stored value violating them is dropped, never thrown. */
    private inline fun <T> guarded(block: () -> T?): T? =
        try {
            block()
        } catch (_: IllegalArgumentException) {
            null
        }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private inline fun <reified E : Enum<E>> StoredValue.Obj?.enum(
        key: String,
        default: E,
    ): E = enumOrNull<E>(this?.string(key)) ?: default

    private fun StoredValue.Obj.string(key: String): String? =
        (fields[key] as? StoredValue.Str)?.value?.takeIf {
            it.isNotBlank()
        }

    private fun StoredValue.Obj.long(key: String): Long? = (fields[key] as? StoredValue.Num)?.value

    private fun StoredValue.Obj.obj(key: String): StoredValue.Obj? = fields[key] as? StoredValue.Obj

    private fun StoredValue.Obj.array(key: String): List<StoredValue> =
        (fields[key] as? StoredValue.Arr)?.items.orEmpty()

    private fun obj(vararg entries: Pair<String, StoredValue>) = StoredValue.Obj(linkedMapOf(*entries))

    private fun str(value: String) = StoredValue.Str(value)

    private fun num(value: Int) = StoredValue.Num(value.toLong())

    private fun num(value: Long) = StoredValue.Num(value)
}
