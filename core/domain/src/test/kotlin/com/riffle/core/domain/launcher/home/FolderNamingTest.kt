package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderNamingTest {
    private fun app(
        name: String,
        category: String?,
    ) = InstalledApp(AppIdentity(AppPackageName("p.$name"), AppActivityName("Main")), name, category = category)

    @Test
    fun aSharedCategoryNamesTheFolder() {
        assertEquals("Social", FolderNaming.suggested(app("a", "Social"), app("b", " Social ")))
    }

    @Test
    fun differentMissingOrUnknownCategoriesGiveTheDefault() {
        assertEquals("Folder", FolderNaming.suggested(app("a", "Social"), app("b", "Audio")))
        assertEquals("Folder", FolderNaming.suggested(app("a", null), app("b", null)))
        assertEquals("Folder", FolderNaming.suggested(app("a", "Social"), null))
        assertEquals("Mappe", FolderNaming.suggested(app("a", null), app("b", "Social"), fallback = "Mappe"))
    }

    @Test
    fun aCategoryNamesAFolderOfSeveralOnlyWhenEveryAppSharesIt() {
        val social = listOf(app("a", "Social"), app("b", "Social"))
        assertEquals("Social", FolderNaming.suggestedForAll(social + app("c", "Social")))
        assertEquals("Folder", FolderNaming.suggestedForAll(social + app("c", "Audio")))
        assertEquals("Folder", FolderNaming.suggestedForAll(listOf(app("a", "Social"), null, app("c", "Social"))))
        assertEquals("Folder", FolderNaming.suggestedForAll(listOf(app("a", "Social"))))
        assertEquals("Mappe", FolderNaming.suggestedForAll(listOf(app("a", null), app("b", null)), fallback = "Mappe"))
    }
}
