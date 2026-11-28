package com.riffle.core.domain.launcher.summon

/** How a move changes what the open ring aims at: the centre, the Dock (once Favourites is open) or a slot. */
internal object SummonAim {
    fun move(
        phase: SummonPhase.Open,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep {
        val dockIndex = event.dockIndex
        return when {
            SummonLock.isLocked(phase, levels) -> SummonLock.move(phase, event, levels)
            phase.origin.distanceTo(event.point) < levels.config.deadZone -> restInCentre(phase, event)
            phase.favouritesOpen && dockIndex != null -> aimDock(phase, event, dockIndex)
            else -> pushThrough(aimRing(phase, event, levels), event, levels)
        }
    }

    private fun restInCentre(
        phase: SummonPhase.Open,
        event: SummonEvent.Move,
    ): SummonStep =
        SummonStep(
            phase.copy(
                finger = event.point,
                aim = null,
                previewing = false,
                live = false,
                favouritesOpen = false,
                dockAim = null,
                inCentreSinceMillis = phase.inCentreSinceMillis ?: event.atMillis,
            ),
            listOfNotNull(SummonEffect.FavouritesHidden.takeIf { phase.favouritesOpen }),
        )

    private fun aimDock(
        phase: SummonPhase.Open,
        event: SummonEvent.Move,
        dockIndex: Int,
    ): SummonStep =
        SummonStep(
            phase.copy(
                finger = event.point,
                aim = null,
                dockAim = dockIndex,
                inCentreSinceMillis = null,
                leftCentre = true,
            ),
            listOfNotNull(SummonEffect.AimedDock(dockIndex).takeIf { dockIndex != phase.dockAim }),
        )

    /** The slot under the finger's direction, with the hysteresis of the slot already held. */
    private fun aimRing(
        phase: SummonPhase.Open,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep {
        val levelRoles = levels.roles(phase.path)
        val aim =
            phase.layout.pick(event.point, phase.aim, levels.config.hysteresisDegrees)?.takeIf {
                it in levelRoles.indices
            }
        val changed = aim != phase.aim
        val opensFavourites = aim != null && levelRoles[aim] == SummonSlotRole.FAVOURITES && !phase.favouritesOpen
        val aimed =
            phase.copy(
                finger = event.point,
                aim = aim,
                aimSinceMillis = if (changed) event.atMillis else phase.aimSinceMillis,
                previewing = phase.previewing && !changed,
                live = phase.live && !changed,
                favouritesOpen = phase.favouritesOpen || opensFavourites,
                dockAim = null,
                inCentreSinceMillis = null,
                leftCentre = true,
            )
        return SummonStep(
            aimed,
            listOfNotNull(
                aim?.takeIf { changed }?.let { SummonEffect.Aimed(phase.path, it) },
                SummonEffect.FavouritesShown.takeIf { opensFavourites },
            ),
        )
    }

    /** Past the ring on a folder opens it at once; past the ring on a card makes the card live. */
    private fun pushThrough(
        step: SummonStep,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep {
        val phase = step.phase as? SummonPhase.Open ?: return step
        val aim = phase.aim
        val pushed = phase.origin.distanceTo(event.point) > phase.layout.radius + levels.config.pushThrough
        return when {
            aim == null || !pushed -> step
            levels.roleOf(phase) == SummonSlotRole.FOLDER ->
                levels.enter(phase, event.atMillis).let { SummonStep(it.phase, step.effects + it.effects) }
            levels.roleOf(phase) == SummonSlotRole.PREVIEW &&
                !phase.live &&
                levels.config.keep == SummonKeepGesture.PUSH_THROUGH ->
                SummonStep(
                    phase.copy(live = true, previewing = true),
                    step.effects + SummonEffect.WentLive(phase.path, aim),
                )
            else -> step
        }
    }
}
