package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherTemplateSchemaTest {
    private val validator = LauncherTemplateSchemaValidator()

    @Test
    fun schemaDocumentRoundTripsSupportedAndUnsupportedElementTypes() {
        val schema =
            LauncherTemplateSchema(
                slots =
                    listOf(
                        slot(
                            id = "hero",
                            supportedElementTypes =
                                setOf(
                                    LauncherTemplateSupportedElementType.APP_ICON,
                                    LauncherTemplateSupportedElementType.TEXT,
                                ),
                        ),
                        slot(
                            id = "cards",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.CARD),
                            maxElementCount = 2,
                        ).copy(unsupportedElementTypes = setOf("video_embed")),
                    ),
                elements =
                    listOf(
                        element(
                            id = "clock",
                            slotId = "hero",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.TEXT),
                        ),
                        element(
                            id = "legacy-video",
                            slotId = "cards",
                            type = UnsupportedLauncherTemplateElementType("video_embed"),
                        ),
                    ),
            )

        assertEquals(schema, schema.toDocument().toSchema())
    }

    @Test
    fun validatorAcceptsVersionedSchemaWithCompatibleSlotUsage() {
        val schema =
            LauncherTemplateSchema(
                slots =
                    listOf(
                        slot(
                            id = "header",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.TEXT),
                            minElementCount = 1,
                            maxElementCount = 1,
                        ),
                        slot(
                            id = "body",
                            supportedElementTypes =
                                setOf(
                                    LauncherTemplateSupportedElementType.CARD,
                                    LauncherTemplateSupportedElementType.DYNAMIC_CONTENT,
                                ),
                            minElementCount = 1,
                        ),
                    ),
                elements =
                    listOf(
                        element(
                            id = "title",
                            slotId = "header",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.TEXT),
                        ),
                        element(
                            id = "today-feed",
                            slotId = "body",
                            type =
                                SupportedLauncherTemplateElementType(
                                    LauncherTemplateSupportedElementType.DYNAMIC_CONTENT,
                                ),
                        ),
                    ),
            )

        val result = validator.validate(schema)

        assertTrue(result.isValid)
        assertEquals(emptyList(), result.issues)
    }

    @Test
    fun validatorRejectsUnsupportedVersionDuplicateIdsAndInvalidCapacity() {
        val schema =
            LauncherTemplateSchema(
                version = CURRENT_LAUNCHER_TEMPLATE_SCHEMA_VERSION + 1,
                slots =
                    listOf(
                        slot(
                            id = "hero",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.TEXT),
                            minElementCount = 2,
                            maxElementCount = 1,
                        ),
                        slot(
                            id = "hero",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.TEXT),
                        ),
                    ),
                elements =
                    listOf(
                        element(
                            id = "title",
                            slotId = "hero",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.TEXT),
                        ),
                        element(
                            id = "title",
                            slotId = "hero",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.TEXT),
                        ),
                    ),
            )

        val result = validator.validate(schema)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                LauncherTemplateSchemaValidationIssue.UnsupportedSchemaVersion(
                    actualVersion = 2,
                    supportedVersion = 1,
                ),
                LauncherTemplateSchemaValidationIssue.DuplicateSlotId(LauncherTemplateSlotId("hero")),
                LauncherTemplateSchemaValidationIssue.DuplicateElementId(LauncherTemplateElementId("title")),
                LauncherTemplateSchemaValidationIssue.InvalidSlotCapacity(
                    slotId = LauncherTemplateSlotId("hero"),
                    minElementCount = 2,
                    maxElementCount = 1,
                ),
            ),
            result.issues.take(4),
        )
    }

    @Test
    fun validatorRejectsMissingSlotsUnsupportedElementsAndIncompatibleSlotUsage() {
        val schema =
            LauncherTemplateSchema(
                slots =
                    listOf(
                        slot(
                            id = "header",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.TEXT),
                            minElementCount = 1,
                            maxElementCount = 1,
                        ),
                    ),
                elements =
                    listOf(
                        element(
                            id = "bad-type",
                            slotId = "header",
                            type = UnsupportedLauncherTemplateElementType("video_embed"),
                        ),
                        element(
                            id = "wrong-slot",
                            slotId = "header",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.APP_ICON),
                        ),
                        element(
                            id = "missing-slot",
                            slotId = "footer",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.TEXT),
                        ),
                    ),
            )

        val result = validator.validate(schema)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                LauncherTemplateSchemaValidationIssue.UnsupportedElementType(
                    elementId = LauncherTemplateElementId("bad-type"),
                    rawType = "video_embed",
                ),
                LauncherTemplateSchemaValidationIssue.IncompatibleSlotElementType(
                    elementId = LauncherTemplateElementId("wrong-slot"),
                    slotId = LauncherTemplateSlotId("header"),
                    elementType = LauncherTemplateSupportedElementType.APP_ICON,
                ),
                LauncherTemplateSchemaValidationIssue.MissingSlot(
                    elementId = LauncherTemplateElementId("missing-slot"),
                    slotId = LauncherTemplateSlotId("footer"),
                ),
                LauncherTemplateSchemaValidationIssue.MissingRequiredSlotElements(
                    slotId = LauncherTemplateSlotId("header"),
                    minimumCount = 1,
                    actualCount = 0,
                ),
            ),
            result.issues,
        )
    }

    @Test
    fun validatorRejectsMissingAndOverflowingSlotContentCounts() {
        val schema =
            LauncherTemplateSchema(
                slots =
                    listOf(
                        slot(
                            id = "header",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.TEXT),
                            minElementCount = 1,
                            maxElementCount = 1,
                        ),
                        slot(
                            id = "cards",
                            supportedElementTypes = setOf(LauncherTemplateSupportedElementType.CARD),
                            maxElementCount = 1,
                        ),
                    ),
                elements =
                    listOf(
                        element(
                            id = "card-1",
                            slotId = "cards",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.CARD),
                        ),
                        element(
                            id = "card-2",
                            slotId = "cards",
                            type = SupportedLauncherTemplateElementType(LauncherTemplateSupportedElementType.CARD),
                        ),
                    ),
            )

        val result = validator.validate(schema)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                LauncherTemplateSchemaValidationIssue.MissingRequiredSlotElements(
                    slotId = LauncherTemplateSlotId("header"),
                    minimumCount = 1,
                    actualCount = 0,
                ),
                LauncherTemplateSchemaValidationIssue.TooManySlotElements(
                    slotId = LauncherTemplateSlotId("cards"),
                    maximumCount = 1,
                    actualCount = 2,
                ),
            ),
            result.issues,
        )
    }

    private fun slot(
        id: String,
        supportedElementTypes: Set<LauncherTemplateSupportedElementType>,
        minElementCount: Int = 0,
        maxElementCount: Int? = null,
    ): LauncherTemplateSlot =
        LauncherTemplateSlot(
            id = LauncherTemplateSlotId(id),
            supportedElementTypes = supportedElementTypes,
            minElementCount = minElementCount,
            maxElementCount = maxElementCount,
        )

    private fun element(
        id: String,
        slotId: String,
        type: LauncherTemplateElementType,
    ): LauncherTemplateElement =
        LauncherTemplateElement(
            id = LauncherTemplateElementId(id),
            slotId = LauncherTemplateSlotId(slotId),
            type = type,
        )
}
