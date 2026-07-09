package com.riffle.core.domain.launcher.home

const val CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION = 1

data class LauncherTemplateSchema(
    val version: Int = CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION,
    val slots: List<LauncherTemplateSlot>,
    val elements: List<LauncherTemplateElement>,
)

@JvmInline
value class LauncherTemplateSlotId(val value: String)

data class LauncherTemplateSlot(
    val id: LauncherTemplateSlotId,
    val supportedElementTypes: Set<LauncherTemplateSupportedElementType>,
    val minElementCount: Int = 0,
    val maxElementCount: Int? = null,
)

@JvmInline
value class LauncherTemplateElementId(val value: String)

data class LauncherTemplateElement(
    val id: LauncherTemplateElementId,
    val slotId: LauncherTemplateSlotId,
    val type: LauncherTemplateElementType,
)

sealed interface LauncherTemplateElementType {
    val rawValue: String
}

data class SupportedLauncherTemplateElementType(
    val value: LauncherTemplateSupportedElementType,
) : LauncherTemplateElementType {
    override val rawValue: String = value.rawValue
}

data class UnsupportedLauncherTemplateElementType(
    override val rawValue: String,
) : LauncherTemplateElementType

enum class LauncherTemplateSupportedElementType(
    val rawValue: String,
) {
    APP_ICON("app_icon"),
    WIDGET("widget"),
    CARD("card"),
    TEXT("text"),
    IMAGE("image"),
    DYNAMIC_CONTENT("dynamic_content"),
    ;

    companion object {
        fun fromRawValue(rawValue: String): LauncherTemplateSupportedElementType? =
            entries.firstOrNull { elementType -> elementType.rawValue == rawValue }
    }
}

data class LauncherTemplateSchemaDocument(
    val version: Int = CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION,
    val slots: List<LauncherTemplateSlotDocument>,
    val elements: List<LauncherTemplateElementDocument>,
)

data class LauncherTemplateSlotDocument(
    val id: String,
    val supportedElementTypes: List<String>,
    val minElementCount: Int = 0,
    val maxElementCount: Int? = null,
)

data class LauncherTemplateElementDocument(
    val id: String,
    val slotId: String,
    val type: String,
)

fun LauncherTemplateSchema.toDocument(): LauncherTemplateSchemaDocument =
    LauncherTemplateSchemaDocument(
        version = version,
        slots =
            slots.map { slot ->
                LauncherTemplateSlotDocument(
                    id = slot.id.value,
                    supportedElementTypes =
                        slot.supportedElementTypes
                            .map { elementType -> elementType.rawValue }
                            .sorted(),
                    minElementCount = slot.minElementCount,
                    maxElementCount = slot.maxElementCount,
                )
            },
        elements =
            elements.map { element ->
                LauncherTemplateElementDocument(
                    id = element.id.value,
                    slotId = element.slotId.value,
                    type = element.type.rawValue,
                )
            },
    )

fun LauncherTemplateSchemaDocument.toSchema(): LauncherTemplateSchema =
    LauncherTemplateSchema(
        version = version,
        slots =
            slots.map { slot ->
                LauncherTemplateSlot(
                    id = LauncherTemplateSlotId(slot.id),
                    supportedElementTypes =
                        slot.supportedElementTypes
                            .mapNotNull(LauncherTemplateSupportedElementType::fromRawValue)
                            .toSet(),
                    minElementCount = slot.minElementCount,
                    maxElementCount = slot.maxElementCount,
                )
            },
        elements =
            elements.map { element ->
                LauncherTemplateElement(
                    id = LauncherTemplateElementId(element.id),
                    slotId = LauncherTemplateSlotId(element.slotId),
                    type = launcherTemplateElementType(element.type),
                )
            },
    )

fun launcherTemplateElementType(rawValue: String): LauncherTemplateElementType =
    LauncherTemplateSupportedElementType
        .fromRawValue(rawValue)
        ?.let(::SupportedLauncherTemplateElementType)
        ?: UnsupportedLauncherTemplateElementType(rawValue)
