package com.riffle.app.launcher.pair

import android.content.Intent
import com.riffle.app.launcher.diagnostics.pair.PairStart
import com.riffle.app.launcher.diagnostics.pair.PairStarter
import com.riffle.app.launcher.diagnostics.pair.TrampolineRequest
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.dock.PairApp
import com.riffle.core.domain.launcher.dock.PairLaunch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppPairLauncherTest {
    private val adjacentNewTask = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK

    private fun app(name: String) =
        PairApp(
            identity = AppIdentity(AppPackageName("com.riffle.$name"), AppActivityName(".Main")),
            label = name.replaceFirstChar { it.uppercase() },
        )

    private val pair = PairLaunch(app("notes"), app("maps"))

    /** Records each start with the virtual time it was made at; can fail chosen packages. */
    private class FakeStarter(
        private val clock: () -> Long,
        private val failing: Set<String> = emptySet(),
    ) : PairStarter {
        val starts = mutableListOf<Triple<String, Int, Long>>()

        override fun start(start: PairStart) {
            starts += Triple(start.target.packageName, start.flags, clock())
            if (start.target.packageName in failing) throw SecurityException("blocked")
        }

        override fun startAll(starts: List<PairStart>) = error("not used")

        override fun startTrampoline(request: TrampolineRequest) = error("not used")
    }

    private class Fixture(
        scope: TestScope,
        failing: Set<String> = emptySet(),
        recipe: PairRecipe = PairRecipe.DEFAULT,
    ) {
        val starter = FakeStarter({ scope.currentTime }, failing)
        val problems = mutableListOf<String>()
        val launcher =
            AppPairLauncher(
                starter = { starter },
                scope = scope,
                onProblem = { problems += it },
                recipe = recipe,
            )
    }

    @Test
    fun theFirstAppStartsAtOnceAndTheSecondAfterTheRecipesDelayBothAdjacent() =
        runTest {
            val f = Fixture(this)

            assertTrue(f.launcher.launch(pair))
            runCurrent()
            assertEquals(listOf(Triple("com.riffle.notes", adjacentNewTask, 0L)), f.starter.starts)
            advanceTimeBy(599)
            runCurrent()
            assertEquals(1, f.starter.starts.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(
                listOf(
                    Triple("com.riffle.notes", adjacentNewTask, 0L),
                    Triple("com.riffle.maps", adjacentNewTask, 600L),
                ),
                f.starter.starts,
            )
            assertTrue(f.problems.isEmpty())
        }

    @Test
    fun theRecipeNeverForcesNewTasksOrClearsThem() {
        val flags = listOf(PairRecipe.DEFAULT.firstFlags, PairRecipe.DEFAULT.secondFlags)
        flags.forEach { flag ->
            assertEquals(0, flag and Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            assertEquals(0, flag and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        assertEquals(600L, PairRecipe.DEFAULT.delayMillis)
    }

    @Test
    fun aSecondTapWhileALaunchIsInFlightIsIgnored() =
        runTest {
            val f = Fixture(this)
            f.launcher.launch(pair)
            runCurrent()
            advanceTimeBy(300)

            assertTrue(f.launcher.isInFlight)
            assertFalse(f.launcher.launch(pair))
            advanceTimeBy(300)
            runCurrent()

            assertEquals(2, f.starter.starts.size)
            assertFalse(f.launcher.isInFlight)
            // And once it is over, a pair can be launched again.
            assertTrue(f.launcher.launch(pair))
            runCurrent()
            assertEquals(3, f.starter.starts.size)
        }

    @Test
    fun swappingThePairReversesTheOrder() =
        runTest {
            val f = Fixture(this)

            f.launcher.launch(pair.copy(first = pair.second, second = pair.first))
            advanceTimeBy(600)
            runCurrent()

            assertEquals(listOf("com.riffle.maps", "com.riffle.notes"), f.starter.starts.map { it.first })
        }

    @Test
    fun ifTheFirstAppCannotBeStartedTheSecondOpensAloneAtOnceAndTheUserIsTold() =
        runTest {
            val f = Fixture(this, failing = setOf("com.riffle.notes"))

            f.launcher.launch(pair)
            runCurrent()

            assertEquals(listOf("Couldn't open Notes"), f.problems)
            assertEquals(
                listOf("com.riffle.notes" to 0L, "com.riffle.maps" to 0L),
                f.starter.starts.map { it.first to it.third },
            )
            // With nothing to be beside, the second is a plain start.
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, f.starter.starts.last().second)
        }

    @Test
    fun ifTheSecondAppCannotBeStartedTheFirstIsOpenAndTheUserIsTold() =
        runTest {
            val f = Fixture(this, failing = setOf("com.riffle.maps"))

            f.launcher.launch(pair)
            advanceTimeBy(600)
            runCurrent()

            assertEquals(listOf("Couldn't open Maps"), f.problems)
            assertEquals(2, f.starter.starts.size)
            assertFalse(f.launcher.isInFlight)
        }

    @Test
    fun ifNeitherCanBeStartedBothAreReportedAndNothingIsLeftInFlight() =
        runTest {
            val f = Fixture(this, failing = setOf("com.riffle.maps", "com.riffle.notes"))

            f.launcher.launch(pair)
            runCurrent()

            assertEquals(listOf("Couldn't open Notes", "Couldn't open Maps"), f.problems)
            assertFalse(f.launcher.isInFlight)
        }

    @Test
    fun cancelEndsALaunchThatIsWaitingForItsSecondStart() =
        runTest {
            val f = Fixture(this)
            f.launcher.launch(pair)
            runCurrent()

            f.launcher.cancel()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(1, f.starter.starts.size)
            assertFalse(f.launcher.isInFlight)
        }

    @Test
    fun aDifferentRecipeIsJustData() =
        runTest {
            val recipe =
                PairRecipe(firstFlags = Intent.FLAG_ACTIVITY_NEW_TASK, secondFlags = adjacentNewTask, delayMillis = 250)
            val f = Fixture(this, recipe = recipe)

            f.launcher.launch(pair)
            advanceTimeBy(250)
            runCurrent()

            assertEquals(
                listOf(Intent.FLAG_ACTIVITY_NEW_TASK to 0L, adjacentNewTask to 250L),
                f.starter.starts.map { it.second to it.third },
            )
        }
}
