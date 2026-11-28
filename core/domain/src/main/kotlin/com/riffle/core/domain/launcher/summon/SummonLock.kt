package com.riffle.core.domain.launcher.summon

/**
 * [SummonKeepGesture.REST_TO_LOCK]: once a card slot has rested long enough to preview, the aim holds while the finger
 * travels, so it can cross the centre and other slots on its way to the readout. Resting somewhere else for the
 * preview time moves the lock: to another slot, or off entirely in the centre.
 */
internal object SummonLock {
    fun isLocked(
        phase: SummonPhase.Open,
        levels: SummonLevels,
    ): Boolean =
        levels.config.keep == SummonKeepGesture.REST_TO_LOCK &&
            phase.aim != null &&
            phase.previewing &&
            levels.roleOf(phase) == SummonSlotRole.PREVIEW

    fun move(
        phase: SummonPhase.Open,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep {
        val aim = phase.aim
        return when {
            aim == null -> SummonStep(phase)
            event.overReadout ->
                SummonStep(
                    phase.copy(finger = event.point, overReadout = true, candidate = null, candidateSinceMillis = null),
                    listOfNotNull(SummonEffect.ReachedReadout(phase.path, aim).takeIf { !phase.overReadout }),
                )
            else -> travel(phase, aim, event, levels)
        }
    }

    /** Away from the readout: note which slot (or the centre) the finger rests on, without re-aiming. */
    private fun travel(
        phase: SummonPhase.Open,
        aim: Int,
        event: SummonEvent.Move,
        levels: SummonLevels,
    ): SummonStep {
        val inCentre = phase.origin.distanceTo(event.point) < levels.config.deadZone
        val under =
            if (inCentre) {
                null
            } else {
                phase.layout.pick(event.point, phase.candidate ?: aim, levels.config.hysteresisDegrees)
            }
        val resting = phase.candidateSinceMillis != null
        val candidateSince =
            when {
                under == aim -> null
                resting && under == phase.candidate -> phase.candidateSinceMillis
                else -> event.atMillis
            }
        return SummonStep(
            phase.copy(
                finger = event.point,
                overReadout = false,
                candidate = under.takeIf { candidateSince != null },
                candidateSinceMillis = candidateSince,
            ),
        )
    }

    /** The finger has rested on [SummonPhase.Open.candidate] for the preview time: the lock moves there. */
    fun moveTo(
        phase: SummonPhase.Open,
        now: Long,
        levels: SummonLevels,
    ): SummonStep {
        val target = phase.candidate?.takeIf { it in levels.roles(phase.path).indices }
        val moved =
            phase.copy(
                aim = target,
                aimSinceMillis = now,
                previewing = false,
                live = false,
                overReadout = false,
                candidate = null,
                candidateSinceMillis = null,
                inCentreSinceMillis = if (target == null) now else null,
            )
        return SummonStep(moved, listOfNotNull(target?.let { SummonEffect.Aimed(phase.path, it) }))
    }
}
