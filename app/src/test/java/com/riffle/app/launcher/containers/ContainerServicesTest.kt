package com.riffle.app.launcher.containers

import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.PageSetPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure mappings containers use between provider output and what expressions draw. */
class ContainerServicesTest {
    @Test
    fun outputsMapOntoExpressionStates() {
        assertEquals(ExpressionState.Loading, LensOutput.Loading.toExpressionState())
        assertEquals(ExpressionState.Ready, LensOutput.ready(LensResult.Flat(emptyList())).toExpressionState())
        assertEquals(
            ExpressionState.Unavailable(ContainerText.PERMISSION_REQUIRED),
            LensOutput.PermissionRequired.toExpressionState(),
        )
        assertEquals(
            ExpressionState.Unavailable(ContainerText.UNAVAILABLE),
            LensOutput.Unavailable.toExpressionState(),
        )
    }

    @Test
    fun notReadyOutputsDrawAnEmptyFlatResult() {
        val result = LensOutput.Loading.resultOrEmpty()

        assertTrue(result is LensResult.Flat && result.items.isEmpty())
    }

    @Test
    fun pageTitlePrefersLabelThenKeyThenANeutralName() {
        assertEquals("Mail", PageSetPage("group:mail", "mail", "Mail", emptyList()).title())
        assertEquals("mail", PageSetPage("group:mail", "mail", " ", emptyList()).title())
        assertEquals(ContainerText.OTHER_PAGE, PageSetPage("group:", "", null, emptyList()).title())
    }
}
