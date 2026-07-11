package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LauncherTemplateEvaluatorTest {
    private val evaluator = LauncherTemplateEvaluator()

    @Test
    fun evaluatesElementsInDeclaredSlotOrder() {
        val header = slot("header", LauncherTemplateSupportedElementType.TEXT)
        val content = slot("content", LauncherTemplateSupportedElementType.CARD)
        val title = element("title", "header", LauncherTemplateSupportedElementType.TEXT)
        val card = element("card", "content", LauncherTemplateSupportedElementType.CARD)

        val result =
            assertIs<LauncherTemplateEvaluationResult.Evaluated>(
                evaluator.evaluate(
                    LauncherTemplateSchema(
                        slots = listOf(header, content),
                        elements = listOf(card, title),
                    ),
                ),
            )

        assertEquals(listOf(header, content), result.slots.map { evaluation -> evaluation.slot })
        assertEquals(listOf(title), result.slots[0].elements)
        assertEquals(listOf(card), result.slots[1].elements)
    }

    @Test
    fun rejectsInvalidSchemasInsteadOfProducingPartialSlots() {
        val result =
            assertIs<LauncherTemplateEvaluationResult.Rejected>(
                evaluator.evaluate(
                    LauncherTemplateSchema(
                        slots = listOf(slot("header", LauncherTemplateSupportedElementType.TEXT)),
                        elements = listOf(element("card", "header", LauncherTemplateSupportedElementType.CARD)),
                    ),
                ),
            )

        assertEquals(
            listOf(
                LauncherTemplateSchemaValidationIssue.IncompatibleSlotElementType(
                    elementId = LauncherTemplateElementId("card"),
                    slotId = LauncherTemplateSlotId("header"),
                    elementType = LauncherTemplateSupportedElementType.CARD,
                ),
            ),
            result.issues,
        )
    }

    private fun slot(
        id: String,
        vararg supportedElementTypes: LauncherTemplateSupportedElementType,
    ): LauncherTemplateSlot =
        LauncherTemplateSlot(
            id = LauncherTemplateSlotId(id),
            supportedElementTypes = supportedElementTypes.toSet(),
        )

    private fun element(
        id: String,
        slotId: String,
        type: LauncherTemplateSupportedElementType,
    ): LauncherTemplateElement =
        LauncherTemplateElement(
            id = LauncherTemplateElementId(id),
            slotId = LauncherTemplateSlotId(slotId),
            type = SupportedLauncherTemplateElementType(type),
        )
}
