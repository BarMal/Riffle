package com.riffle.core.domain.launcher.home

@JvmInline
value class LauncherTemplateId(val value: String)

data class LauncherTemplateMetadata(
    val displayName: String,
    val description: String,
)

data class LauncherTemplate(
    val id: LauncherTemplateId,
    val metadata: LauncherTemplateMetadata,
    val supportedViewModes: Set<LauncherViewMode>,
    val supportedDeviceClasses: Set<HomeLayoutDeviceClass>,
    val seedPageTypes: List<LauncherPageType>,
) {
    fun supports(
        viewMode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass,
    ): Boolean = viewMode in supportedViewModes && deviceClass in supportedDeviceClasses
}

data class LauncherTemplateCatalog(
    val templates: List<LauncherTemplate>,
) {
    fun compatibleWith(
        viewMode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass,
    ): List<LauncherTemplate> {
        return templates.filter { template ->
            template.supports(viewMode = viewMode, deviceClass = deviceClass)
        }
    }
}
