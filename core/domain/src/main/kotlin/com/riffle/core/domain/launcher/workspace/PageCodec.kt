package com.riffle.core.domain.launcher.workspace

internal object PageCodec {
    fun encode(page: PageHost): StoredValue.Obj =
        when (page) {
            is PageSetContainer ->
                obj(
                    "type" to str("page_set"),
                    "id" to str(page.id.value),
                    "binding" to LensCodec.encodeBinding(page.binding),
                )
            is PageContainer ->
                obj(
                    "type" to str("page"),
                    "id" to str(page.id.value),
                    "role" to str(page.role.name),
                    "content" to encodeContent(page.content),
                )
        }

    /** Returns null for unknown page types and pages missing an id or content. */
    fun decode(value: StoredValue): PageHost? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            val id = ContainerId(root.string("id") ?: return@guarded null)
            when (root.string("type")) {
                "page_set" -> LensCodec.decodeBinding(root.obj("binding"))?.let { PageSetContainer(id, it) }
                "page" ->
                    decodeContent(root.obj("content"))?.let {
                        PageContainer(id, it, enumOrNull<PageRole>(root.string("role")) ?: PageRole.STANDARD)
                    }
                else -> null
            }
        }
    }

    private fun encodeContent(content: PageContent): StoredValue.Obj =
        when (content) {
            is PageContent.Bound -> obj("type" to str("bound"), "binding" to LensCodec.encodeBinding(content.binding))
            is PageContent.WidgetGrid ->
                obj(
                    "type" to str("grid"),
                    "columns" to num(content.columns),
                    "rows" to num(content.rows),
                    "widgets" to arr(content.placements.map(::encodePlacement)),
                )
        }

    private fun decodeContent(content: StoredValue.Obj?): PageContent? =
        when (content?.string("type")) {
            "bound" -> LensCodec.decodeBinding(content.obj("binding"))?.let(PageContent::Bound)
            "grid" -> {
                val columns = content.long("columns")?.toInt()
                val rows = content.long("rows")?.toInt()
                if (columns == null || rows == null) {
                    null
                } else {
                    PageContent.WidgetGrid(columns, rows, content.array("widgets").mapNotNull(::decodePlacement))
                }
            }
            else -> null
        }

    private fun encodePlacement(placement: WidgetPlacement): StoredValue.Obj =
        obj(
            "id" to str(placement.widget.id.value),
            "column" to num(placement.column),
            "row" to num(placement.row),
            "columns" to num(placement.widget.span.columns),
            "rows" to num(placement.widget.span.rows),
            "binding" to LensCodec.encodeBinding(placement.widget.binding),
        )

    private fun decodePlacement(value: StoredValue): WidgetPlacement? {
        val root = value as? StoredValue.Obj ?: return null
        return guarded {
            val id = root.string("id") ?: return@guarded null
            val binding = LensCodec.decodeBinding(root.obj("binding")) ?: return@guarded null
            val column = root.long("column")?.toInt() ?: return@guarded null
            val row = root.long("row")?.toInt() ?: return@guarded null
            val span = WidgetSpan(root.long("columns")?.toInt() ?: 1, root.long("rows")?.toInt() ?: 1)
            WidgetPlacement(WidgetContainer(ContainerId(id), span, binding), column, row)
        }
    }
}
