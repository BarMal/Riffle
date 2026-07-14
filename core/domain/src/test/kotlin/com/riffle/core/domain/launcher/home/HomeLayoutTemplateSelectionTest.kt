package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HomeLayoutTemplateSelectionTest {
    @Test
    fun defaultTemplatesSeedTheirCompatibleLayoutsAndRecordTheTemplateId() {
        val selections =
            listOf(
                LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId to
                    HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE),
                LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId to
                    HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.TABLET),
                LauncherTemplateCatalogDefaults.cardInterfaceId to
                    HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.FOLDABLE),
            )

        selections.forEach { (templateId, targetKey) ->
            val template = LauncherTemplateCatalogDefaults.templates.first { it.id == templateId }
            val layout = assertNotNull(template.seedHomeLayout(targetKey))

            assertEquals(templateId, layout.templateId)
            assertEquals(targetKey.viewMode, layout.viewMode)
            assertEquals(template.seedPageTypes, layout.pages.map { page -> page.type })
        }
    }
}
