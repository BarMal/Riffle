package com.riffle.core.domain.launcher.workspace

/** Name helpers for the lens library: collision-free names and repair of decoded entries. */
internal object LensNames {
    private const val MAX_ATTEMPTS = 1000
    private const val FALLBACK_NAME = "Lens"

    /**
     * [base] when its lower-case form is not in [taken], else [base] plus `suffix(1)`, `suffix(2)`...,
     * truncating [base] so the result still fits [MAX_SAVED_LENS_NAME].
     */
    fun unique(
        base: String,
        taken: Set<String>,
        suffix: (Int) -> String,
    ): String {
        val first = base.trim().take(MAX_SAVED_LENS_NAME).trim().ifEmpty { FALLBACK_NAME }
        val attempts = (1..MAX_ATTEMPTS).asSequence().map { n -> fit(base, suffix(n)) }
        return (sequenceOf(first) + attempts).firstOrNull { it.lowercase() !in taken } ?: first
    }

    /** The lower-case names of [lenses], for collision checks. */
    fun of(lenses: List<SavedLens>): Set<String> = lenses.map { it.name.lowercase() }.toSet()

    private fun fit(
        base: String,
        tail: String,
    ): String = base.trim().take(MAX_SAVED_LENS_NAME - tail.length).trimEnd() + tail

    /** A valid library from possibly inconsistent [entries] (decoded data): bounded, unique ids and names. */
    fun repaired(entries: List<SavedLens>): LensLibrary {
        val seen = HashSet<LensId>()
        val names = HashSet<String>()
        val kept =
            entries.filter { seen.add(it.id) }.take(MAX_SAVED_LENSES).map { entry ->
                val name = unique(entry.name, names) { " ${it + 1}" }
                names += name.lowercase()
                entry.copy(name = name)
            }
        return LensLibrary(kept)
    }
}
