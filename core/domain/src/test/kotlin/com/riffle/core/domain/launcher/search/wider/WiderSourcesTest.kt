package com.riffle.core.domain.launcher.search.wider

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WiderSourcesTest {
    private val entries =
        listOf(
            SystemSettingEntry(
                "wifi",
                "Wi-Fi",
                listOf("internet", "network", "wireless"),
                "android.settings.WIFI_SETTINGS",
            ),
            SystemSettingEntry(
                "display",
                "Display",
                listOf("brightness", "dark theme"),
                "android.settings.DISPLAY_SETTINGS",
            ),
            SystemSettingEntry("date", "Date and time", listOf("clock", "time zone"), "android.settings.DATE_SETTINGS"),
        )

    @Test
    fun systemSettingsMatchByTitleAndKeywordBestFirst() =
        runTest {
            val source = SystemSettingsSource({ entries })

            suspend fun titles(query: String) =
                source.query(query, 5).sortedByDescending { it.score }
                    .map { (it.result as WiderSearchResult.SystemSetting).title }
            assertEquals(listOf("Wi-Fi"), titles("wi"))
            assertEquals(listOf("Display"), titles("bright"))
            assertEquals(listOf("Date and time"), titles("time"))
            assertEquals(emptyList(), titles("zzz"))
            assertTrue(
                source.query("dis", 5).single().score > source.query("bright", 5).single().score,
            )
        }

    @Test
    fun aSettingOffersOnlyTheEntriesGivenToIt() =
        runTest {
            assertEquals(emptyList(), SystemSettingsSource({ emptyList() }).query("wifi", 5))
        }

    private val contacts =
        listOf(
            ContactRecord("content://c/1", "Zoë Example", "+15550100", null),
            ContactRecord("content://c/2", "Anna Beispiel", null, "content://photo/2"),
            ContactRecord("content://c/3", "Maria Zoe Sample", "+15550101", null),
            ContactRecord("content://c/4", "Lazo Test", null, null),
        )

    private suspend fun contactNames(query: String): List<String> =
        ContactsSource({ _, _ -> contacts }).query(query, 5)
            .sortedByDescending { it.score }
            .map { (it.result as WiderSearchResult.Contact).name }

    @Test
    fun contactsMatchByPrefixAndWordPrefixIgnoringAccentsAndCase() =
        runTest {
            assertEquals(listOf("Zoë Example", "Maria Zoe Sample"), contactNames("zoe"))
            assertEquals(listOf("Zoë Example"), contactNames("ZOË ex"))
            assertEquals(listOf("Anna Beispiel"), contactNames("bei"))
            assertEquals(listOf("Anna Beispiel"), contactNames("beispiel anna"))
        }

    @Test
    fun contactsDoNotMatchInTheMiddleOfAWord() =
        runTest {
            assertEquals(emptyList(), contactNames("azo"))
            assertEquals(emptyList(), contactNames("zo xq"))
            assertEquals(listOf("Maria Zoe Sample"), contactNames("zo ma"))
        }

    @Test
    fun contactsCarryTheNumberAndPhotoOnlyWhenTheyHaveThem() =
        runTest {
            val results =
                ContactsSource(
                    { _, _ -> contacts },
                ).query("anna", 5).map { it.result as WiderSearchResult.Contact }
            assertNull(results.single().number)
            assertEquals("content://photo/2", results.single().photoUri)
        }

    @Test
    fun contactsAreReadWithABoundedRowLimitAndNothingIsKeptBetweenQueries() =
        runTest {
            val limits = mutableListOf<Int>()
            val seen = mutableListOf<String>()
            val source =
                ContactsSource(
                    ContactsReader { query, limit ->
                        limits += limit
                        seen += query
                        contacts
                    },
                )
            source.query("zoe", 5)
            source.query("anna", 5)
            assertEquals(listOf(ContactsSource.READ_LIMIT, ContactsSource.READ_LIMIT), limits)
            assertEquals(listOf("zoe", "anna"), seen)
            // The second answer holds nothing of the first.
            assertTrue(source.query("anna", 5).none { (it.result as WiderSearchResult.Contact).name.startsWith("Zo") })
        }

    @Test
    fun filesMatchByNameAndAlwaysEndWithTheFilesAppRow() =
        runTest {
            val files =
                listOf(
                    FileRecord("content://f/1", "Trip report.pdf", "application/pdf", FileKind.DOWNLOAD, 10),
                    FileRecord("content://f/2", "holiday photo.jpg", "image/jpeg", FileKind.IMAGE, 9),
                    FileRecord("content://f/3", "My report draft.txt", "text/plain", FileKind.DOCUMENT, 8),
                )
            val results = FilesSource({ _, _ -> files }).query("report", 5)
            val ordered = results.sortedByDescending { it.score }.map { it.result }
            assertEquals(
                listOf("Trip report.pdf", "My report draft.txt"),
                ordered.filterIsInstance<WiderSearchResult.FileHit>().map { it.name },
            )
            assertTrue(ordered.last() is WiderSearchResult.FilesApp)
            assertEquals("report", (ordered.last() as WiderSearchResult.FilesApp).query)
        }

    @Test
    fun withNothingVisibleFilesOffersOnlyTheFilesAppRow() =
        runTest {
            val results = FilesSource({ _, _ -> emptyList() }).query("report", 5)
            assertEquals(1, results.size)
            assertNotNull(results.single().result as? WiderSearchResult.FilesApp)
        }

    @Test
    fun theCalculatorSourceAnswersArithmeticAndUnitsAndNothingElse() =
        runTest {
            val source = CalculatorSource()
            assertEquals(
                "4",
                (source.query("2+2", 1).single().result as WiderSearchResult.CalculatorAnswer).answer,
            )
            assertEquals(
                "7.456454 mi",
                (source.query("12 km in miles", 1).single().result as WiderSearchResult.UnitConversion).answer,
            )
            assertEquals(emptyList(), source.query("calendar", 1))
            assertEquals(emptyList(), source.query("1/0", 1))
        }
}
