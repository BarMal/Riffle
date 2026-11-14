package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * The two postures the layout is a setting for (ADR 0003, Amendment 2): folded and unfolded each keep their own.
 * Folded is the phone layout (portrait or landscape); unfolded is every wide layout: a foldable opened flat, a tablet
 * and a desktop window. A tablet is the same kind of wide, flat screen, so it takes the Unfolded settings.
 */
enum class LayoutPosture {
    FOLDED,
    UNFOLDED,
}

/** The posture a device class's layout belongs to. */
fun HomeLayoutDeviceClass.layoutPosture(): LayoutPosture =
    when (this) {
        HomeLayoutDeviceClass.PHONE,
        HomeLayoutDeviceClass.PHONE_LANDSCAPE,
        -> LayoutPosture.FOLDED

        HomeLayoutDeviceClass.FOLDABLE,
        HomeLayoutDeviceClass.TABLET,
        HomeLayoutDeviceClass.DESKTOP,
        -> LayoutPosture.UNFOLDED
    }

/**
 * The device class Settings edits for [this] posture's Dock edge: the class being held when it belongs to the posture,
 * else the posture's own (the phone for folded, the foldable for unfolded). The edge itself is stored where it always
 * was, on each device class's one shared Dock (`DockModel.position`), so a choice made on the Dock page or here is the
 * same choice.
 */
fun LayoutPosture.dockDeviceClass(active: HomeLayoutDeviceClass): HomeLayoutDeviceClass =
    if (active.layoutPosture() == this) {
        active
    } else {
        when (this) {
            LayoutPosture.FOLDED -> HomeLayoutDeviceClass.PHONE
            LayoutPosture.UNFOLDED -> HomeLayoutDeviceClass.FOLDABLE
        }
    }

/**
 * The edge the Dock takes before the user chooses one (ADR 0003, Amendment 2): along the bottom folded; unfolded on
 * the right edge of a foldable, where the thumb rests. A tablet and a desktop keep the left edge they always had.
 */
val HomeLayoutDeviceClass.defaultDockEdge: DockPosition
    get() =
        when (this) {
            HomeLayoutDeviceClass.PHONE,
            HomeLayoutDeviceClass.PHONE_LANDSCAPE,
            -> DockPosition.BOTTOM

            HomeLayoutDeviceClass.FOLDABLE -> DockPosition.RIGHT

            HomeLayoutDeviceClass.TABLET,
            HomeLayoutDeviceClass.DESKTOP,
            -> DockPosition.LEFT
        }

/** Which way the app pages of Home (and of the paged Library) scroll. [storedId] is a stored contract. */
enum class PageScrollDirection(
    val storedId: String,
) {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical"),
    ;

    companion object {
        fun fromStoredId(id: String?): PageScrollDirection? = entries.firstOrNull { it.storedId == id }
    }
}

/**
 * Where the page indicator sits (ADR 0003, Amendment 2). [BESIDE_DOCK] is the Home handle: the search glyph and the
 * page dots as one pill just in from the Dock, in front of its middle. [OFF] draws no dots; the pill keeps its search
 * glyph, so search keeps its place. (The amendment's "in the Dock" placement has no room in the single Dock of
 * Amendment 3, and is not offered.) [storedId] is a stored contract.
 */
enum class PageIndicatorPlacement(
    val storedId: String,
) {
    BESIDE_DOCK("beside_dock"),
    OFF("off"),
    ;

    companion object {
        fun fromStoredId(id: String?): PageIndicatorPlacement? = entries.firstOrNull { it.storedId == id }
    }
}

/**
 * One posture's layout settings, apart from the Dock edge (which is the Dock's own). The page indicator always runs
 * the way the pages scroll, so a quick-scroll drag never moves pages at right angles to it.
 */
data class PostureLayoutSettings(
    val pageScroll: PageScrollDirection,
    val indicator: PageIndicatorPlacement = PageIndicatorPlacement.BESIDE_DOCK,
    /** Whether dragging along the dots scrolls quickly; off, the dots only show where you are. */
    val quickScroll: Boolean = true,
    /**
     * Whether apps show faintly through the Stream's cards (unfolded, where Home stays beside and beneath the stack;
     * ADR 0003, Amendment 2). Off, nothing shows behind them. Read only by the unfolded posture.
     */
    val appPeekThrough: Boolean = true,
) {
    /** Whether the dots are drawn at all. */
    val showsDots: Boolean get() = indicator != PageIndicatorPlacement.OFF

    /** Whether a drag along the dots scrolls the pages. */
    val dragsToScroll: Boolean get() = showsDots && quickScroll

    companion object {
        /** Folded: pages scroll sideways, the indicator above the Dock. */
        val FOLDED_DEFAULT = PostureLayoutSettings(pageScroll = PageScrollDirection.HORIZONTAL)

        /** Unfolded: pages scroll up and down, the indicator just in from the Dock in front of its middle. */
        val UNFOLDED_DEFAULT = PostureLayoutSettings(pageScroll = PageScrollDirection.VERTICAL)

        fun defaultFor(posture: LayoutPosture): PostureLayoutSettings =
            when (posture) {
                LayoutPosture.FOLDED -> FOLDED_DEFAULT
                LayoutPosture.UNFOLDED -> UNFOLDED_DEFAULT
            }
    }
}

/**
 * The layout settings of both postures, each independent. A device class reads its posture's
 * ([forDeviceClass]); an old blob, which holds none, decodes to the defaults.
 */
data class LayoutSettings(
    val folded: PostureLayoutSettings = PostureLayoutSettings.FOLDED_DEFAULT,
    val unfolded: PostureLayoutSettings = PostureLayoutSettings.UNFOLDED_DEFAULT,
) {
    fun forPosture(posture: LayoutPosture): PostureLayoutSettings =
        when (posture) {
            LayoutPosture.FOLDED -> folded
            LayoutPosture.UNFOLDED -> unfolded
        }

    fun forDeviceClass(deviceClass: HomeLayoutDeviceClass): PostureLayoutSettings =
        forPosture(deviceClass.layoutPosture())

    /** These settings with [posture]'s replaced by [settings]. */
    fun with(
        posture: LayoutPosture,
        settings: PostureLayoutSettings,
    ): LayoutSettings =
        when (posture) {
            LayoutPosture.FOLDED -> copy(folded = settings)
            LayoutPosture.UNFOLDED -> copy(unfolded = settings)
        }

    fun withPageScroll(
        posture: LayoutPosture,
        direction: PageScrollDirection,
    ): LayoutSettings = with(posture, forPosture(posture).copy(pageScroll = direction))

    fun withIndicator(
        posture: LayoutPosture,
        placement: PageIndicatorPlacement,
    ): LayoutSettings = with(posture, forPosture(posture).copy(indicator = placement))

    fun withQuickScroll(
        posture: LayoutPosture,
        enabled: Boolean,
    ): LayoutSettings = with(posture, forPosture(posture).copy(quickScroll = enabled))

    fun withAppPeekThrough(
        posture: LayoutPosture,
        enabled: Boolean,
    ): LayoutSettings = with(posture, forPosture(posture).copy(appPeekThrough = enabled))
}
