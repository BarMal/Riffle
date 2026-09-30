package com.riffle.core.domain.launcher.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LensExpressionValidityTest {
    private val apps = SourceId("apps")
    private val flat = Lens(sources = listOf(apps))
    private val grouped = flat.copy(group = LensGroup.ByGroupKey)
    private val groupable = SourceDescriptor(apps, setOf(SourceCapability.GROUPABLE))

    @Test
    fun flatLensIsValidForListButNotCategories() {
        assertTrue(LensExpressionValidity.check(flat, ExpressionKind.LIST).isValid)
        val invalid = LensExpressionValidity.check(flat, ExpressionKind.CATEGORIES) as LensValidity.Invalid
        assertTrue(invalid.issues.any { it is LensIssue.ShapeNotAccepted })
    }

    @Test
    fun groupedLensIsValidForCategoriesButNotList() {
        assertTrue(LensExpressionValidity.check(grouped, ExpressionKind.CATEGORIES).isValid)
        assertTrue(!LensExpressionValidity.check(grouped, ExpressionKind.LIST).isValid)
    }

    @Test
    fun limitOneProducesSingleShapeForCard() {
        assertTrue(!LensExpressionValidity.check(flat, ExpressionKind.CARD).isValid)
        assertTrue(LensExpressionValidity.check(flat.copy(limit = 1), ExpressionKind.CARD).isValid)
        // A single-item lens is still a valid flat lens.
        assertTrue(LensExpressionValidity.check(flat.copy(limit = 1), ExpressionKind.LIST).isValid)
    }

    @Test
    fun projectingAwayARequiredFieldIsInvalid() {
        val noIcon = flat.copy(project = ItemField.ALL - ItemField.ICON)
        val result = LensExpressionValidity.check(noIcon, ExpressionKind.ICON_GRID) as LensValidity.Invalid
        assertEquals(listOf(LensIssue.MissingRequiredField(ItemField.ICON)), result.issues)
    }

    @Test
    fun groupingByGroupKeyNeedsGroupableSources() {
        val plain = SourceDescriptor(apps)
        val result =
            LensExpressionValidity.check(
                grouped,
                ExpressionKind.CATEGORIES,
                listOf(plain),
            ) as LensValidity.Invalid
        assertEquals(listOf(LensIssue.SourceNotGroupable(apps)), result.issues)
        assertTrue(LensExpressionValidity.check(grouped, ExpressionKind.CATEGORIES, listOf(groupable)).isValid)
    }

    @Test
    fun unknownSourceIsReportedOnlyWhenSourcesAreProvided() {
        assertTrue(LensExpressionValidity.check(flat, ExpressionKind.LIST, null).isValid)
        val result = LensExpressionValidity.check(flat, ExpressionKind.LIST, emptyList()) as LensValidity.Invalid
        assertEquals(listOf(LensIssue.UnknownSource(apps)), result.issues)
    }

    @Test
    fun compatibleExpressionsFiltersEditorChoices() {
        assertEquals(
            setOf(ExpressionKind.CATEGORIES, ExpressionKind.INDEX),
            LensExpressionValidity.compatibleExpressions(grouped).toSet(),
        )
        assertTrue(ExpressionKind.CARD !in LensExpressionValidity.compatibleExpressions(flat))
    }

    @Test
    fun everyExpressionHasADescriptorAndAcceptsSomeShape() {
        ExpressionKind.entries.forEach { kind ->
            val descriptor = ExpressionCatalog.descriptorFor(kind)
            assertEquals(kind, descriptor.kind)
            assertTrue(descriptor.accepts.isNotEmpty())
        }
    }
}
