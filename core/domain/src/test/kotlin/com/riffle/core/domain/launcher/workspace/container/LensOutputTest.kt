package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LensOutputTest {
    private val ready = SourceState.Ready(emptyList())

    @Test
    fun availabilityPrefersReadyThenLoadingThenPermissionThenUnavailable() {
        val expected =
            listOf(
                listOf(ready, SourceState.PermissionRequired) to LensAvailability.READY,
                listOf(SourceState.Loading, SourceState.PermissionRequired) to LensAvailability.LOADING,
                listOf(SourceState.Unavailable, SourceState.PermissionRequired) to LensAvailability.PERMISSION_REQUIRED,
                listOf(SourceState.Unavailable) to LensAvailability.UNAVAILABLE,
                emptyList<SourceState>() to LensAvailability.READY,
            )
        expected.forEach { (states, availability) ->
            assertEquals(availability, LensOutput.availabilityOf(states), states.toString())
        }
    }

    @Test
    fun aResultIsPresentExactlyWhenReady() {
        assertFailsWith<IllegalArgumentException> { LensOutput(LensAvailability.READY) }
        assertFailsWith<IllegalArgumentException> {
            LensOutput(LensAvailability.LOADING, LensResult.Flat(emptyList()))
        }
        assertEquals(LensAvailability.READY, LensOutput.ready(LensResult.Flat(emptyList())).availability)
    }

    @Test
    fun aPageSetWithOnePageDoesNotClaimThePagerAxis() {
        val declaration = AxisDeclaration(owned = setOf(GestureAxis.HORIZONTAL_PAGER, GestureAxis.VERTICAL_SCROLL))

        assertEquals(setOf(GestureAxis.VERTICAL_SCROLL), declaration.forPageCount(1).owned)
        assertEquals(setOf(GestureAxis.VERTICAL_SCROLL), declaration.forPageCount(0).owned)
        assertEquals(declaration, declaration.forPageCount(2))
    }
}
