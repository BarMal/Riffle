package com.riffle.core.domain.launcher.summon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SummonGestureTest {
    private val config =
        SummonGestureConfig(
            timings = SummonTimings(),
            bounds = RingBounds(40f, 100f, 1040f, 2200f),
            radius = 300f,
            clearance = 90f,
            deadZone = 80f,
            pushThrough = 130f,
            touchSlop = 24f,
        )

    // Eight slots clockwise from the top: app, previewable app, app, app, favourites, folder, widget, app.
    private val root =
        listOf(
            SummonSlotRole.APP,
            SummonSlotRole.PREVIEW,
            SummonSlotRole.APP,
            SummonSlotRole.APP,
            SummonSlotRole.FAVOURITES,
            SummonSlotRole.FOLDER,
            SummonSlotRole.PREVIEW,
            SummonSlotRole.APP,
        )
    private val folder = listOf(SummonSlotRole.APP, SummonSlotRole.APP, SummonSlotRole.APP)

    private val roles: (List<Int>) -> List<SummonSlotRole> = { path -> if (path.isEmpty()) root else folder }

    private val centre = RingPoint(540f, 1100f)

    private fun at(
        degrees: Float,
        distance: Float = 200f,
    ): RingPoint {
        val a = Math.toRadians(degrees.toDouble())
        return RingPoint(
            centre.x + (distance * kotlin.math.cos(a)).toFloat(),
            centre.y + (distance * kotlin.math.sin(a)).toFloat(),
        )
    }

    private class Run(
        val config: SummonGestureConfig,
        val roles: (List<Int>) -> List<SummonSlotRole>,
    ) {
        var phase: SummonPhase = SummonPhase.Idle
        val effects = mutableListOf<SummonEffect>()

        fun send(event: SummonEvent): Run {
            val step = SummonGesture.reduce(phase, event, config, roles)
            phase = step.phase
            effects += step.effects
            return this
        }
    }

    private fun armed(): Run =
        Run(config, roles)
            .send(SummonEvent.Down(centre, 0))
            .send(SummonEvent.Tick(200))

    @Test
    fun aHoldArmsTheRingAfterTheArmTime() {
        val run = Run(config, roles).send(SummonEvent.Down(centre, 0)).send(SummonEvent.Tick(199))
        assertIs<SummonPhase.Pending>(run.phase)
        assertEquals(200L, SummonGesture.nextDeadline(run.phase, config, roles))
        run.send(SummonEvent.Tick(200))
        val open = assertIs<SummonPhase.Open>(run.phase)
        assertEquals(8, open.layout.angles.size)
        assertEquals(listOf<SummonEffect>(SummonEffect.Armed), run.effects)
    }

    @Test
    fun movingBeforeTheRingArmsHandsTheGestureBack() {
        val run =
            Run(
                config,
                roles,
            ).send(SummonEvent.Down(centre, 0)).send(SummonEvent.Move(RingPoint(540f, 1200f), 50))
        assertEquals(SummonPhase.Idle, run.phase)
        assertEquals(listOf<SummonEffect>(SummonEffect.PassedThrough(centre, RingPoint(540f, 1200f))), run.effects)
    }

    @Test
    fun aTapThatNeverArmsIsJustATap() {
        val run = Run(config, roles).send(SummonEvent.Down(centre, 0)).send(SummonEvent.Up(100))
        assertEquals(SummonPhase.Idle, run.phase)
        assertEquals(listOf<SummonEffect>(SummonEffect.Tapped(centre)), run.effects)
    }

    @Test
    fun releasingInTheCentreWithoutAimingOpensTheMenu() {
        val run = armed().send(SummonEvent.Up(600))
        assertEquals(SummonEffect.MenuRequested, run.effects.last())
    }

    @Test
    fun aimingTicksAndReleasingLaunches() {
        val run = armed().send(SummonEvent.Move(at(-90f), 250))
        assertEquals(SummonEffect.Aimed(listOf(), 0), run.effects.last())
        run.send(SummonEvent.Up(300))
        assertEquals(SummonEffect.Launch(listOf(), 0), run.effects.last())
        assertEquals(SummonPhase.Idle, run.phase)
    }

    @Test
    fun aimingAwayAndBackToTheCentreCancels() {
        val run =
            armed().send(
                SummonEvent.Move(at(-90f), 250),
            ).send(SummonEvent.Move(centre, 300)).send(SummonEvent.Up(320))
        assertEquals(SummonEffect.Cancelled, run.effects.last())
    }

    @Test
    fun restingOnAPreviewableSlotShowsItsCardAndReleaseStillOpensTheApp() {
        val run = armed().send(SummonEvent.Move(at(-45f), 250))
        assertEquals(600L, SummonGesture.nextDeadline(run.phase, config, roles))
        run.send(SummonEvent.Tick(600))
        assertEquals(SummonEffect.Previewed(listOf(), 1), run.effects.last())
        run.send(SummonEvent.Up(700))
        assertEquals(SummonEffect.Launch(listOf(), 1), run.effects.last())
    }

    @Test
    fun pushingThroughAPreviewableSlotMakesTheCardLiveAndReleaseKeepsIt() {
        val run = armed().send(SummonEvent.Move(at(-45f), 250)).send(SummonEvent.Move(at(-45f, 460f), 300))
        assertTrue(SummonEffect.WentLive(listOf(), 1) in run.effects)
        assertTrue((run.phase as SummonPhase.Open).live)
        run.send(SummonEvent.Up(400))
        assertEquals(SummonEffect.KeepCard(listOf(), 1), run.effects.last())
    }

    @Test
    fun restingOnAFolderOpensItsRingInPlaceAndTheCentreGoesBack() {
        val run = armed().send(SummonEvent.Move(at(135f), 250)).send(SummonEvent.Tick(250 + 450))
        assertEquals(SummonEffect.OpenedFolder(listOf(5)), run.effects.last())
        val open = assertIs<SummonPhase.Open>(run.phase)
        assertEquals(listOf(5), open.path)
        assertEquals(3, open.layout.angles.size)
        assertEquals(centre, open.layout.centre)
        assertNull(open.aim)

        run.send(SummonEvent.Move(centre, 800)).send(SummonEvent.Tick(800 + 450))
        assertEquals(SummonEffect.WentBack(listOf()), run.effects.last())
        assertEquals(listOf<Int>(), (run.phase as SummonPhase.Open).path)
    }

    @Test
    fun pushingThroughAFolderOpensItAtOnce() {
        val run = armed().send(SummonEvent.Move(at(135f), 250)).send(SummonEvent.Move(at(135f, 460f), 280))
        assertEquals(SummonEffect.OpenedFolder(listOf(5)), run.effects.last())
    }

    @Test
    fun launchingInsideAFolderCarriesThePath() {
        val run = armed().send(SummonEvent.Move(at(135f, 460f), 250))
        run.send(SummonEvent.Move(at(-90f), 400)).send(SummonEvent.Up(450))
        assertEquals(SummonEffect.Launch(listOf(5), 0), run.effects.last())
    }

    @Test
    fun favouritesOpensTheDockAndReleasingOverADockIconLaunchesIt() {
        val run = armed().send(SummonEvent.Move(at(90f), 250))
        assertTrue(SummonEffect.FavouritesShown in run.effects)
        run.send(SummonEvent.Move(at(90f, 520f), 300, dockIndex = 2))
        assertEquals(SummonEffect.AimedDock(2), run.effects.last())
        run.send(SummonEvent.Up(350))
        assertEquals(SummonEffect.LaunchFavourite(2), run.effects.last())
    }

    @Test
    fun theDockIsIgnoredUntilFavouritesHasBeenEntered() {
        val run = armed().send(SummonEvent.Move(at(-90f), 250, dockIndex = 1)).send(SummonEvent.Up(300))
        assertEquals(SummonEffect.Launch(listOf(), 0), run.effects.last())
    }

    @Test
    fun releasingOnAFolderOrFavouritesLaunchesNothing() {
        val folderRun = armed().send(SummonEvent.Move(at(135f), 250)).send(SummonEvent.Up(300))
        assertEquals(SummonEffect.ReleasedOn(listOf(), 5, SummonSlotRole.FOLDER), folderRun.effects.last())
        val favRun = armed().send(SummonEvent.Move(at(90f), 250)).send(SummonEvent.Up(300))
        assertEquals(SummonEffect.ReleasedOn(listOf(), 4, SummonSlotRole.FAVOURITES), favRun.effects.last())
    }

    @Test
    fun anEmptyLevelStillOpensAndOnlyOffersTheMenu() {
        val run = Run(config) { emptyList() }.send(SummonEvent.Down(centre, 0)).send(SummonEvent.Tick(200))
        run.send(SummonEvent.Move(at(0f), 250)).send(SummonEvent.Up(300))
        assertEquals(SummonEffect.Cancelled, run.effects.last())
    }

    @Test
    fun cancelAlwaysReturnsToIdle() {
        val run = armed().send(SummonEvent.Move(at(-90f), 250)).send(SummonEvent.Cancel)
        assertEquals(SummonPhase.Idle, run.phase)
        assertEquals(SummonEffect.Cancelled, run.effects.last())
    }
}
