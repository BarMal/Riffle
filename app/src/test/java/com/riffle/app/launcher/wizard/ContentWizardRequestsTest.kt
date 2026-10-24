package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentWizardRequestsTest {
    private val handoff = AdvancedHandoff(LensDraft(sources = listOf(SourceIds.NOTIFICATIONS)))

    @Test
    fun openAndCloseShowAndHideTheWizard() {
        val requests = ContentWizardRequests()
        assertNull(requests.current.value)
        val request = WizardRequest.NewSavedLens(HomeLayoutDeviceClass.PHONE)
        requests.open(request)
        assertEquals(request, requests.current.value)
        requests.close()
        assertNull(requests.current.value)
    }

    @Test
    fun aSavedLensHandsTheDraftToItsBuilder() {
        val requests = ContentWizardRequests()
        val received = mutableListOf<AdvancedHandoff>()
        requests.open(WizardRequest.NewSavedLens(HomeLayoutDeviceClass.PHONE)) { received += it }

        assertTrue(requests.advance(handoff))

        assertEquals(listOf(handoff), received)
        assertNull(requests.current.value)
        assertNull(requests.takeEditorHandoff())
    }

    @Test
    fun aSourcePageHoldsTheDraftForTheEditorOnce() {
        val requests = ContentWizardRequests()
        requests.open(WizardRequest.FromSource(SourceIds.NOTIFICATIONS, HomeLayoutDeviceClass.PHONE))

        assertFalse(requests.advance(handoff))

        assertNull(requests.current.value)
        assertEquals(handoff, requests.takeEditorHandoff())
        assertNull(requests.takeEditorHandoff())
    }
}
