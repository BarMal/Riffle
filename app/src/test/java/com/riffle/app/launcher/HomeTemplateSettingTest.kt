package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTemplateSettingTest {
    @Test
    fun exposesCompatibleDefaultTemplatesAsViewModeOptions() {
        assertEquals(
            listOf(
                HomeTemplateOption(
                    id = LauncherTemplateCatalogDefaults.cardInterfaceId,
                    displayName = "Card interface",
                    description = "A card-first launcher layout with notification cards and a dock.",
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                ),
                HomeTemplateOption(
                    id = LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId,
                    displayName = "Generated pages",
                    description = "A conservative generated-page layout backed only by installed apps.",
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                ),
                HomeTemplateOption(
                    id = LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId,
                    displayName = "Standard phone app drawer",
                    description = "A classic phone launcher layout with a home page and app drawer.",
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                ),
            ),
            homeTemplateOptions(
                availableViewModes = LauncherViewMode.entries,
                deviceClass = HomeLayoutDeviceClass.PHONE,
            ),
        )
    }

    @Test
    fun omitsTemplatesForUnavailableModes() {
        assertEquals(
            listOf(
                HomeTemplateOption(
                    id = LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId,
                    displayName = "Generated pages",
                    description = "A conservative generated-page layout backed only by installed apps.",
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                ),
            ),
            homeTemplateOptions(
                availableViewModes = listOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                deviceClass = HomeLayoutDeviceClass.TABLET,
            ),
        )
    }
}
