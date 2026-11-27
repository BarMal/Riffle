package com.riffle.core.domain.launcher.summon

/** The config and the slot roles together: what a level needs to lay itself out and to move between levels. */
internal class SummonLevels(
    val config: SummonGestureConfig,
    val roles: (List<Int>) -> List<SummonSlotRole>,
) {
    fun open(
        origin: RingPoint,
        path: List<Int>,
    ): SummonPhase.Open {
        val angles = SummonGesture.levelAngles(path, roles)
        val layout = SummonRingGeometry.layout(origin, angles, config.bounds, config.radius, config.clearance)
        return SummonPhase.Open(origin = origin, path = path, layout = layout, finger = origin)
    }

    fun roleOf(phase: SummonPhase.Open): SummonSlotRole? = phase.aim?.let { roles(phase.path).getOrNull(it) }

    /** Opens the aimed folder in place of the current level. */
    fun enter(
        phase: SummonPhase.Open,
        now: Long,
    ): SummonStep {
        val path = phase.aim?.let { phase.path + it } ?: return SummonStep(phase)
        return SummonStep(switchTo(phase, path, now), listOf(SummonEffect.OpenedFolder(path)))
    }

    fun back(
        phase: SummonPhase.Open,
        now: Long,
    ): SummonStep {
        val path = phase.path.dropLast(1)
        return SummonStep(switchTo(phase, path, now), listOf(SummonEffect.WentBack(path)))
    }

    /** [from] moved to the level at [path], keeping the finger where it is. */
    private fun switchTo(
        from: SummonPhase.Open,
        path: List<Int>,
        now: Long,
    ): SummonPhase.Open = open(from.origin, path).copy(finger = from.finger, leftCentre = true, aimSinceMillis = now)
}
