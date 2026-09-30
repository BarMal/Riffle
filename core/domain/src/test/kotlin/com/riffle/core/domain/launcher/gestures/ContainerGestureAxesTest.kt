package com.riffle.core.domain.launcher.gestures

import com.riffle.core.domain.launcher.workspace.GestureAxis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContainerGestureAxesTest {
    @Test
    fun verticalDeclarationEnablesOverscrollHandOff() {
        assertTrue(ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)).handsOffVerticalOverscroll)
        assertFalse(ContainerGestureAxes(setOf(GestureAxis.HORIZONTAL_PAGER)).handsOffVerticalOverscroll)
        assertFalse(ContainerGestureAxes().handsOffVerticalOverscroll)
    }

    @Test
    fun horizontalScrollAndPagerBothCountAsHorizontalOwnership() {
        assertTrue(ContainerGestureAxes(setOf(GestureAxis.HORIZONTAL_SCROLL)).ownsHorizontal)
        assertTrue(ContainerGestureAxes(setOf(GestureAxis.HORIZONTAL_PAGER)).ownsHorizontal)
        assertFalse(ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)).ownsHorizontal)
    }

    @Test
    fun dragAxisIsTheDominantComponent() {
        assertEquals(DragAxis.HORIZONTAL, ContainerGestureAxes.dragAxisOf(-9f, 3f))
        assertEquals(DragAxis.VERTICAL, ContainerGestureAxes.dragAxisOf(2f, -8f))
        assertNull(ContainerGestureAxes.dragAxisOf(4f, -4f))
        assertNull(ContainerGestureAxes.dragAxisOf(0f, 0f))
    }

    @Test
    fun declaredAxisClaimsOnlyDragsAlongIt() {
        val pager = ContainerGestureAxes(setOf(GestureAxis.HORIZONTAL_PAGER, GestureAxis.VERTICAL_SCROLL))
        val verticalOnly = ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL))

        assertTrue(pager.declaresDragAlong(30f, 2f))
        assertTrue(verticalOnly.declaresDragAlong(1f, -30f))
        assertFalse(verticalOnly.declaresDragAlong(30f, 2f))
        assertFalse(pager.declaresDragAlong(0f, 0f))
    }

    @Test
    fun arbiterWithNothingDeclaredNeverDefersToAChild() {
        val arbiter = HomeGestureArbiter()

        assertFalse(arbiter.dragBelongsToDeclaredChild(0f, -50f, pointerCount = 1))
    }

    @Test
    fun arbiterDefersOneAndTwoFingerDragsAlongADeclaredAxis() {
        val arbiter = HomeGestureArbiter(childAxes = ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)))

        assertTrue(arbiter.dragBelongsToDeclaredChild(0f, -50f, pointerCount = 1))
        assertTrue(arbiter.dragBelongsToDeclaredChild(0f, -50f, pointerCount = 2))
        // Across the declared axis the home layer keeps its swipe.
        assertFalse(arbiter.dragBelongsToDeclaredChild(50f, 0f, pointerCount = 1))
    }

    @Test
    fun threeFingersAlwaysBelongToHomeEvenOverADeclaredAxis() {
        val arbiter = HomeGestureArbiter(childAxes = ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)))

        assertFalse(arbiter.dragBelongsToDeclaredChild(0f, -50f, pointerCount = 3))

        arbiter.shouldClaim(pressedPointerCount = 3)
        // Once claimed, a finger lifting keeps the claim.
        assertFalse(arbiter.dragBelongsToDeclaredChild(0f, -50f, pointerCount = 2))
    }

    @Test
    fun mayFireOnDragCombinesYieldingAndDeclaredAxes() {
        val arbiter = HomeGestureArbiter(childAxes = ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)))

        assertTrue(arbiter.mayFireOnDrag(50f, 0f, pointerCount = 1))
        assertFalse(arbiter.mayFireOnDrag(0f, 50f, pointerCount = 1))

        val yielded = HomeGestureArbiter()
        yielded.dispositionFor(1, true)
        assertFalse(yielded.mayFireOnDrag(50f, 0f, pointerCount = 1))
    }

    @Test
    fun resolveHandOffKeepsTheSurfaceOptInAndAddsDeclaredVertical() {
        assertTrue(ContainerGestureAxes().resolveHandOff(true))
        assertFalse(ContainerGestureAxes().resolveHandOff(false))
        assertTrue(ContainerGestureAxes(setOf(GestureAxis.VERTICAL_SCROLL)).resolveHandOff(false))
    }
}
