package com.riffle.core.domain.launcher.icons

/** One element of an `appfilter.xml`: its tag name and attributes. */
data class AppFilterTag(
    val name: String,
    val attributes: Map<String, String>,
)

/**
 * What an icon pack's `appfilter.xml` says: which drawable draws which activity, and how to dress the apps it has no
 * drawable for (back plates, a mask, an overlay and a scale). Names are drawable names, never bitmaps.
 */
data class IconPackMapping(
    val packPackage: String,
    /** "package/activity" to a drawable name. */
    val drawables: Map<String, String> = emptyMap(),
    val iconBack: List<String> = emptyList(),
    val iconMask: String? = null,
    val iconUpon: String? = null,
    val scale: Float = 1f,
) {
    fun drawableFor(
        packageName: String,
        activityName: String,
    ): String? {
        // An activity written relative to its package (".Main") is the same activity as the full class name.
        val full = if (activityName.startsWith(".")) packageName + activityName else activityName
        return drawables["$packageName/$full"]
    }

    /** The back plate for [packageName]: stable per package when the pack has several. */
    fun backFor(packageName: String): String? =
        iconBack.takeIf { it.isNotEmpty() }?.let { it[(packageName.hashCode() and Int.MAX_VALUE) % it.size] }

    val dressesUnthemedApps: Boolean get() = iconBack.isNotEmpty() || iconMask != null || iconUpon != null
}

/**
 * Reads the common `appfilter.xml` format, defensively: a malformed file, a missing attribute, an absurd number of
 * entries or a file that is too large never throws, it yields what could be read. Pure, so it runs on the JVM.
 */
object IconPackAppFilter {
    const val MAX_XML_CHARS = 8_000_000
    const val MAX_ENTRIES = 30_000
    const val MAX_NAME_LENGTH = 200
    private const val MIN_SCALE = 0.3f
    private const val MAX_SCALE = 1.6f

    private val componentPattern = Regex("""(?:ComponentInfo\{)?([^/{}\s]+)/([^/{}\s]+?)\}?""")
    private val tagPattern = Regex("""<([A-Za-z][\w.:-]*)((?:\s+[^<>]*?)?)\s*/?>""")
    private val attributePattern = Regex("""([A-Za-z_][\w.:-]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val commentPattern = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val cdataPattern = Regex("""<!\[CDATA\[.*?]]>""", RegexOption.DOT_MATCHES_ALL)
    private val drawableNamePattern = Regex("""[A-Za-z0-9_.]+""")

    /** Reads the tags of [xml] (any element, attributes in order); text over [MAX_XML_CHARS] is not read at all. */
    fun tagsOf(xml: String): List<AppFilterTag> {
        if (xml.length > MAX_XML_CHARS) return emptyList()
        val clean = xml.replace(commentPattern, "").replace(cdataPattern, "")
        return tagPattern.findAll(clean).map { match ->
            val attributes =
                attributePattern.findAll(match.groupValues[2]).associate { attribute ->
                    attribute.groupValues[1] to
                        unescape(attribute.groupValues[2].ifEmpty { attribute.groupValues[3] })
                }
            AppFilterTag(match.groupValues[1], attributes)
        }.take(MAX_ENTRIES * 2).toList()
    }

    fun parse(
        packPackage: String,
        xml: String,
    ): IconPackMapping = parse(packPackage, tagsOf(xml))

    fun parse(
        packPackage: String,
        tags: List<AppFilterTag>,
    ): IconPackMapping {
        val drawables = LinkedHashMap<String, String>()
        val backs = mutableListOf<String>()
        var mask: String? = null
        var upon: String? = null
        var scale = 1f
        for (tag in tags) {
            when (tag.name.lowercase()) {
                "item" -> {
                    if (drawables.size >= MAX_ENTRIES) continue
                    val component = tag.attributes["component"]?.let(::component)
                    val drawable = tag.attributes["drawable"]?.let(::drawableName)
                    // The first entry for an activity wins: later ones are the pack's alternatives.
                    if (component != null && drawable != null) drawables.putIfAbsent(component, drawable)
                }
                "iconback" -> backs += imageNames(tag)
                "iconmask" -> mask = mask ?: imageNames(tag).firstOrNull()
                "iconupon" -> upon = upon ?: imageNames(tag).firstOrNull()
                "scale" ->
                    tag.attributes["factor"]?.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                        scale = it.coerceIn(MIN_SCALE, MAX_SCALE)
                    }
            }
        }
        return IconPackMapping(packPackage, drawables, backs.distinct().take(MAX_ENTRIES), mask, upon, scale)
    }

    /** The drawable names a pack lists in its `drawable.xml` (`<item drawable="name"/>`), unique, in file order. */
    fun drawableList(tags: List<AppFilterTag>): List<String> =
        tags.asSequence()
            .filter { it.name.equals("item", ignoreCase = true) }
            .mapNotNull { it.attributes["drawable"]?.let(::drawableName) }
            .distinct()
            .take(MAX_ENTRIES)
            .toList()

    /** "ComponentInfo{pkg/activity}" (or a bare "pkg/activity", or ".Activity" relative to the package) normalised. */
    fun component(raw: String): String? {
        val match = componentPattern.matchEntire(raw.trim()) ?: return null
        val packageName = match.groupValues[1]
        val activity = match.groupValues[2].let { if (it.startsWith(".")) packageName + it else it }
        val tooLong = packageName.length > MAX_NAME_LENGTH || activity.length > MAX_NAME_LENGTH
        return if (tooLong) null else "$packageName/$activity"
    }

    private fun imageNames(tag: AppFilterTag): List<String> =
        tag.attributes.entries
            .filter { it.key.startsWith("img") }
            .sortedBy { it.key }
            .mapNotNull { drawableName(it.value) }

    private fun drawableName(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH && drawableNamePattern.matches(it) }

    private fun unescape(value: String): String =
        value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
            .replace("&amp;", "&")
}
