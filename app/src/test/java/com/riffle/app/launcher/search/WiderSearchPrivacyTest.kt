package com.riffle.app.launcher.search

import com.riffle.app.launcher.decodeLauncherSettings
import com.riffle.app.launcher.encodeLauncherSettings
import com.riffle.core.domain.launcher.search.wider.ContactRecord
import com.riffle.core.domain.launcher.search.wider.ContactsReader
import com.riffle.core.domain.launcher.search.wider.ContactsSource
import com.riffle.core.domain.launcher.search.wider.FileKind
import com.riffle.core.domain.launcher.search.wider.FileRecord
import com.riffle.core.domain.launcher.search.wider.FilesReader
import com.riffle.core.domain.launcher.search.wider.FilesSource
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.WiderSearchEngine
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.SearchSettings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * What a person types into search and what the wider sources find (contacts above all) is never persisted, logged or
 * put in a backup. The settings that are stored hold switches, an order and a count only.
 */
class WiderSearchPrivacyTest {
    private val secretQuery = "Quentin Exampleton"
    private val secretNumber = "+15550199"

    @Test
    fun runningContactsAndFilesSearchesChangesNoStoredSetting() {
        val before = LauncherSettings(search = SearchSettings(contacts = true, files = true))
        val encodedBefore = encodeLauncherSettings(before)
        val engine =
            WiderSearchEngine(
                listOf(
                    ContactsSource(
                        ContactsReader { _, _ ->
                            listOf(ContactRecord("content://contacts/lookup/q", secretQuery, secretNumber, null))
                        },
                    ),
                    FilesSource(
                        FilesReader { _, _ ->
                            listOf(
                                FileRecord("content://files/q", "$secretQuery.txt", "text/plain", FileKind.DOCUMENT, 1),
                            )
                        },
                    ),
                ),
            )
        val last =
            runBlocking {
                engine.search(secretQuery, setOf(SearchSourceId.CONTACTS, SearchSourceId.FILES), 5).toList().last()
            }
        assertEquals(2, last.results.size)

        // Settings stay exactly as they were, and nothing of the query or the results can be in what they encode.
        assertEquals(encodedBefore, encodeLauncherSettings(before))
        val encoded = encodeLauncherSettings(decodeLauncherSettings(encodedBefore))
        assertFalse(encoded.contains("Quentin"))
        assertFalse(encoded.contains(secretNumber))
    }

    @Test
    fun theSettingsThatAreStoredCarryNoQueryOrResultFields() {
        val keys = org.json.JSONObject(encodeLauncherSettings(LauncherSettings())).getJSONObject("search").keys()
        val stored = keys.asSequence().toSet()
        assertEquals(
            setOf(
                "resultPresentation",
                "launcherSettings",
                "calculator",
                "systemSettings",
                "contacts",
                "files",
                "webHandoff",
                "groupOrder",
                "resultsPerGroup",
            ),
            stored,
        )
    }

    @Test
    fun theWiderSearchCodeNeverWritesLogsOrStoresAnything() {
        val forbidden =
            listOf(
                "android.util.Log",
                "Log.d(",
                "Log.i(",
                "Log.w(",
                "Log.e(",
                "println(",
                "androidx.datastore",
                "SharedPreferences",
                "FileOutputStream",
                "writeText(",
                "openFileOutput",
                "saveLauncherSettings",
                "Timber",
                "Base64",
            )
        val sources =
            listOf(
                File("src/main/java/com/riffle/app/launcher/search/wider"),
                File("../core/domain/src/main/kotlin/com/riffle/core/domain/launcher/search/wider"),
            ).flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertFalse("found no wider search sources", sources.isEmpty())
        sources.forEach { file ->
            val text = file.readText()
            forbidden.forEach { token ->
                assertFalse("${file.name} must not use $token", text.contains(token))
            }
        }
    }

    @Test
    fun noPersistedStoreOrBackupFileMentionsTheWiderResultTypes() {
        val stores =
            File("src/main/java/com/riffle/app")
                .walkTopDown()
                .filter { file ->
                    file.extension == "kt" &&
                        (
                            file.name.contains("JsonCodec") || file.name.contains("DataStore") ||
                                file.name.contains("Backup")
                        )
                }
                .toList()
        assertFalse(stores.isEmpty())
        stores.forEach { file ->
            val text = file.readText()
            listOf("WiderSearchResult", "ContactRecord", "FileRecord", "WiderSearchSnapshot").forEach { type ->
                assertFalse("${file.name} must not persist $type", text.contains(type))
            }
        }
    }
}
