package com.riffle.app.launcher.diagnostics.pair

import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

/** The holder activity driven by hand: it makes the two starts a variant asks for, when the machine says so. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairHolderActivityTest {
    private val first = PairTarget("com.example.first", "com.example.first.Main", "First")
    private val second = PairTarget("com.example.second", "com.example.second.Main", "Second")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val h1 = HeldVariants.all[0]

    private fun controller(variant: HeldVariant = h1): ActivityController<PairHolderActivity> =
        Robolectric.buildActivity(
            PairHolderActivity::class.java,
            AndroidHolderIntents.intentFor(context, HolderRequest(first, second, variant)),
        )

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun resumed(variant: HeldVariant = h1) = controller(variant).create().start().resume().visible()

    @Test
    fun theHolderStartsTheFirstAppAdjacentAfterResumeAndSettleThenTheSecondOnceSplitAndFinishes() {
        val controller = resumed()
        val activity = controller.get()
        val shadow = shadowOf(activity)
        // Robolectric moves the clock a millisecond or two while the activity is brought up, so every wait is checked
        // with a margin on both sides: not yet 10 ms before the due time, done 10 ms after it.
        advance(290)
        assertNull(shadow.nextStartedActivity)
        advance(20)

        val firstIntent = shadow.nextStartedActivity
        assertEquals(first.packageName, firstIntent.component?.packageName)
        assertEquals(PairFlags.describe(h1.firstFlags), PairFlags.describe(firstIntent.flags))
        assertNull(shadow.nextStartedActivity)

        activity.onMultiWindowModeChanged(true, Configuration())
        advance(390)
        assertNull(shadow.nextStartedActivity)
        advance(20)
        val secondIntent = shadow.nextStartedActivity
        assertEquals(second.packageName, secondIntent.component?.packageName)
        assertEquals(PairFlags.describe(h1.secondFlags), PairFlags.describe(secondIntent.flags))
        advance(260)
        assertFalse(activity.isFinishing)
        advance(40)
        assertTrue(activity.isFinishing)
        assertNull(shadow.nextStartedActivity)
    }

    @Test
    fun withoutASplitTheHolderStartsTheSecondAppAnywayAfterTheTimeoutAndTheLogSaysSo() {
        val experiment = AppPairExperiment.shared
        runBlocking {
            experiment.launch(
                PairMethod.CUSTOM,
                first,
                second,
                0,
                object : PairStarter {
                    override fun start(start: PairStart) = Unit

                    override fun startAll(starts: List<PairStart>) = Unit

                    override fun startTrampoline(request: TrampolineRequest) = Unit

                    override fun startHolder(request: HolderRequest) = Unit
                },
                { _, _ ->
                    PairFacts(34, "Acme", "Fold", false, "412x915", "compact", "PHONE", emptyList(), emptyList())
                },
                PairLaunchOptions(scenario = h1.title, held = h1),
            )
        }
        val activity = resumed().get()
        val shadow = shadowOf(activity)
        advance(310)
        assertNotNull(shadow.nextStartedActivity)
        advance(2_980)
        assertNull(shadow.nextStartedActivity)
        advance(30)
        val secondIntent = shadow.nextStartedActivity
        assertEquals(second.packageName, secondIntent.component?.packageName)
        advance(300)
        assertTrue(activity.isFinishing)

        val trace = experiment.log.value.first().holder
        assertNotNull(trace)
        val text = trace!!.lines.joinToString("\n") { "+${it.offsetMillis} ${it.text}" }
        listOf(
            "no split after first start",
            "start first com.example.first [LAUNCH_ADJACENT|NEW_TASK]: ok",
            "start second com.example.second [NEW_TASK]: ok",
            "isInMultiWindowMode false (first look)",
            "window bounds unknown -> ",
        ).forEach { assertTrue("$it in\n$text", text.contains(it)) }
        assertEquals("finished after the second start", trace.finalState)
    }

    @Test
    fun aHolderThatLeavesTheScreenBeforeTheSettleStartsNothing() {
        val controller = resumed()
        val shadow = shadowOf(controller.get())
        advance(100)
        controller.pause().stop()
        advance(10_000)
        assertNull(shadow.nextStartedActivity)
        assertTrue(controller.get().isFinishing)
    }

    @Test
    fun aRequestWithoutExtrasFinishesAtOnceAndStartsNothing() {
        val controller = Robolectric.buildActivity(PairHolderActivity::class.java).create()
        assertTrue(controller.get().isFinishing)
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }
}
