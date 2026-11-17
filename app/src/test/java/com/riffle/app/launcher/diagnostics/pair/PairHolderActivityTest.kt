package com.riffle.app.launcher.diagnostics.pair

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
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

    private fun probe(id: String) = HeldVariants.probes.single { it.id == id }

    @Test
    fun theListedHolderIsAnActivityOfOursStartedWithTheHolderFlagsAndListedInRecents() {
        val p2 = probe("P2")
        val intent = AndroidHolderIntents.intentFor(context, HolderRequest(first, second, p2))
        assertEquals(PairHolderListedActivity::class.java.name, intent.component?.className)
        val plain = AndroidHolderIntents.intentFor(context, HolderRequest(first, second, probe("P1")))
        assertEquals(PairHolderActivity::class.java.name, plain.component?.className)
        assertEquals(plain.flags, intent.flags)
        assertEquals(
            "NEW_TASK|MULTIPLE_TASK|NO_ANIMATION",
            PairFlags.describe(intent.flags),
        )
        val info =
            context.packageManager.getActivityInfo(ComponentName(context, PairHolderListedActivity::class.java), 0)
        assertEquals(0, info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
        assertEquals(".pairholder", info.taskAffinity)
        val base = context.packageManager.getActivityInfo(ComponentName(context, PairHolderActivity::class.java), 0)
        assertEquals(ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS, base.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
        assertEquals(AndroidHolderIntents.requestFrom(intent.extras)?.variant, p2)
    }

    @Test
    fun theListedHolderRunsTheSameHoldOnlyProbe() {
        val p2 = probe("P2")
        val intent = AndroidHolderIntents.intentFor(context, HolderRequest(first, second, p2))
        val activity =
            Robolectric.buildActivity(PairHolderListedActivity::class.java, intent).create().start().resume().visible()
                .get()
        val shadow = shadowOf(activity)
        advance(310)
        assertEquals(first.packageName, shadow.nextStartedActivity.component?.packageName)
        advance(6_000)
        assertNull(shadow.nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun holdOnlyProbeStartsExactlyOneActivityAndFinishesFiveSecondsAfterIt() {
        val p1 = probe("P1")
        val activity = resumed(p1).get()
        val shadow = shadowOf(activity)
        advance(310)
        val firstIntent = shadow.nextStartedActivity
        assertEquals(first.packageName, firstIntent.component?.packageName)
        assertEquals(PairFlags.describe(p1.firstFlags), PairFlags.describe(firstIntent.flags))
        activity.onMultiWindowModeChanged(true, Configuration())
        advance(4_900)
        assertNull("no second start", shadow.nextStartedActivity)
        assertFalse(activity.isFinishing)
        advance(200)
        assertTrue(activity.isFinishing)
        assertNull(shadow.nextStartedActivity)
    }

    @Test
    fun theNeverFinishProbeStartsTwoActivitiesAndFinishesOnlyAfterTheHold() {
        val p3 = probe("P3")
        val activity = resumed(p3).get()
        val shadow = shadowOf(activity)
        advance(310)
        assertEquals(first.packageName, shadow.nextStartedActivity.component?.packageName)
        activity.onMultiWindowModeChanged(true, Configuration())
        advance(410)
        val secondIntent = shadow.nextStartedActivity
        assertEquals(second.packageName, secondIntent.component?.packageName)
        assertEquals(PairFlags.describe(p3.secondFlags), PairFlags.describe(secondIntent.flags))
        advance(4_900)
        assertFalse("still held 4.9 s after the second start", activity.isFinishing)
        advance(200)
        assertTrue(activity.isFinishing)
        assertNull(shadow.nextStartedActivity)
    }

    @Test
    fun theHolderWritesItsOwnFactsAtTheStartForTheLog() {
        val p1 = probe("P1")
        val experiment = AppPairExperiment.shared
        val quiet =
            object : PairStarter {
                override fun start(start: PairStart) = Unit

                override fun startAll(starts: List<PairStart>) = Unit

                override fun startTrampoline(request: TrampolineRequest) = Unit

                override fun startHolder(request: HolderRequest) = Unit
            }
        runBlocking {
            experiment.launch(
                PairMethod.CUSTOM,
                first,
                second,
                0,
                quiet,
                { _, _ ->
                    PairFacts(34, "Acme", "Fold", false, "412x915", "compact", "PHONE", emptyList(), emptyList())
                },
                PairLaunchOptions(scenario = p1.title, held = p1),
            )
        }
        resumed(p1)
        advance(10)
        val lines = experiment.log.value.first().holder!!.lines.map { it.text }
        val facts = lines.filter { it.startsWith(PairHolderSession.FACT_PREFIX) }
        assertEquals(lines.toString(), 3, facts.size)
        assertTrue(facts[0], facts[0].startsWith("fact: activityInfo: PairHolderActivity flags=0x"))
        assertTrue(facts[0], facts[0].contains("excludeFromRecents=true"))
        assertTrue(facts[0], facts[0].contains("resizeableActivity: no public API reads it"))
        assertTrue(facts[1], facts[1].startsWith("fact: FEATURE_FREEFORM_WINDOW_MANAGEMENT="))
        assertTrue(facts[2], facts[2].startsWith("fact: holds the HOME role: "))
        assertTrue(lines.toString(), lines.any { it.startsWith("config smallestScreenWidthDp=") })
    }
}
