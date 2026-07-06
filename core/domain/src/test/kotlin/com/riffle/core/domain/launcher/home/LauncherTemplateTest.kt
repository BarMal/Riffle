package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherTemplateTest {
    @Test
    fun templateDescriptorStoresMetadataCompatibilityAndSeedPages() {
        val template =
            template(
                id = "standard-library",
                displayName = "Standard library",
                description = "A home screen with an app library page.",
                viewModes = setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                deviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
                seedPageTypes =
                    listOf(
                        LauncherPageType.Home,
                        LauncherPageType.AllApps,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES),
                    ),
            )

        assertEquals(LauncherTemplateId("standard-library"), template.id)
        assertEquals("Standard library", template.metadata.displayName)
        assertEquals("A home screen with an app library page.", template.metadata.description)
        assertEquals(setOf(LauncherViewMode.HOME_SCREEN_LIBRARY), template.supportedViewModes)
        assertEquals(
            setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            template.supportedDeviceClasses,
        )
        assertEquals(
            listOf(
                LauncherPageType.Home,
                LauncherPageType.AllApps,
                LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES),
            ),
            template.seedPageTypes,
        )
        assertTrue(template.supports(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE))
        assertFalse(template.supports(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.PHONE))
        assertFalse(template.supports(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.TABLET))
    }

    @Test
    fun emptyCatalogIsSafeToFilter() {
        val catalog = LauncherTemplateCatalog(emptyList())

        assertEquals(
            emptyList(),
            catalog.compatibleWith(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.PHONE,
            ),
        )
    }

    @Test
    fun filtersByPhoneFoldableAndTabletDeviceClasses() {
        val phone = template(id = "phone", deviceClasses = setOf(HomeLayoutDeviceClass.PHONE))
        val foldable = template(id = "foldable", deviceClasses = setOf(HomeLayoutDeviceClass.FOLDABLE))
        val tablet = template(id = "tablet", deviceClasses = setOf(HomeLayoutDeviceClass.TABLET))
        val shared =
            template(
                id = "shared",
                deviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.TABLET),
            )
        val catalog = LauncherTemplateCatalog(listOf(phone, foldable, tablet, shared))

        assertEquals(
            listOf(phone.id, shared.id),
            catalog
                .compatibleWith(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
                .map { template -> template.id },
        )
        assertEquals(
            listOf(foldable.id),
            catalog
                .compatibleWith(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
                .map { template -> template.id },
        )
        assertEquals(
            listOf(tablet.id, shared.id),
            catalog
                .compatibleWith(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.TABLET)
                .map { template -> template.id },
        )
    }

    @Test
    fun filtersByStandardLibraryAndCardViewModes() {
        val standard =
            template(
                id = "standard",
                viewModes = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
            )
        val library =
            template(
                id = "library",
                viewModes = setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        val card =
            template(
                id = "card",
                viewModes = setOf(LauncherViewMode.CARD_INTERFACE),
            )
        val hybrid =
            template(
                id = "hybrid",
                viewModes =
                    setOf(
                        LauncherViewMode.STANDARD_APP_DRAWER,
                        LauncherViewMode.CARD_INTERFACE,
                    ),
            )
        val catalog = LauncherTemplateCatalog(listOf(standard, library, card, hybrid))

        assertEquals(
            listOf(standard.id, hybrid.id),
            catalog
                .compatibleWith(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
                .map { template -> template.id },
        )
        assertEquals(
            listOf(library.id),
            catalog
                .compatibleWith(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
                .map { template -> template.id },
        )
        assertEquals(
            listOf(card.id, hybrid.id),
            catalog
                .compatibleWith(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.PHONE)
                .map { template -> template.id },
        )
    }

    @Test
    fun compatibleFilterPreservesCatalogOrdering() {
        val first = template(id = "first")
        val second = template(id = "second")
        val third = template(id = "third")
        val catalog = LauncherTemplateCatalog(listOf(third, first, second))

        assertEquals(
            listOf(third.id, first.id, second.id),
            catalog
                .compatibleWith(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
                .map { template -> template.id },
        )
    }

    private fun template(
        id: String,
        displayName: String = id,
        description: String = "$id template",
        viewModes: Set<LauncherViewMode> = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
        deviceClasses: Set<HomeLayoutDeviceClass> = setOf(HomeLayoutDeviceClass.PHONE),
        seedPageTypes: List<LauncherPageType> = listOf(LauncherPageType.Home),
    ): LauncherTemplate =
        LauncherTemplate(
            id = LauncherTemplateId(id),
            metadata =
                LauncherTemplateMetadata(
                    displayName = displayName,
                    description = description,
                ),
            supportedViewModes = viewModes,
            supportedDeviceClasses = deviceClasses,
            seedPageTypes = seedPageTypes,
        )
}
