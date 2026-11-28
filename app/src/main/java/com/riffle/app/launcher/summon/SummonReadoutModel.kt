package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.summon.SummonKeepGesture
import com.riffle.core.domain.launcher.summon.SummonPhase
import com.riffle.core.domain.launcher.summon.SummonSlotRole
import kotlin.math.pow

private const val READOUT_NOTIFICATIONS = 3
private const val HIGH_RING_FRACTION = 0.45f
private const val GLOW_REACH_DP = 380f
private const val GLOW_FALLOFF = 1.3

/** What the readout says releasing (or pushing through) will do. */
internal enum class SummonReadoutHint {
    RELEASE_TO_OPEN,
    OPEN_OR_ACTIONS,
    OPEN_OR_KEEP,
    RELEASE_TO_KEEP,
    REST_TO_KEEP,
    SLIDE_TO_KEEP,
    OPEN_FOLDER,
    SHOW_FAVOURITES,
}

internal enum class SummonReadoutPlacement { TOP, BOTTOM }

/** The card that says what the thumb is on: the entry, its newest notifications, and what letting go does. */
internal data class SummonReadout(
    val entry: SummonEntry,
    val notifications: List<LauncherNotification>,
    val notificationCount: Int,
    val hint: SummonReadoutHint,
)

internal fun summonReadout(
    phase: SummonPhase.Open,
    model: SummonRingModel,
    keep: SummonKeepGesture = SummonKeepGesture.PUSH_THROUGH,
): SummonReadout? {
    val favourite = phase.dockAim?.let { model.favourites.getOrNull(it) }
    val entry = favourite ?: phase.aim?.let { model.entry(phase.path, it) } ?: return null
    val group = (entry as? SummonEntry.App)?.notifications
    val role = if (favourite != null) SummonSlotRole.APP else entry.role
    return SummonReadout(
        entry = entry,
        notifications = group?.notifications.orEmpty().take(READOUT_NOTIFICATIONS),
        notificationCount = group?.count ?: 0,
        hint = readoutHint(role, phase, keep, hasActions = (entry as? SummonEntry.App)?.actions.orEmpty().isNotEmpty()),
    )
}

private fun readoutHint(
    role: SummonSlotRole,
    phase: SummonPhase.Open,
    keep: SummonKeepGesture,
    hasActions: Boolean,
): SummonReadoutHint =
    when {
        role == SummonSlotRole.APP && hasActions -> SummonReadoutHint.OPEN_OR_ACTIONS
        role == SummonSlotRole.FOLDER -> SummonReadoutHint.OPEN_FOLDER
        role == SummonSlotRole.FAVOURITES -> SummonReadoutHint.SHOW_FAVOURITES
        role != SummonSlotRole.PREVIEW -> SummonReadoutHint.RELEASE_TO_OPEN
        phase.live || phase.overReadout -> SummonReadoutHint.RELEASE_TO_KEEP
        keep == SummonKeepGesture.PUSH_THROUGH -> SummonReadoutHint.OPEN_OR_KEEP
        phase.previewing -> SummonReadoutHint.SLIDE_TO_KEEP
        else -> SummonReadoutHint.REST_TO_KEEP
    }

/** At the top, out of the thumb's way; at the bottom when the ring is held high enough to cover the top. */
internal fun summonReadoutPlacement(
    ringCentreY: Float,
    height: Float,
): SummonReadoutPlacement =
    if (ringCentreY < height * HIGH_RING_FRACTION) SummonReadoutPlacement.BOTTOM else SummonReadoutPlacement.TOP

/** How strongly something in the readout glows, 0..1, by its distance from the lit stretch of rim beside the icon. */
internal fun summonReadoutGlow(distanceDp: Float): Float =
    (1f - distanceDp / GLOW_REACH_DP).coerceIn(0f, 1f).toDouble().pow(GLOW_FALLOFF).toFloat()
