package com.riffle.core.domain.launcher.home

object LauncherTemplateCatalogDefaults {
    val standardPhoneAppDrawerId: LauncherTemplateId =
        LauncherTemplateId("standard-phone-app-drawer")

    val conservativeGeneratedPagesId: LauncherTemplateId =
        LauncherTemplateId("conservative-generated-pages")

    val cardInterfaceId: LauncherTemplateId =
        LauncherTemplateId("card-interface")

    val templates: List<LauncherTemplate> =
        listOf(
            standardPhoneAppDrawer(),
            conservativeGeneratedPages(),
            cardInterface(),
        ).sortedBy { template -> template.id.value }
            .also(::requireUniqueIds)

    val catalog: LauncherTemplateCatalog = LauncherTemplateCatalog(templates)

    private fun standardPhoneAppDrawer(): LauncherTemplate =
        LauncherTemplate(
            id = standardPhoneAppDrawerId,
            metadata =
                LauncherTemplateMetadata(
                    displayName = "Standard phone app drawer",
                    description = "A classic phone launcher layout with a home page and app drawer.",
                ),
            supportedViewModes = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
            supportedDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE),
            seedPageTypes =
                listOf(
                    LauncherPageType.Home,
                ),
            schema =
                LauncherTemplateSchema(
                    slots =
                        listOf(
                            slot(
                                "home",
                                LauncherTemplateSupportedElementType.APP_ICON,
                                LauncherTemplateSupportedElementType.WIDGET,
                            ),
                        ),
                    elements = emptyList(),
                ),
        )

    private fun conservativeGeneratedPages(): LauncherTemplate =
        LauncherTemplate(
            id = conservativeGeneratedPagesId,
            metadata =
                LauncherTemplateMetadata(
                    displayName = "Generated pages",
                    description = "A conservative generated-page layout backed only by installed apps.",
                ),
            supportedViewModes = setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
            supportedDeviceClasses = HomeLayoutDeviceClass.entries.toSet(),
            seedPageTypes =
                listOf(
                    LauncherPageType.Home,
                    LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
                    LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                ),
            schema =
                LauncherTemplateSchema(
                    slots =
                        listOf(
                            slot(
                                "home",
                                LauncherTemplateSupportedElementType.APP_ICON,
                                LauncherTemplateSupportedElementType.WIDGET,
                            ),
                            slot(
                                "generated-content",
                                LauncherTemplateSupportedElementType.DYNAMIC_CONTENT,
                                LauncherTemplateSupportedElementType.CARD,
                                LauncherTemplateSupportedElementType.TEXT,
                            ),
                            slot(
                                "dock",
                                LauncherTemplateSupportedElementType.APP_ICON,
                                LauncherTemplateSupportedElementType.CARD,
                            ),
                        ),
                    elements = emptyList(),
                ),
        )

    private fun cardInterface(): LauncherTemplate =
        LauncherTemplate(
            id = cardInterfaceId,
            metadata =
                LauncherTemplateMetadata(
                    displayName = "Card interface",
                    description = "A card-first launcher layout with notification cards and a dock.",
                ),
            supportedViewModes = setOf(LauncherViewMode.CARD_INTERFACE),
            supportedDeviceClasses = HomeLayoutDeviceClass.entries.toSet(),
            seedPageTypes =
                listOf(
                    LauncherPageType.Home,
                ),
            schema =
                LauncherTemplateSchema(
                    slots =
                        listOf(
                            slot(
                                "cards",
                                LauncherTemplateSupportedElementType.CARD,
                                LauncherTemplateSupportedElementType.DYNAMIC_CONTENT,
                                LauncherTemplateSupportedElementType.TEXT,
                                LauncherTemplateSupportedElementType.IMAGE,
                            ),
                            slot(
                                "dock",
                                LauncherTemplateSupportedElementType.APP_ICON,
                                LauncherTemplateSupportedElementType.CARD,
                            ),
                        ),
                    elements = emptyList(),
                ),
        )

    private fun slot(
        id: String,
        vararg supportedElementTypes: LauncherTemplateSupportedElementType,
    ): LauncherTemplateSlot =
        LauncherTemplateSlot(
            id = LauncherTemplateSlotId(id),
            supportedElementTypes = supportedElementTypes.toSet(),
        )

    private fun requireUniqueIds(templates: List<LauncherTemplate>) {
        require(templates.map { template -> template.id }.distinct().size == templates.size) {
            "Built-in launcher template ids must be unique."
        }
    }
}
