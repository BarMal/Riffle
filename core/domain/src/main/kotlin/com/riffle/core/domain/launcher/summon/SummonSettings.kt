package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.LauncherItemId

const val MIN_SUMMON_SLOT_COUNT: Int = 3
const val MAX_SUMMON_SLOT_COUNT: Int = 8
const val DEFAULT_SUMMON_SLOT_COUNT: Int = MAX_SUMMON_SLOT_COUNT

val SUMMON_ARM_MILLIS_RANGE: IntRange = 100..600
val SUMMON_PREVIEW_MILLIS_RANGE: IntRange = 150..900
val SUMMON_ACCEPT_MILLIS_RANGE: IntRange = 200..1000
val SUMMON_SLIDE_MILLIS_RANGE: IntRange = 200..1200

/**
 * One position around the Summon ring. Folders and Favourites are references to what Home and the Dock already hold
 * (a model has one home, AGENTS.md): the ring stores where they sit, never their contents.
 */
sealed interface SummonSlot {
    data class App(val identity: AppIdentity) : SummonSlot

    /** A folder placed on Home, by its item id; a folder that no longer exists is skipped when the ring draws. */
    data class Folder(val folderId: LauncherItemId) : SummonSlot

    data class Glance(val kind: GlanceWidgetKind) : SummonSlot

    /** The Dock's pinned apps: the only way the Dock appears while Summon is on. */
    data object Favourites : SummonSlot
}

/** What sits above the ring at rest, where Home's clock would be. */
sealed interface SummonHeader {
    data object None : SummonHeader

    data object Clock : SummonHeader

    data object ClockAndDate : SummonHeader

    data class Glance(val kind: GlanceWidgetKind) : SummonHeader

    /** A widget the user placed on Home, drawn again here; missing widgets fall back to the clock. */
    data class HomeWidget(val itemId: LauncherItemId) : SummonHeader

    companion object {
        val DEFAULT: SummonHeader = ClockAndDate
    }
}

/**
 * [armMillis]: how long a press is held before the ring appears (and the arm haptic plays). [previewMillis]: how long
 * the thumb rests on a slot before its card shows. [acceptMillis]: how long it rests on a folder before the folder
 * opens, and in the centre before going back up. [slideMillis]: how long items take to slide onto the ring.
 */
data class SummonTimings(
    val armMillis: Int = 200,
    val previewMillis: Int = 350,
    val acceptMillis: Int = 450,
    val slideMillis: Int = 600,
) {
    fun clamped(): SummonTimings =
        SummonTimings(
            armMillis = armMillis.coerceIn(SUMMON_ARM_MILLIS_RANGE),
            previewMillis = previewMillis.coerceIn(SUMMON_PREVIEW_MILLIS_RANGE),
            acceptMillis = acceptMillis.coerceIn(SUMMON_ACCEPT_MILLIS_RANGE),
            slideMillis = slideMillis.coerceIn(SUMMON_SLIDE_MILLIS_RANGE),
        )
}

const val DEFAULT_SUMMON_ICON_SIZE_DP = 44
val SUMMON_ICON_SIZE_DP_RANGE = 32..56

/**
 * The experimental Summon Home (switched on by `FeatureSettings.summon`). [slots] is the user's order, clockwise from
 * the top; the ring shows the first [slotCount] of them. Slots past the count are kept so lowering and raising the
 * count is lossless.
 */
data class SummonSettings(
    val slotCount: Int = DEFAULT_SUMMON_SLOT_COUNT,
    val slots: List<SummonSlot> = emptyList(),
    val header: SummonHeader = SummonHeader.DEFAULT,
    val timings: SummonTimings = SummonTimings(),
    /** The short lines that say what letting go will do (on the ring and the readout). */
    val showHints: Boolean = true,
    /** How big each app icon on the ring and the Favourites row draws; the touch target stays the slot's. */
    val iconSizeDp: Int = DEFAULT_SUMMON_ICON_SIZE_DP,
) {
    val ringSlots: List<SummonSlot> get() = slots.take(slotCount)

    /** These settings with a first ring built from [apps] when the user has none yet; otherwise unchanged. */
    fun seeded(apps: List<AppIdentity>): SummonSettings =
        if (slots.isNotEmpty()) this else copy(slots = defaultSummonSlots(apps, slotCount))

    fun withIconSize(sizeDp: Int): SummonSettings = copy(iconSizeDp = sizeDp.coerceIn(SUMMON_ICON_SIZE_DP_RANGE))

    fun withSlotCount(count: Int): SummonSettings =
        copy(slotCount = count.coerceIn(MIN_SUMMON_SLOT_COUNT, MAX_SUMMON_SLOT_COUNT))

    fun moved(
        from: Int,
        to: Int,
    ): SummonSettings {
        if (from !in slots.indices || to !in slots.indices || from == to) return this
        val reordered = slots.toMutableList().apply { add(to, removeAt(from)) }
        return copy(slots = reordered)
    }

    fun added(slot: SummonSlot): SummonSettings = if (slot in slots) this else copy(slots = slots + slot)

    fun removed(index: Int): SummonSettings =
        if (index !in slots.indices) this else copy(slots = slots.filterIndexed { i, _ -> i != index })

    /** [slot] at [index]; if it was already elsewhere on the ring the two positions swap. */
    fun replaced(
        index: Int,
        slot: SummonSlot,
    ): SummonSettings {
        if (index !in slots.indices) return this
        val existing = slots.indexOf(slot)
        val updated = slots.toMutableList()
        if (existing >= 0) updated[existing] = slots[index]
        updated[index] = slot
        return copy(slots = updated)
    }
}

/** A first ring: up to `count - 1` distinct apps, with Favourites straight down (half-way round, clockwise). */
fun defaultSummonSlots(
    apps: List<AppIdentity>,
    count: Int,
): List<SummonSlot> {
    val ring = count.coerceIn(MIN_SUMMON_SLOT_COUNT, MAX_SUMMON_SLOT_COUNT)
    val appSlots: List<SummonSlot> = apps.distinct().take(ring - 1).map(SummonSlot::App)
    val favouritesAt = (ring / 2).coerceAtMost(appSlots.size)
    return appSlots.toMutableList().apply { add(favouritesAt, SummonSlot.Favourites) }
}
