package com.riffle.app.launcher.diagnostics.pair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The session over a fake host on a manual clock: what the ticker, the callbacks and the split-lost line write. */
class PairHolderSessionTest {
    private var now = 1_000L
    private var multi = false
    private var bounds = "[0,0][724,784] 724x784 px (724x784 dp)"
    private var config = "smallestScreenWidthDp=724 screenWidthDp=724"
    private var pip = false
    private val started = mutableListOf<String>()
    private var finished = false
    private val posted = mutableListOf<Pair<Long, Runnable>>()
    private var report: HolderTrace? = null
    private val first = PairTarget("com.example.first", "com.example.first.Main", "First")
    private val second = PairTarget("com.example.second", "com.example.second.Main", "Second")

    private val host =
        object : HolderHost {
            override fun isInMultiWindowMode() = multi

            override fun boundsText() = bounds

            override fun isInPictureInPicture() = pip

            override fun configText() = config

            override fun holderFacts() = listOf("activityInfo: Fake", "holds the HOME role: true")

            override fun start(
                target: PairTarget,
                flags: Int,
            ) {
                started += target.packageName
            }

            override fun finishHolder() {
                finished = true
            }

            override fun post(
                delayMillis: Long,
                task: Runnable,
            ) {
                posted += (now + delayMillis) to task
            }

            override fun cancel(task: Runnable) {
                posted.removeAll { it.second === task }
            }
        }

    private fun session(variant: HeldVariant) =
        PairHolderSession(HolderRequest(first, second, variant), host, { now }) { report = it }

    // Runs every posted task that is due, in time order, moving the clock to its time.
    private fun advanceTo(time: Long) {
        while (true) {
            val next = posted.filter { it.first <= time }.minByOrNull { it.first } ?: break
            posted.remove(next)
            now = maxOf(now, next.first)
            next.second.run()
        }
        now = time
    }

    private fun text() = report!!.lines.joinToString("\n") { "+${it.offsetMillis} ${it.text}" }

    @Test
    fun theTickerWritesEachChangeOfBoundsConfigAndPipOnceNoMatterHowOftenItLooks() {
        val s = session(HeldVariants.all[0])
        s.on(HolderEvent.Created)
        advanceTo(2_000)
        bounds = "[0,0][362,784] 362x784 px (362x784 dp)"
        config = "smallestScreenWidthDp=362 screenWidthDp=362"
        advanceTo(3_000)
        pip = true
        advanceTo(4_000)
        s.on(HolderEvent.Destroyed)
        val t = text()
        assertEquals(t, 1, Regex("window bounds \\[0,0\\]\\[724,784\\].* -> \\[0,0\\]\\[362,784\\]").findAll(t).count())
        assertEquals(t, 1, Regex("window bounds unknown -> ").findAll(t).count())
        assertEquals(t, 1, Regex("config smallestScreenWidthDp=724 screenWidthDp=724 -> ").findAll(t).count())
        assertEquals(t, 1, Regex("isInPictureInPictureMode false -> true").findAll(t).count())
        assertEquals(t, 1, Regex("isInMultiWindowMode false \\(first look\\)").findAll(t).count())
    }

    @Test
    fun theTickerStopsWhenTheActivityIsDestroyed() {
        val s = session(HeldVariants.all[0])
        s.on(HolderEvent.Created)
        advanceTo(1_600)
        s.on(HolderEvent.Destroyed)
        assertTrue("nothing left posted: $posted", posted.isEmpty())
        bounds = "changed"
        advanceTo(9_000)
        assertTrue(text(), !text().contains("changed"))
    }

    @Test
    fun theTickerSeesASplitTheCallbacksMissedAndFeedsTheMachine() {
        val s = session(HeldVariants.all[0])
        s.on(HolderEvent.Created)
        s.on(HolderEvent.Resumed)
        advanceTo(1_400)
        assertEquals(listOf(first.packageName), started)
        multi = true
        advanceTo(1_500)
        assertTrue(text(), text().contains("split observed via poll 250 ms"))
        advanceTo(2_000)
        assertEquals(listOf(first.packageName, second.packageName), started)
    }

    @Test
    fun aDropOutOfSplitWritesHowLongItLastedAndWhatTheHolderWasDoing() {
        val s = session(HeldVariants.probes[2])
        s.on(HolderEvent.Created)
        s.on(HolderEvent.Resumed)
        advanceTo(1_400)
        multi = true
        s.on(HolderEvent.MultiWindowChanged(true))
        advanceTo(1_500)
        advanceTo(1_900)
        assertEquals(listOf(first.packageName, second.packageName), started)
        multi = false
        s.on(HolderEvent.MultiWindowChanged(false))
        val t = text()
        assertTrue(t, t.contains("isInMultiWindowMode true -> false"))
        val lost = report!!.lines.single { it.text.startsWith("split lost") }.text
        assertEquals("split lost after 500 ms in split (second started 100 ms ago)", lost)
        assertEquals(lost, PairHolderSession.splitLostText(500, "second started 100 ms ago"))
        assertTrue(!finished)
    }

    @Test
    fun theHolderFactsAreWrittenOnceAtTheStartWithTheirMarker() {
        session(HeldVariants.all[0]).on(HolderEvent.Created)
        val facts = report!!.lines.map { it.text }.filter { it.startsWith(PairHolderSession.FACT_PREFIX) }
        assertEquals(listOf("fact: activityInfo: Fake", "fact: holds the HOME role: true"), facts)
    }

    @Test
    fun everyCallbackTheHolderGetsIsWritten() {
        val s = session(HeldVariants.all[0])
        s.on(HolderEvent.Created)
        s.on(HolderEvent.Started)
        s.on(HolderEvent.Resumed)
        s.on(HolderEvent.TopResumedChanged(true))
        s.on(HolderEvent.FocusChanged(true))
        s.on(HolderEvent.ConfigurationChanged)
        s.on(HolderEvent.Paused)
        s.on(HolderEvent.Stopped)
        val t = text()
        listOf(
            "onCreate",
            "onStart",
            "onResume",
            "onTopResumedActivityChanged(true)",
            "onWindowFocusChanged(true)",
            "onConfigurationChanged",
            "onPause",
            "onStop",
        ).forEach { assertTrue("$it in\n$t", t.contains(it)) }
    }
}
