package com.riffle.core.domain.launcher.summon

/** What lifting the finger commits to. */
internal object SummonRelease {
    fun effectOf(
        phase: SummonPhase,
        roles: (List<Int>) -> List<SummonSlotRole>,
    ): SummonEffect? =
        when (phase) {
            SummonPhase.Idle -> null
            is SummonPhase.Pending -> SummonEffect.Tapped(phase.origin)
            is SummonPhase.Open -> fromOpenRing(phase, roles)
        }

    private fun fromOpenRing(
        phase: SummonPhase.Open,
        roles: (List<Int>) -> List<SummonSlotRole>,
    ): SummonEffect {
        val aim = phase.aim
        val dockAim = phase.dockAim
        return when {
            phase.favouritesOpen && dockAim != null -> SummonEffect.LaunchFavourite(dockAim)
            aim != null -> onSlot(phase, aim, roles(phase.path)[aim])
            // A tap-and-hold released where it started: the menu, which is also how Settings is reached.
            !phase.leftCentre && phase.path.isEmpty() -> SummonEffect.MenuRequested
            else -> SummonEffect.Cancelled
        }
    }

    private fun onSlot(
        phase: SummonPhase.Open,
        aim: Int,
        role: SummonSlotRole,
    ): SummonEffect =
        when (role) {
            SummonSlotRole.APP -> SummonEffect.Launch(phase.path, aim)
            SummonSlotRole.PREVIEW ->
                if (phase.live) SummonEffect.KeepCard(phase.path, aim) else SummonEffect.Launch(phase.path, aim)
            SummonSlotRole.FOLDER, SummonSlotRole.FAVOURITES -> SummonEffect.ReleasedOn(phase.path, aim, role)
        }
}
