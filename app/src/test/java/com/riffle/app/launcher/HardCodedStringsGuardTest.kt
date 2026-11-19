package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * User-visible text belongs in `strings.xml`. This scans the launcher's UI code for string literals handed straight to
 * `text`, `label`, `title`, `subtitle`, `contentDescription` and `Text(...)`, and fails when a file holds more than it
 * did when the guard was added. [REMAINING] is the list of what is still literal: sample data for a preview, glyphs
 * and animation labels, presentation helpers with pure unit tests, and text with several parts. Move one to
 * resources and lower its number; never raise one. The diagnostics pages behind Developer options are not scanned.
 */
class HardCodedStringsGuardTest {
    private val call =
        Regex(
            """(?:\b(?:text|label|title|subtitle|contentDescription|description|placeholder|supportingText|""" +
                """actionLabel|stateDescription)\s*=\s*|\bText\(\s*|SettingsButtonText\(\s*|""" +
                """ShortcutContextMenuItem\(\s*)"((?:[^"\\]|\\.)*)"""",
        )
    private val letters = Regex("[A-Za-z]{2}")
    private val identifier = Regex("[A-Za-z0-9_.:\\-]+")

    private fun isWords(literal: String): Boolean {
        if (!letters.containsMatchIn(literal)) return false
        val tagLike =
            identifier.matches(literal) && ' ' !in literal &&
                (literal.all { !it.isUpperCase() } || literal.any { it in "-_.:" })
        return !tagLike
    }

    private fun literalsByFile(): Map<String, Int> =
        File("src/main/java")
            .walkTopDown()
            .filter { it.extension == "kt" && "/diagnostics/" !in it.invariantSeparatorsPath }
            .associate { file ->
                file.name to
                    file.readLines()
                        .filterNot {
                                line ->
                            line.trim().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
                        }
                        .sumOf { line -> call.findAll(line).count { isWords(it.groupValues[1]) } }
            }
            .filterValues { it > 0 }

    @Test
    fun noFileGainsAHardCodedUserVisibleString() {
        val found = literalsByFile()
        val worse = found.filter { (file, count) -> count > (REMAINING[file] ?: 0) }
        assertEquals("Move these literals to strings.xml: $worse", emptyMap<String, Int>(), worse)
    }

    @Test
    fun theRemainingListShrinksAsStringsMove() {
        val found = literalsByFile()
        val stale = REMAINING.filter { (file, count) -> (found[file] ?: 0) < count }
        assertEquals("Lower these numbers in REMAINING: $stale", emptyMap<String, Int>(), stale)
    }

    private companion object {
        val REMAINING =
            mapOf(
                "DockAppearance.kt" to 1,
                "DockNotificationCards.kt" to 1,
                "HomeDock.kt" to 3,
                "HomeDockItemState.kt" to 1,
                "LauncherSearchSurface.kt" to 2,
                "LauncherShell.kt" to 3,
                "OverlayDockSetting.kt" to 4,
                "SearchGridResults.kt" to 1,
                "SearchStackCards.kt" to 1,
                "SettingsLayoutDeviceTabs.kt" to 2,
                "SettingsWallpaperMotionSetting.kt" to 2,
            )
    }
}
