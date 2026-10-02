package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement

/** One-line descriptions of what a workspace holds, for the overview and for screen readers. */
internal object EditorDescribe {
    fun bindingText(binding: LensBinding): String =
        "${EditorText.expressionLabel(binding.expression)} of " +
            binding.lens.sources.joinToString(", ") { EditorText.sourceLabel(it) }

    /** The binding a bound page or a page-set draws; null for a grid of widgets (each widget has its own). */
    fun pageBinding(page: PageHost): LensBinding? =
        when (page) {
            is PageSetContainer -> page.binding
            is PageContainer -> (page.content as? PageContent.Bound)?.binding
        }

    /** "Page 2" (1-based) so unnamed pages can be told apart. */
    fun pageName(index: Int): String = "Page ${index + 1}"

    fun page(page: PageHost): String =
        when (page) {
            is PageSetContainer -> "Page per group: ${bindingText(page.binding)}"
            is PageContainer ->
                when (val content = page.content) {
                    is PageContent.Bound ->
                        (if (page.role == PageRole.FINDER) "${EditorText.FINDER_PAGE_LABEL}: " else "") +
                            bindingText(content.binding)
                    is PageContent.WidgetGrid ->
                        "Widgets: ${content.placements.size} in a ${content.columns} by ${content.rows} grid"
                }
        }

    fun widget(placement: WidgetPlacement): String =
        "${bindingText(placement.widget.binding)} (column ${placement.column + 1}, row ${placement.row + 1})"

    fun dock(binding: LensBinding?): String = binding?.let { bindingText(it) } ?: EditorText.DOCK_SECTION_EMPTY
}
