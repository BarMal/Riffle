package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.editor.WidgetSlot
import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.GoalSetup
import com.riffle.core.domain.launcher.workspace.wizard.WizardExit
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardStateCodecTest {
    private val work = AppProfile(AppProfileId("10"), AppProfileType.WORK)

    private val workMail =
        WizardTestFixtures.app("mail").let { it.copy(identity = it.identity.copy(profile = work)) }

    private val full =
        WizardState(
            step = WizardStep.PLACE,
            setup =
                GoalSetup(
                    goal = ContentGoalId.NOTIFICATIONS,
                    filterId = "work",
                    lookId = "list",
                    apps = listOf(WizardTestFixtures.app("maps"), workMail),
                    feeds = listOf("feed-1", "feed-2"),
                    query = "weekend",
                ),
            destination = Destination.WIDGET,
            widgetTarget = WidgetTarget(ContainerId("now"), WidgetSlot(2, 3)),
            saveToLibrary = true,
            name = "Inbox",
        )

    @Test
    fun everyChoiceRoundTrips() {
        assertEquals(full, WizardStateCodec.decode(WizardStateCodec.encode(full)))
        val empty = WizardState()
        assertEquals(empty, WizardStateCodec.decode(WizardStateCodec.encode(empty)))
        val newPage = full.copy(widgetTarget = WidgetTarget(null, WidgetSlot(0, 0)), name = null)
        assertEquals(newPage, WizardStateCodec.decode(WizardStateCodec.encode(newPage)))
    }

    @Test
    fun theEncodingHoldsOnlyBundleFriendlyValues() {
        fun check(value: Any?) {
            when (value) {
                null, is String, is Int, is Boolean -> Unit
                is ArrayList<*> -> value.forEach(::check)
                else -> throw AssertionError("Not saveable: ${value::class}")
            }
        }
        check(WizardStateCodec.encode(full))
    }

    @Test
    fun aClosedRunIsNotKept() {
        val closed = full.copy(exit = WizardExit.Finished)
        assertNull(WizardStateCodec.decode(WizardStateCodec.encode(closed))?.exit)
    }

    @Test
    fun anythingUnreadableDecodesToNull() {
        assertNull(WizardStateCodec.decode(null))
        assertNull(WizardStateCodec.decode("garbage"))
        assertNull(WizardStateCodec.decode(arrayListOf(2, "SOURCE")))
        assertNull(WizardStateCodec.decode(arrayListOf(1, "NOT_A_STEP", null, null, null, false, null)))
        val unknownGoal = WizardStateCodec.encode(full)
        unknownGoal[2] = arrayListOf("gone", "a", "b", arrayListOf<Any>(), arrayListOf<Any>(), "")
        assertNull(WizardStateCodec.decode(unknownGoal))
        assertTrue(WizardStateCodec.decode(arrayListOf(1)) == null)
    }
}
