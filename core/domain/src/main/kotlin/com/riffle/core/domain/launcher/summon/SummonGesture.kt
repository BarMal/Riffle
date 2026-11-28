package com.riffle.core.domain.launcher.summon

import kotlin.math.abs

/** What a slot does when aimed at, independent of what it shows. */
enum class SummonSlotRole {
    APP,

    /** An app or widget with a card to show (live Stream content, now playing, the next event). */
    PREVIEW,
    FOLDER,
    FAVOURITES,
}

/**
 * Pixel sizes and timings for one gesture. [deadZone]: the centre radius where nothing is aimed (and, inside a folder,
 * where resting goes back up). [pushThrough]: how far past the ring the finger goes to "push through" a slot.
 * [touchSlop]: how far a press may wander before the ring arms and still count as a hold.
 */
data class SummonGestureConfig(
    val timings: SummonTimings,
    val bounds: RingBounds,
    val radius: Float,
    val clearance: Float,
    val deadZone: Float,
    val pushThrough: Float,
    val touchSlop: Float,
    val hysteresisDegrees: Float = DEFAULT_HYSTERESIS_DEGREES,
    val keep: SummonKeepGesture = SummonKeepGesture.PUSH_THROUGH,
)

sealed interface SummonEvent {
    data class Down(val point: RingPoint, val atMillis: Long) : SummonEvent

    /**
     * [dockIndex]: the Dock icon under the finger, when the Dock is showing (the UI hit-tests it). [overReadout]: the
     * finger is on the readout card.
     */
    data class Move(
        val point: RingPoint,
        val atMillis: Long,
        val dockIndex: Int? = null,
        val overReadout: Boolean = false,
    ) : SummonEvent

    data class Up(val atMillis: Long) : SummonEvent

    /** Time passing with the finger still; the UI sends it at [SummonGesture.nextDeadline]. */
    data class Tick(val atMillis: Long) : SummonEvent

    data object Cancel : SummonEvent
}

sealed interface SummonPhase {
    data object Idle : SummonPhase

    data class Pending(val origin: RingPoint, val downAtMillis: Long) : SummonPhase

    /**
     * The ring is showing. [path] is the folders opened from the top level (slot indices); [layout] is the current
     * level's. [aim] is the slot pointed at since [aimSinceMillis]; [previewing] once its card shows; [live] once
     * pushed through, so release keeps the card. [inCentreSinceMillis] is set while the finger rests in the dead
     * zone. With [SummonKeepGesture.REST_TO_LOCK] a previewing card slot is locked: the finger can travel (to the
     * readout, [overReadout]) without re-aiming, and resting on another slot, or the centre when [candidate] is null,
     * since [candidateSinceMillis] moves the lock there.
     */
    data class Open(
        val origin: RingPoint,
        val path: List<Int>,
        val layout: RingLayout,
        val finger: RingPoint,
        val aim: Int? = null,
        val aimSinceMillis: Long = 0,
        val previewing: Boolean = false,
        val live: Boolean = false,
        val favouritesOpen: Boolean = false,
        val dockAim: Int? = null,
        val inCentreSinceMillis: Long? = null,
        val leftCentre: Boolean = false,
        val overReadout: Boolean = false,
        val candidate: Int? = null,
        val candidateSinceMillis: Long? = null,
    ) : SummonPhase
}

/** What the UI should do (haptics, drawing, launching) as a result of an event. */
sealed interface SummonEffect {
    data object Armed : SummonEffect

    data class Aimed(val path: List<Int>, val index: Int) : SummonEffect

    data class AimedDock(val index: Int) : SummonEffect

    data class Previewed(val path: List<Int>, val index: Int) : SummonEffect

    data class WentLive(val path: List<Int>, val index: Int) : SummonEffect

    /** A locked card's finger reached the readout: letting go now keeps the card. */
    data class ReachedReadout(val path: List<Int>, val index: Int) : SummonEffect

    data class OpenedFolder(val path: List<Int>) : SummonEffect

    data class WentBack(val path: List<Int>) : SummonEffect

    data object FavouritesShown : SummonEffect

    data object FavouritesHidden : SummonEffect

    data class Launch(val path: List<Int>, val index: Int) : SummonEffect

    data class LaunchFavourite(val index: Int) : SummonEffect

    data class KeepCard(val path: List<Int>, val index: Int) : SummonEffect

    data class ReleasedOn(val path: List<Int>, val index: Int, val role: SummonSlotRole) : SummonEffect

    /** Released in the centre without ever aiming: the menu (Settings, Search, leave Summon). */
    data object MenuRequested : SummonEffect

    /** A press released before the ring armed. */
    data class Tapped(val point: RingPoint) : SummonEffect

    /** The finger moved before the ring armed: a swipe, for Home's own gestures. */
    data class PassedThrough(val from: RingPoint, val to: RingPoint) : SummonEffect

    data object Cancelled : SummonEffect
}

data class SummonStep(
    val phase: SummonPhase,
    val effects: List<SummonEffect> = emptyList(),
)

/**
 * The Summon gesture as a pure reducer: press, hold to arm, aim by direction, rest to preview or open, push through to
 * go deeper, release to commit. Time comes in with the events, so it runs the same in tests as on a device.
 * [roles] gives each level's slots by folder path (the top level for an empty path).
 */
object SummonGesture {
    fun reduce(
        phase: SummonPhase,
        event: SummonEvent,
        config: SummonGestureConfig,
        roles: (List<Int>) -> List<SummonSlotRole>,
    ): SummonStep {
        val levels = SummonLevels(config, roles)
        return when (event) {
            is SummonEvent.Down -> SummonStep(SummonPhase.Pending(event.point, event.atMillis))
            SummonEvent.Cancel -> cancel(phase)
            is SummonEvent.Tick -> tick(phase, event.atMillis, levels)
            is SummonEvent.Move -> move(phase, event, levels)
            is SummonEvent.Up -> SummonStep(SummonPhase.Idle, listOfNotNull(SummonRelease.effectOf(phase, roles)))
        }
    }

    /** When the UI should send the next [SummonEvent.Tick], or null when nothing is waiting on time. */
    fun nextDeadline(
        phase: SummonPhase,
        config: SummonGestureConfig,
        roles: (List<Int>) -> List<SummonSlotRole>,
    ): Long? {
        val t = config.timings
        return when (phase) {
            SummonPhase.Idle -> null
            is SummonPhase.Pending -> phase.downAtMillis + t.armMillis
            is SummonPhase.Open -> {
                val role = SummonLevels(config, roles).roleOf(phase)
                val awaitingPreview = role == SummonSlotRole.PREVIEW && !phase.previewing
                listOfNotNull(
                    (phase.aimSinceMillis + t.previewMillis).takeIf { awaitingPreview },
                    (phase.aimSinceMillis + t.acceptMillis).takeIf { role == SummonSlotRole.FOLDER },
                    phase.inCentreSinceMillis?.plus(t.acceptMillis)?.takeIf { phase.path.isNotEmpty() },
                    phase.candidateSinceMillis?.plus(t.previewMillis),
                ).minOrNull()
            }
        }
    }

    /**
     * The directions a level's slots sit on. The top level spreads its slots evenly clockwise from the top. A folder
     * keeps the top level's grid of positions (so its items land where top-level items do), fills it clockwise from the
     * top and leaves empty the position of the folder it was opened from, so the thumb arrives on nothing.
     */
    fun levelAngles(
        path: List<Int>,
        roles: (List<Int>) -> List<SummonSlotRole>,
    ): List<Float> {
        val rootCount = roles(emptyList()).size
        if (path.isEmpty()) return SummonRingGeometry.baseAngles(rootCount)
        val grid = SummonRingGeometry.baseAngles(maxOf(rootCount, 1))
        val entry = levelAngles(path.dropLast(1), roles).getOrNull(path.last())
        val free = grid.filter { entry == null || abs(wrap(it - entry)) > SAME_DIRECTION_DEGREES }
        return free.take(roles(path).size)
    }

    private fun cancel(phase: SummonPhase): SummonStep =
        if (phase == SummonPhase.Idle) {
            SummonStep(
                phase,
            )
        } else {
            SummonStep(SummonPhase.Idle, listOf(SummonEffect.Cancelled))
        }

    private fun tick(
        phase: SummonPhase,
        now: Long,
        levels: SummonLevels,
    ): SummonStep =
        when (phase) {
            SummonPhase.Idle -> SummonStep(phase)
            is SummonPhase.Pending ->
                if (now - phase.downAtMillis >= levels.config.timings.armMillis) {
                    SummonStep(levels.open(phase.origin, emptyList()), listOf(SummonEffect.Armed))
                } else {
                    SummonStep(phase)
                }
            is SummonPhase.Open -> tickOpen(phase, now, levels)
        }

    private fun tickOpen(
        phase: SummonPhase.Open,
        now: Long,
        levels: SummonLevels,
    ): SummonStep {
        val t = levels.config.timings
        val role = levels.roleOf(phase)
        val restedInCentre = phase.inCentreSinceMillis?.let { now - it >= t.acceptMillis } == true
        val restedOnAim = now - phase.aimSinceMillis
        val aim = phase.aim
        val candidateSince = phase.candidateSinceMillis
        return when {
            candidateSince != null && now - candidateSince >= t.previewMillis -> SummonLock.moveTo(phase, now, levels)
            phase.path.isNotEmpty() && restedInCentre -> levels.back(phase, now)
            role == SummonSlotRole.FOLDER && restedOnAim >= t.acceptMillis -> levels.enter(phase, now)
            aim != null && role == SummonSlotRole.PREVIEW && !phase.previewing && restedOnAim >= t.previewMillis ->
                SummonStep(phase.copy(previewing = true), listOf(SummonEffect.Previewed(phase.path, aim)))
            else -> SummonStep(phase)
        }
    }

    private fun move(
        phase: SummonPhase,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep =
        when (phase) {
            SummonPhase.Idle -> SummonStep(phase)
            is SummonPhase.Pending ->
                if (phase.origin.distanceTo(event.point) > levels.config.touchSlop) {
                    SummonStep(SummonPhase.Idle, listOf(SummonEffect.PassedThrough(phase.origin, event.point)))
                } else {
                    SummonStep(phase)
                }
            is SummonPhase.Open -> SummonAim.move(phase, event, levels)
        }
}

private const val SAME_DIRECTION_DEGREES = 0.5f
