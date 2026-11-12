package com.riffle.app.launcher

import com.riffle.app.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The real string resources for JVM tests that have no Android resources: the ids from [R.string] are mapped to their
 * names by reflection and the values read from `strings.xml`, so a test sees exactly the text a user sees.
 */
internal object SettingsTestText : SettingsText {
    private val namesById: Map<Int, String> by lazy {
        R.string::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }
    }

    val values: Map<String, String> by lazy {
        val file = File("src/main/res/values/strings.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to
                node.textContent.replace("\\\"", "\"").replace("\\'", "'")
        }
    }

    fun valueOf(name: String): String = requireNotNull(values[name]) { "No string named $name" }

    override fun text(
        id: Int,
        vararg args: Any,
    ): String {
        val name = requireNotNull(namesById[id]) { "No string with id $id" }
        val raw = valueOf(name)
        return if (args.isEmpty()) raw else String.format(raw, *args)
    }
}
