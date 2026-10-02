package com.riffle.core.domain.launcher.workspace.sources.ics

/** One unfolded iCalendar content line: `NAME;PARAM=value:value`. Names and parameter names are upper-cased. */
internal class IcsProperty(
    val name: String,
    val params: Map<String, String>,
    val value: String,
)

/**
 * RFC 5545 section 3.1 line handling: unfolding (a line starting with a space or tab continues the previous
 * one) and splitting. Both are bounded: a logical line longer than [IcsLimits.MAX_LINE_CHARS] is dropped, and
 * nothing here throws.
 */
internal object IcsContentLines {
    /** Unfolded lines, oversize ones removed. Lazy: callers stop reading when they hit a limit. */
    fun unfold(text: String): Sequence<String> =
        sequence {
            val current = StringBuilder()
            var oversize = false
            var pending = false
            for (raw in text.lineSequence()) {
                val continuation = raw.isNotEmpty() && (raw[0] == ' ' || raw[0] == '\t')
                if (continuation && pending) {
                    if (current.length + raw.length > IcsLimits.MAX_LINE_CHARS) oversize = true
                    if (!oversize) current.append(raw, 1, raw.length)
                } else {
                    if (pending && !oversize) yield(current.toString())
                    current.setLength(0)
                    oversize = raw.length > IcsLimits.MAX_LINE_CHARS
                    pending = true
                    if (!oversize) current.append(raw)
                }
            }
            if (pending && !oversize) yield(current.toString())
        }

    /** Splits a content line, or null when it has no `:` or an empty name. Quoted parameter values may hold `:`. */
    fun parse(line: String): IcsProperty? {
        var i = 0
        while (i < line.length && line[i] !in ";:") i++
        if (i == 0 || i >= line.length) return null
        val name = line.substring(0, i).trim().uppercase()
        val params = LinkedHashMap<String, String>()
        while (i < line.length && line[i] == ';') {
            i = readParam(line, i + 1, params)
        }
        return if (i < line.length && line[i] == ':') IcsProperty(name, params, line.substring(i + 1)) else null
    }

    private fun readParam(
        line: String,
        from: Int,
        into: MutableMap<String, String>,
    ): Int {
        var i = from
        while (i < line.length && line[i] !in "=;:") i++
        val paramName = line.substring(from, i).trim().uppercase()
        if (i >= line.length || line[i] != '=') return i
        i++
        val value = StringBuilder()
        var quoted = false
        while (i < line.length && (quoted || line[i] !in ";:")) {
            if (line[i] == '"') quoted = !quoted else value.append(line[i])
            i++
        }
        if (paramName.isNotEmpty() && paramName !in into) into[paramName] = value.toString()
        return i
    }

    /** RFC 5545 section 3.3.11 unescaping, with control characters and line breaks reduced to spaces. */
    fun text(
        raw: String,
        maxChars: Int = IcsLimits.MAX_TEXT_CHARS,
    ): String {
        val out = StringBuilder()
        var i = 0
        while (i < raw.length && out.length < maxChars) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                out.append(if (next == 'n' || next == 'N') ' ' else next)
                i += 2
            } else {
                out.append(if (c.isISOControl()) ' ' else c)
                i++
            }
        }
        return out.toString().replace(WHITESPACE, " ").trim()
    }

    private val WHITESPACE = Regex("\\s+")
}
