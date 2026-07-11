package com.riffle.core.domain.launcher.home

class LauncherTemplateEvaluator(
    private val validator: LauncherTemplateSchemaValidator = LauncherTemplateSchemaValidator(),
) {
    fun evaluate(schema: LauncherTemplateSchema): LauncherTemplateEvaluationResult {
        val validation = validator.validate(schema)
        if (!validation.isValid) {
            return LauncherTemplateEvaluationResult.Rejected(validation.issues)
        }

        return LauncherTemplateEvaluationResult.Evaluated(
            slots =
                schema.slots.map { slot ->
                    LauncherTemplateSlotEvaluation(
                        slot = slot,
                        elements = schema.elements.filter { element -> element.slotId == slot.id },
                    )
                },
        )
    }
}

sealed interface LauncherTemplateEvaluationResult {
    data class Evaluated(
        val slots: List<LauncherTemplateSlotEvaluation>,
    ) : LauncherTemplateEvaluationResult

    data class Rejected(
        val issues: List<LauncherTemplateSchemaValidationIssue>,
    ) : LauncherTemplateEvaluationResult
}

data class LauncherTemplateSlotEvaluation(
    val slot: LauncherTemplateSlot,
    val elements: List<LauncherTemplateElement>,
)
