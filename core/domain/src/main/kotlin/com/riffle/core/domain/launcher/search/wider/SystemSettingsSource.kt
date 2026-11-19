package com.riffle.core.domain.launcher.search.wider

/** A system settings screen the device can open: its words are already in the user's language. */
data class SystemSettingEntry(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val intentAction: String,
)

/** The system's own settings screens, found by title and keyword. Answers from memory, so it is not debounced. */
class SystemSettingsSource(
    private val entries: () -> List<SystemSettingEntry>,
    override val maxResults: Int = SearchSource.DEFAULT_MAX_RESULTS,
) : SearchSource {
    override val id: SearchSourceId = SearchSourceId.SYSTEM_SETTINGS

    override suspend fun query(
        query: String,
        limit: Int,
    ): List<ScoredResult> =
        entries().mapNotNull { entry ->
            WiderMatching.titleScore(entry.title, entry.keywords, query)?.let { score ->
                ScoredResult(WiderSearchResult.SystemSetting(entry.id, entry.title, entry.intentAction), score)
            }
        }
}

/** The calculator and the unit converter, one source: a query is at most one of the two. */
class CalculatorSource(
    private val symbols: () -> NumberSymbols = { NumberSymbols.DOT_DECIMAL },
) : SearchSource {
    override val id: SearchSourceId = SearchSourceId.CALCULATOR
    override val maxResults: Int = 1
    override val minQueryLength: Int = MIN_LENGTH

    override suspend fun query(
        query: String,
        limit: Int,
    ): List<ScoredResult> {
        val numbers = symbols()
        val arithmetic = Calculator.evaluate(query, numbers)
        val conversion = if (arithmetic == null) UnitConverter.convert(query, numbers) else null
        val result =
            when {
                arithmetic != null -> WiderSearchResult.CalculatorAnswer(query.trim(), arithmetic.text)
                conversion != null -> WiderSearchResult.UnitConversion(conversion.input, conversion.answer)
                else -> null
            }
        return listOfNotNull(result?.let { ScoredResult(it, WiderMatching.FULL_PREFIX) })
    }

    private companion object {
        const val MIN_LENGTH = 3
    }
}
