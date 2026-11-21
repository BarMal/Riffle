package com.riffle.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest's entry points: a task created from a LAUNCHER intent is a standard task, so the launcher icon must not
 * be on the activity that is the Home. Exactly one activity carries the LAUNCHER filter, and it is not `MainActivity`.
 */
class AndroidManifestEntryTest {
    private val activities: List<Element> by lazy {
        val document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                File("src/main/AndroidManifest.xml"),
            )
        val nodes = document.getElementsByTagName("activity")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.name(): String = getAttribute("android:name")

    private fun Element.categories(): List<Set<String>> {
        val filters = getElementsByTagName("intent-filter")
        return (0 until filters.length).map { index ->
            val categories = (filters.item(index) as Element).getElementsByTagName("category")
            (0 until categories.length).map { (categories.item(it) as Element).getAttribute("android:name") }.toSet()
        }
    }

    private val launcher = "android.intent.category.LAUNCHER"
    private val home = "android.intent.category.HOME"

    @Test
    fun mainActivityHasNoLauncherFilterAndKeepsItsHomeFilter() {
        val main = activities.single { it.name() == ".MainActivity" }

        assertTrue(main.categories().none { launcher in it })
        assertEquals(listOf(setOf(home, "android.intent.category.DEFAULT")), main.categories())
    }

    @Test
    fun exactlyOneActivityHasTheLauncherFilterAndItIsTheEntryActivity() {
        val withLauncher = activities.filter { activity -> activity.categories().any { launcher in it } }

        assertEquals(listOf(".LauncherEntryActivity"), withLauncher.map { it.name() })
        val entry = withLauncher.single()
        assertEquals("true", entry.getAttribute("android:exported"))
        assertEquals("true", entry.getAttribute("android:noHistory"))
        assertEquals("true", entry.getAttribute("android:excludeFromRecents"))
        assertTrue("an own, empty task affinity", entry.hasAttribute("android:taskAffinity"))
        assertEquals("", entry.getAttribute("android:taskAffinity"))
        assertFalse("the entry activity is not a Home", entry.categories().any { home in it })
    }
}
