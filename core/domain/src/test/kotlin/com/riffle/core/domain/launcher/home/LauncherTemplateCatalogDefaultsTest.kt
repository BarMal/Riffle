package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LauncherTemplateCatalogDefaultsTest {
    private val templates = LauncherTemplateCatalogDefaults.templates
    private val catalog = LauncherTemplateCatalogDefaults.catalog
    private val planner = LauncherTemplateSeedPlanner()

    @Test
    fun defaultTemplateIdsAreDeterministicAndUnique() {
        val ids = templates.map { template -> template.id }

        assertEquals(ids.sortedBy { id -> id.value }, ids)
        assertEquals(ids.distinct(), ids)
        assertEquals(
            listOf(
                LauncherTemplateCatalogDefaults.cardInterfaceId,
                LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId,
                LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId,
            ),
            ids,
        )
    }

    @Test
    fun standardPhoneAppDrawerDefaultsToClassicPhoneSeeds() {
        val template = requireTemplate(LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId)

        assertEquals("Standard phone app drawer", template.metadata.displayName)
        assertEquals(setOf(LauncherViewMode.STANDARD_APP_DRAWER), template.supportedViewModes)
        assertEquals(setOf(HomeLayoutDeviceClass.PHONE), template.supportedDeviceClasses)
        assertEquals(
            listOf(
                LauncherPageType.Home,
                LauncherPageType.AllApps,
            ),
            template.seedPageTypes,
        )
    }

    @Test
    fun conservativeGeneratedPagesUseInstalledAppBackedSeedPages() {
        val template = requireTemplate(LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId)

        assertEquals("Generated pages", template.metadata.displayName)
        assertEquals(setOf(LauncherViewMode.HOME_SCREEN_LIBRARY), template.supportedViewModes)
        assertEquals(HomeLayoutDeviceClass.entries.toSet(), template.supportedDeviceClasses)
        assertEquals(
            listOf(
                LauncherPageType.Home,
                LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
                LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
            ),
            template.seedPageTypes,
        )
        assertEquals(
            setOf(GeneratedLauncherPageDataSource.INSTALLED_APPS),
            generatedDataSources(template),
        )
    }

    @Test
    fun cardInterfaceDefaultsToAStaticHomePageAndDockSlots() {
        val template = requireTemplate(LauncherTemplateCatalogDefaults.cardInterfaceId)

        assertEquals("Card interface", template.metadata.displayName)
        assertEquals(setOf(LauncherViewMode.CARD_INTERFACE), template.supportedViewModes)
        assertEquals(HomeLayoutDeviceClass.entries.toSet(), template.supportedDeviceClasses)
        assertEquals(
            listOf(LauncherPageType.Home),
            template.seedPageTypes,
        )
        assertEquals(
            emptySet(),
            generatedDataSources(template),
        )
        assertEquals(
            listOf("cards", "dock"),
            template.schema.slots.map { slot -> slot.id.value },
        )
    }

    @Test
    fun catalogCompatibilityFiltersByModeAndDeviceClass() {
        assertEquals(
            listOf(LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId),
            catalog
                .compatibleWith(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    deviceClass = HomeLayoutDeviceClass.PHONE,
                )
                .map { template -> template.id },
        )
        assertEquals(
            emptyList(),
            catalog
                .compatibleWith(
                    viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                )
                .map { template -> template.id },
        )
        assertEquals(
            listOf(LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId),
            catalog
                .compatibleWith(
                    viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                )
                .map { template -> template.id },
        )
        assertEquals(
            listOf(LauncherTemplateCatalogDefaults.cardInterfaceId),
            catalog
                .compatibleWith(
                    viewMode = LauncherViewMode.CARD_INTERFACE,
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                )
                .map { template -> template.id },
        )
    }

    @Test
    fun builtInSeedsPlanForEverySupportedModeAndDeviceClass() {
        templates.forEach { template ->
            template.supportedViewModes.forEach { viewMode ->
                template.supportedDeviceClasses.forEach { deviceClass ->
                    val key = HomeLayoutKey(viewMode = viewMode, deviceClass = deviceClass)
                    val planned =
                        assertIs<LauncherTemplateSeedPlanResult.Planned>(
                            planner.plan(template = template, targetKey = key),
                        ).plan

                    assertEquals(template.id, planned.templateId)
                    assertEquals(key, planned.targetKey)
                    assertEquals(template.seedPageTypes, planned.pageTypes)
                }
            }
        }
    }

    @Test
    fun builtInSeedsHaveStablePlannerPageIds() {
        val standard =
            assertIs<LauncherTemplateSeedPlanResult.Planned>(
                planner.plan(
                    template = requireTemplate(LauncherTemplateCatalogDefaults.standardPhoneAppDrawerId),
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            ).plan
        val generated =
            assertIs<LauncherTemplateSeedPlanResult.Planned>(
                planner.plan(
                    template = requireTemplate(LauncherTemplateCatalogDefaults.conservativeGeneratedPagesId),
                    targetKey =
                        HomeLayoutKey(
                            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                            deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                        ),
                ),
            ).plan

        assertEquals(
            listOf(
                LauncherPageId("home"),
                LauncherPageId("all-apps"),
            ),
            standard.pages.map { page -> page.id },
        )
        assertEquals(
            listOf(
                LauncherPageId("home"),
                LauncherPageId("generated:app:1"),
                LauncherPageId("generated:today:1"),
            ),
            generated.pages.map { page -> page.id },
        )
    }

    @Test
    fun builtInTemplatesHaveVersionedValidSchemas() {
        val validator = LauncherTemplateSchemaValidator()

        templates.forEach { template ->
            assertEquals(CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION, template.schema.version)
            assertEquals(template.schema, template.schema.toDocument().toSchema())
            assertTrue(validator.validate(template.schema).isValid)
        }
    }

    private fun requireTemplate(id: LauncherTemplateId): LauncherTemplate =
        assertNotNull(templates.singleOrNull { template -> template.id == id })

    private fun generatedDataSources(template: LauncherTemplate): Set<GeneratedLauncherPageDataSource> =
        template.seedPageTypes
            .filterIsInstance<LauncherPageType.Generated>()
            .flatMap { pageType -> pageType.kind.spec.requiredDataSources }
            .toSet()
}
