package com.riffle.app.launcher.diagnostics.pair

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairScriptRunnerTest {
    private class FakeLifecycle(
        start: Boolean = true,
    ) : PairScriptLifecycle {
        val state = MutableStateFlow(start)
        override val resumed: StateFlow<Boolean> = state
    }

    private class FakeLog : PairScriptLog {
        val infos = mutableMapOf<Long, PairScriptInfo?>()

        override fun update(
            entryId: Long,
            change: (PairScriptInfo) -> PairScriptInfo,
        ) {
            infos[entryId] = infos[entryId]?.let(change)
        }
    }

    private class FakeLauncher(
        private val lifecycle: FakeLifecycle,
        private val missing: Set<String> = emptySet(),
    ) : PairScriptLauncher {
        val fired = mutableListOf<String>()
        val returns = mutableListOf<Unit>()
        val firedAt = mutableListOf<Long>()
        var clock: () -> Long = { 0 }

        /** What launching a step does to the launcher's window: by default the apps cover it. */
        var coversLauncher = true

        /** What the return attempt does: by default the launcher comes back at once. */
        var onReturn: () -> Unit = { lifecycle.state.value = true }
        var log: FakeLog? = null

        override fun resolve(packageName: String) =
            if (packageName in missing) null else PairTarget(packageName, "$packageName.Main", packageName)

        override suspend fun fire(
            step: PairScriptStep,
            info: PairScriptInfo,
            first: PairTarget,
            second: PairTarget,
        ): Long {
            fired += step.name
            firedAt += clock()
            if (coversLauncher) lifecycle.state.value = false
            val id = fired.size.toLong()
            log?.infos?.set(id, info)
            return id
        }

        override fun returnToLauncher() {
            returns += Unit
            onReturn()
        }
    }

    private class Rig(
        scope: TestScope,
        steps: List<PairScriptStep>,
        resumed: Boolean = true,
        missing: Set<String> = emptySet(),
    ) {
        val lifecycle = FakeLifecycle(resumed)
        val log = FakeLog()
        val toasts = mutableListOf<String>()
        val launcher =
            FakeLauncher(lifecycle, missing).also {
                it.log = log
                it.clock = { scope.currentTime }
            }
        val runner =
            PairScriptRunner(
                scope.backgroundScope,
                { scope.currentTime },
                lifecycle,
                { toasts += it },
                log,
                steps,
            )

        fun start() = runner.start(launcher, "com.example.notes", "com.example.maps")
    }

    private fun TestScope.rig(
        resumed: Boolean = true,
        missing: Set<String> = emptySet(),
        stepCount: Int = 3,
    ) = Rig(this, PairScript.steps.take(stepCount), resumed, missing)

    private suspend fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun aStepFiresOnlyWhileTheLauncherIsResumedAndHasBeenForTheSettleTime() =
        runTest {
            val rig = rig(resumed = false)
            assertTrue(rig.start())
            advance(10_000)
            assertTrue(rig.launcher.fired.isEmpty())

            rig.lifecycle.state.value = true
            advance(999)
            assertTrue(rig.launcher.fired.isEmpty())
            advance(2)
            assertEquals(listOf("Warm-up: open both"), rig.launcher.fired)
            assertEquals(listOf("Step 1 of 3: Warm-up: open both"), rig.toasts)
        }

    @Test
    fun aPauseDuringTheSettleTimeStartsTheSettleTimeAgain() =
        runTest {
            val rig = rig()
            rig.start()
            advance(600)
            rig.lifecycle.state.value = false
            advance(100)
            rig.lifecycle.state.value = true
            advance(900)
            assertTrue(rig.launcher.fired.isEmpty())
            advance(200)
            assertEquals(1, rig.launcher.fired.size)
        }

    @Test
    fun afterFiringItHoldsForTheDwellAndThenTriesToStartTheLauncher() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = {}
            rig.start()
            advance(1_001)
            assertEquals(1, rig.launcher.fired.size)
            advance(3_900)
            assertTrue(rig.launcher.returns.isEmpty())
            advance(200)
            assertEquals(1, rig.launcher.returns.size)
            assertEquals("ok", rig.log.infos[1]?.returnAttempt)
        }

    @Test
    fun itContinuesWhenTheAutomaticReturnWorks() =
        runTest {
            val rig = rig()
            rig.start()
            advance(1_001)
            advance(4_001)
            val first = rig.log.infos[1]
            assertEquals("ok", first?.returnAttempt)
            assertEquals("automatically after 0 ms", first?.returned)
            assertEquals(1, rig.launcher.fired.size)
            advance(1_000)
            assertEquals(listOf("Warm-up: open both", "Reuse both"), rig.launcher.fired)
            assertEquals("Step 2 of 3: Reuse both", rig.toasts.last())
            assertTrue(rig.log.infos[2]?.firstOpen == true && rig.log.infos[2]?.secondOpen == true)
            assertFalse(rig.log.infos[1]?.firstOpen == true)
        }

    @Test
    fun itContinuesWhenTheReturnIsRefusedAndTheUserComesBackLater() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = { throw SecurityException("background start blocked") }
            rig.start()
            advance(1_001)
            advance(4_001)
            assertEquals("refused: SecurityException: background start blocked", rig.log.infos[1]?.returnAttempt)
            advance(5_000)
            assertNull(rig.log.infos[1]?.returned)
            assertEquals(1, rig.launcher.fired.size)

            rig.lifecycle.state.value = true
            runCurrent()
            assertEquals("by the user after 5002 ms", rig.log.infos[1]?.returned)
            advance(1_001)
            assertEquals(2, rig.launcher.fired.size)
        }

    @Test
    fun aReturnThatRaisesNothingButTakesLongerThanTheAutomaticWindowIsCreditedToTheUser() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = {}
            rig.start()
            advance(1_001)
            advance(4_001)
            advance(7_000)
            rig.lifecycle.state.value = true
            runCurrent()
            assertEquals("ok", rig.log.infos[1]?.returnAttempt)
            assertEquals("by the user after 7002 ms", rig.log.infos[1]?.returned)
        }

    @Test
    fun ifTheLauncherIsNotResumedWithinTwentySecondsItLogsThatAndKeepsWaiting() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = { throw IllegalStateException("no") }
            rig.start()
            advance(1_001)
            advance(4_001)
            advance(20_000)
            assertEquals("not within 20 s", rig.log.infos[1]?.returned)
            advance(600_000)
            assertEquals(1, rig.launcher.fired.size)
            assertEquals(PairScriptState.Running(1, 3, "Warm-up: open both"), rig.runner.state.value)

            rig.lifecycle.state.value = true
            runCurrent()
            assertTrue(rig.log.infos[1]?.returned?.startsWith("not within 20 s, then by the user after") == true)
            advance(1_001)
            assertEquals(2, rig.launcher.fired.size)
        }

    @Test
    fun aLauncherThatNeverLeftIsNotedAndTheScriptContinuesAfterTheDwell() =
        runTest {
            val rig = rig()
            rig.launcher.coversLauncher = false
            rig.start()
            advance(1_001)
            advance(3_900)
            assertEquals(1, rig.launcher.fired.size)
            advance(200)
            assertEquals(listOf("launcher stayed in front"), rig.log.infos[1]?.notes)
            assertTrue(rig.launcher.returns.isEmpty())
            assertNull(rig.log.infos[1]?.returned)
            advance(1_001)
            assertEquals(2, rig.launcher.fired.size)
        }

    @Test
    fun stopCancelsPendingWaitsAndNothingMoreFires() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = {}
            rig.start()
            advance(1_001)
            advance(2_000)
            rig.runner.stop()
            assertEquals(PairScriptState.Stopped(1, 3), rig.runner.state.value)
            advance(600_000)
            rig.lifecycle.state.value = true
            advance(600_000)
            assertEquals(1, rig.launcher.fired.size)
            assertTrue(rig.launcher.returns.isEmpty())
        }

    @Test
    fun stopBeforeTheFirstStepFiresStopsItFromFiring() =
        runTest {
            val rig = rig()
            rig.start()
            advance(500)
            rig.runner.stop()
            advance(60_000)
            assertTrue(rig.launcher.fired.isEmpty())
            assertTrue(rig.toasts.isEmpty())
        }

    @Test
    fun aMissingAppRefusesToStartAndSaysWhichOne() =
        runTest {
            val rig = rig(missing = setOf("com.example.maps"))
            assertFalse(rig.start())
            advance(60_000)
            assertEquals(PairScriptState.Missing(listOf("com.example.maps")), rig.runner.state.value)
            assertTrue(rig.launcher.fired.isEmpty())
            assertTrue(rig.toasts.isEmpty())
        }

    @Test
    fun bothMissingAreListedInOrder() =
        runTest {
            val rig = rig(missing = setOf("com.example.maps", "com.example.notes"))
            assertFalse(rig.start())
            assertEquals(
                PairScriptState.Missing(listOf("com.example.notes", "com.example.maps")),
                rig.runner.state.value,
            )
        }

    @Test
    fun aSecondPressWhileRunningIsIgnored() =
        runTest {
            val rig = rig()
            rig.launcher.onReturn = {}
            assertTrue(rig.start())
            assertFalse(rig.start())
            advance(1_001)
            assertEquals(1, rig.launcher.fired.size)
            assertFalse(rig.start())
            advance(10_000)
            assertEquals(1, rig.launcher.fired.size)
        }

    @Test
    fun theWholeScriptRunsInOrderAndFinishesWithAToast() =
        runTest {
            val rig = rig()
            rig.start()
            repeat(3) { advance(1_001 + 4_001) }
            assertEquals(PairScript.steps.take(3).map { it.name }, rig.launcher.fired)
            assertEquals(
                listOf(
                    "Step 1 of 3: Warm-up: open both",
                    "Step 2 of 3: Reuse both",
                    "Step 3 of 3: Reset both",
                    "Script finished",
                ),
                rig.toasts,
            )
            assertEquals(PairScriptState.Finished(3), rig.runner.state.value)
            assertEquals(listOf(1, 2, 3), rig.log.infos.values.map { it?.step })
        }

    @Test
    fun aFinishedRunCanBeStartedAgain() =
        runTest {
            val rig = rig(stepCount = 1)
            rig.start()
            advance(1_001 + 4_001)
            assertEquals(PairScriptState.Finished(1), rig.runner.state.value)
            assertTrue(rig.start())
            advance(1_001)
            assertEquals(2, rig.launcher.fired.size)
        }
}
