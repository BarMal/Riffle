package com.riffle.core.domain.launcher.home

class LauncherTemplateSchemaValidator(
    private val supportedVersion: Int = CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION,
) {
    fun validate(schema: LauncherTemplateSchema): LauncherTemplateSchemaValidationResult {
        val elementValidation = validateElements(schema)
        return LauncherTemplateSchemaValidationResult(
            validateVersion(schema = schema, supportedVersion = supportedVersion) +
                validateDuplicateIds(schema) +
                validateSlotCapacities(schema.slots) +
                elementValidation.issues +
                validateSlotElementCounts(
                    slots = schema.slots,
                    validElementCountsBySlotId = elementValidation.validElementCountsBySlotId,
                ),
        )
    }
}

data class LauncherTemplateSchemaValidationResult(
    val issues: List<LauncherTemplateSchemaValidationIssue>,
) {
    val isValid: Boolean = issues.isEmpty()
}

sealed interface LauncherTemplateSchemaValidationIssue {
    data class UnsupportedSchemaVersion(
        val actualVersion: Int,
        val supportedVersion: Int,
    ) : LauncherTemplateSchemaValidationIssue

    data class DuplicateSlotId(
        val slotId: LauncherTemplateSlotId,
    ) : LauncherTemplateSchemaValidationIssue

    data class DuplicateElementId(
        val elementId: LauncherTemplateElementId,
    ) : LauncherTemplateSchemaValidationIssue

    data class InvalidSlotCapacity(
        val slotId: LauncherTemplateSlotId,
        val minElementCount: Int,
        val maxElementCount: Int?,
    ) : LauncherTemplateSchemaValidationIssue

    data class MissingSlot(
        val elementId: LauncherTemplateElementId,
        val slotId: LauncherTemplateSlotId,
    ) : LauncherTemplateSchemaValidationIssue

    data class UnsupportedElementType(
        val elementId: LauncherTemplateElementId,
        val rawType: String,
    ) : LauncherTemplateSchemaValidationIssue

    data class IncompatibleSlotElementType(
        val elementId: LauncherTemplateElementId,
        val slotId: LauncherTemplateSlotId,
        val elementType: LauncherTemplateSupportedElementType,
    ) : LauncherTemplateSchemaValidationIssue

    data class MissingRequiredSlotElements(
        val slotId: LauncherTemplateSlotId,
        val minimumCount: Int,
        val actualCount: Int,
    ) : LauncherTemplateSchemaValidationIssue

    data class TooManySlotElements(
        val slotId: LauncherTemplateSlotId,
        val maximumCount: Int,
        val actualCount: Int,
    ) : LauncherTemplateSchemaValidationIssue
}

private fun <T, K> List<T>.duplicatesBy(keySelector: (T) -> K): List<K> =
    groupingBy(keySelector)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .toList()

private fun validateVersion(
    schema: LauncherTemplateSchema,
    supportedVersion: Int,
): List<LauncherTemplateSchemaValidationIssue> =
    when (schema.version) {
        supportedVersion -> emptyList()
        else ->
            listOf(
                LauncherTemplateSchemaValidationIssue.UnsupportedSchemaVersion(
                    actualVersion = schema.version,
                    supportedVersion = supportedVersion,
                ),
            )
    }

private fun validateDuplicateIds(schema: LauncherTemplateSchema): List<LauncherTemplateSchemaValidationIssue> =
    schema.slots
        .duplicatesBy { slot -> slot.id }
        .map<LauncherTemplateSlotId, LauncherTemplateSchemaValidationIssue>(
            LauncherTemplateSchemaValidationIssue::DuplicateSlotId,
        ) +
        schema.elements
            .duplicatesBy { element -> element.id }
            .map<LauncherTemplateElementId, LauncherTemplateSchemaValidationIssue>(
                LauncherTemplateSchemaValidationIssue::DuplicateElementId,
            )

private fun validateSlotCapacities(slots: List<LauncherTemplateSlot>): List<LauncherTemplateSchemaValidationIssue> =
    slots.mapNotNull { slot ->
        val maxElementCount = slot.maxElementCount
        when {
            slot.minElementCount < 0 || (maxElementCount != null && maxElementCount < slot.minElementCount) ->
                LauncherTemplateSchemaValidationIssue.InvalidSlotCapacity(
                    slotId = slot.id,
                    minElementCount = slot.minElementCount,
                    maxElementCount = maxElementCount,
                )

            else -> null
        }
    }

private fun validateElements(schema: LauncherTemplateSchema): TemplateElementValidation {
    val slotsById = schema.slots.associateBy { slot -> slot.id }
    val issues = mutableListOf<LauncherTemplateSchemaValidationIssue>()
    val validElementCountsBySlotId = mutableMapOf<LauncherTemplateSlotId, Int>()

    schema.elements.forEach { element ->
        validateElement(
            element = element,
            slotsById = slotsById,
            issues = issues,
            validElementCountsBySlotId = validElementCountsBySlotId,
        )
    }

    return TemplateElementValidation(
        issues = issues,
        validElementCountsBySlotId = validElementCountsBySlotId,
    )
}

private fun validateElement(
    element: LauncherTemplateElement,
    slotsById: Map<LauncherTemplateSlotId, LauncherTemplateSlot>,
    issues: MutableList<LauncherTemplateSchemaValidationIssue>,
    validElementCountsBySlotId: MutableMap<LauncherTemplateSlotId, Int>,
) {
    val slot = slotsById[element.slotId]
    if (slot == null) {
        issues +=
            LauncherTemplateSchemaValidationIssue.MissingSlot(
                elementId = element.id,
                slotId = element.slotId,
            )
        return
    }

    when (val type = element.type) {
        is UnsupportedLauncherTemplateElementType ->
            issues +=
                LauncherTemplateSchemaValidationIssue.UnsupportedElementType(
                    elementId = element.id,
                    rawType = type.rawValue,
                )

        is SupportedLauncherTemplateElementType ->
            if (type.value !in slot.supportedElementTypes) {
                issues +=
                    LauncherTemplateSchemaValidationIssue.IncompatibleSlotElementType(
                        elementId = element.id,
                        slotId = slot.id,
                        elementType = type.value,
                    )
            } else {
                validElementCountsBySlotId[slot.id] = (validElementCountsBySlotId[slot.id] ?: 0) + 1
            }
    }
}

private fun validateSlotElementCounts(
    slots: List<LauncherTemplateSlot>,
    validElementCountsBySlotId: Map<LauncherTemplateSlotId, Int>,
): List<LauncherTemplateSchemaValidationIssue> {
    val issues = mutableListOf<LauncherTemplateSchemaValidationIssue>()

    slots.forEach { slot ->
        val elementCount = validElementCountsBySlotId[slot.id] ?: 0
        if (elementCount < slot.minElementCount) {
            issues +=
                LauncherTemplateSchemaValidationIssue.MissingRequiredSlotElements(
                    slotId = slot.id,
                    minimumCount = slot.minElementCount,
                    actualCount = elementCount,
                )
        }
        slot.maxElementCount
            ?.takeIf { maxElementCount -> elementCount > maxElementCount }
            ?.let { maxElementCount ->
                issues +=
                    LauncherTemplateSchemaValidationIssue.TooManySlotElements(
                        slotId = slot.id,
                        maximumCount = maxElementCount,
                        actualCount = elementCount,
                    )
            }
    }

    return issues
}

private data class TemplateElementValidation(
    val issues: List<LauncherTemplateSchemaValidationIssue>,
    val validElementCountsBySlotId: Map<LauncherTemplateSlotId, Int>,
)
