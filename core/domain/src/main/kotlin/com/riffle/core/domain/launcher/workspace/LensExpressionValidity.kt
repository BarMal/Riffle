package com.riffle.core.domain.launcher.workspace

sealed interface LensIssue {
    /** The lens does not project a field the expression requires. */
    data class MissingRequiredField(val field: ItemField) : LensIssue

    /** None of the shapes the lens can produce is drawn by the expression. */
    data class ShapeNotAccepted(
        val produced: Set<ResultShape>,
        val accepted: Set<ResultShape>,
    ) : LensIssue

    /** Grouping by group key needs every source to be groupable. */
    data class SourceNotGroupable(val sourceId: SourceId) : LensIssue

    data class UnknownSource(val sourceId: SourceId) : LensIssue
}

sealed interface LensValidity {
    data object Valid : LensValidity

    data class Invalid(val issues: List<LensIssue>) : LensValidity

    val isValid: Boolean get() = this is Valid
}

/**
 * Decides whether a [Lens] and an expression can be paired: the lens result must satisfy both the
 * expression's `requires` and `accepts`. The editor filters its choices with this, so invalid
 * combinations cannot be built; containers and workspaces re-check it so stored data cannot bypass it.
 */
object LensExpressionValidity {
    /**
     * @param sources known source descriptors. When null, source-dependent checks (unknown and
     * groupable sources) are skipped, which lets the editor test a pairing before sources resolve.
     */
    fun check(
        lens: Lens,
        expression: ExpressionDescriptor,
        sources: List<SourceDescriptor>? = null,
    ): LensValidity {
        val issues =
            buildList {
                (expression.requires - lens.project).sortedBy { it.ordinal }.forEach {
                    add(
                        LensIssue.MissingRequiredField(it),
                    )
                }
                val produced = lens.resultShapes
                if (produced.none { it in expression.accepts }) {
                    add(
                        LensIssue.ShapeNotAccepted(produced, expression.accepts),
                    )
                }
                if (sources != null) addAll(sourceIssues(lens, sources))
            }
        return if (issues.isEmpty()) LensValidity.Valid else LensValidity.Invalid(issues)
    }

    fun check(
        lens: Lens,
        kind: ExpressionKind,
        sources: List<SourceDescriptor>? = null,
    ): LensValidity = check(lens, ExpressionCatalog.descriptorFor(kind), sources)

    /** The expressions the editor may offer for [lens]. */
    fun compatibleExpressions(
        lens: Lens,
        sources: List<SourceDescriptor>? = null,
    ): List<ExpressionKind> = ExpressionKind.entries.filter { check(lens, it, sources).isValid }

    private fun sourceIssues(
        lens: Lens,
        sources: List<SourceDescriptor>,
    ): List<LensIssue> {
        val byId = sources.associateBy { it.id }
        return lens.sources.distinct().mapNotNull { id ->
            val descriptor = byId[id]
            when {
                descriptor == null -> LensIssue.UnknownSource(id)
                lens.group == LensGroup.ByGroupKey && SourceCapability.GROUPABLE !in descriptor.capabilities ->
                    LensIssue.SourceNotGroupable(id)
                else -> null
            }
        }
    }
}
