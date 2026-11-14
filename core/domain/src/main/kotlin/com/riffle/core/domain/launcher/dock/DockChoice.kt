package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import com.riffle.core.domain.launcher.stream.StreamSection
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Hold to choose (ADR 0003, Amendment 3, One gesture, third depth): a press held past the commit threshold, or slid
 * sideways while pressed, stops being a toggle and becomes a selection that follows the finger. Releasing opens the
 * selected item's page.
 *
 * Everything that decides anything is here and pure, so it is tested without a screen: when choosing starts
 * ([DockChoiceStart]), what can be chosen and which item is nearest the finger ([DockChoice.pageFor],
 * [DockChoice.nearestSelectable]), when holding near an end scrolls the row ([DockChoice.edgeDirection]), when the
 * finger has gone far enough away to cancel ([DockChoice.isCancelling]), when the selection earns a haptic tick
 * ([DockChoice.shouldTick]) and what a release does in every case ([DockChoice.outcome]).
 */
object DockChoice {
    /**
     * The Stream page the Dock item keyed [entryKey] opens when chosen, or null when it has none and so cannot be
     * chosen. THE decision of what is selectable: live apps, now playing and the contextual tiles have a page; a pinned
     * app has none yet (its shortcut stack is a later slice, which only has to answer here to make it selectable).
     */
    fun pageFor(entryKey: String): StreamFocusTarget? =
        when {
            entryKey == DockDynamicSideEntry.NOW_PLAYING_KEY -> StreamFocusTarget.Now
            entryKey.startsWith(DockDynamicSideEntry.LIVE_APP_KEY_PREFIX) ->
                StreamFocusTarget.AppStack(entryKey.removePrefix(DockDynamicSideEntry.LIVE_APP_KEY_PREFIX))
            entryKey.startsWith(DockDynamicSideEntry.CONTEXT_TILE_KEY_PREFIX) ->
                StreamSection.fromStoredId(entryKey.removePrefix(DockDynamicSideEntry.CONTEXT_TILE_KEY_PREFIX))
                    ?.let(StreamFocusTarget::Section)
            else -> null
        }

    /** Whether the item keyed [entryKey] can be chosen: it has a page ([pageFor]). */
    fun isSelectable(entryKey: String): Boolean = pageFor(entryKey) != null

    /**
     * The key of the selectable item nearest [positionPx] along the Dock's run (the finger's place, in the same pixels
     * as the items' extents), or null when nothing is selectable. Items that cannot be chosen are skipped, so a finger
     * over a pinned app snaps to the nearest item that can. Nearness is to an item's centre; a tie goes to the earlier
     * item.
     */
    fun nearestSelectable(
        positionPx: Float,
        items: List<DockChoiceItem>,
    ): String? =
        items
            .filter { item -> isSelectable(item.key) }
            .minByOrNull { item -> abs((item.startPx + item.endPx) / 2f - positionPx) }
            ?.key

    /**
     * Which way holding the finger at [fingerPx] scrolls the row whose viewport spans [viewportStartPx] to
     * [viewportEndPx]: -1 toward its start, +1 toward its end, 0 not at all. The end zones are
     * [DockMotionConstants.CHOOSE_EDGE_ZONE_FRACTION] of the viewport each, and a finger beyond an end counts.
     */
    fun edgeDirection(
        fingerPx: Float,
        viewportStartPx: Float,
        viewportEndPx: Float,
    ): Int {
        val zone = (viewportEndPx - viewportStartPx) * DockMotionConstants.CHOOSE_EDGE_ZONE_FRACTION
        return when {
            viewportEndPx <= viewportStartPx -> 0
            fingerPx < viewportStartPx + zone -> -1
            fingerPx > viewportEndPx - zone -> 1
            else -> 0
        }
    }

    /** How far the row scrolls in [seconds] while the finger holds in an end zone ([direction]), in dp. */
    fun edgeScrollDp(
        direction: Int,
        seconds: Float,
    ): Float =
        direction.coerceIn(-1, 1) * DockMotionConstants.CHOOSE_EDGE_SCROLL_DP_PER_SECOND *
            seconds.coerceIn(0f, DockMotionConstants.MAX_FRAME_SECONDS)

    /**
     * Whether a finger [awayDp] beyond the Dock's inner edge (the edge away from the screen's, positive further out,
     * negative still over the Dock) has gone far enough to cancel: more than
     * [DockMotionConstants.CHOOSE_CANCEL_DOCK_HEIGHTS] times the Dock's own thickness ([dockThicknessDp]) past it.
     * Lifting there changes nothing; coming back over the Dock chooses again.
     */
    fun isCancelling(
        awayDp: Float,
        dockThicknessDp: Float,
    ): Boolean = dockThicknessDp > 0f && awayDp > dockThicknessDp * DockMotionConstants.CHOOSE_CANCEL_DOCK_HEIGHTS

    /** Whether the selection moving from [previous] to [next] earns a haptic tick: only onto another item. */
    fun shouldTick(
        previous: String?,
        next: String?,
    ): Boolean = next != null && next != previous

    /**
     * What a release does. [fromContextual]: the Stream was open under the finger. [committed]: the press went past the
     * threshold. [choosing]: it had turned into a selection, with [selectedKey] the item selected (null when none is
     * selectable) and [cancelling] the finger far enough away to cancel. [startedOnEntryKey] is the item the finger
     * landed on, which a plain press opens from Home. [startedNearKey] is the selectable item nearest where the finger
     * landed (what [nearestSelectable] says of the landing): with the Stream open, a selection that ends on it is the
     * press that never left where it started (a thumb drifts along the run while it presses), and closes like a plain
     * one; only a selection that lands on another item switches pages.
     */
    @Suppress("LongParameterList")
    fun outcome(
        fromContextual: Boolean,
        committed: Boolean,
        choosing: Boolean,
        selectedKey: String?,
        cancelling: Boolean,
        startedOnEntryKey: String?,
        startedNearKey: String? = null,
    ): DockPressOutcome =
        when {
            // A plain press: past the threshold it toggles the modes, short of it nothing changes.
            !choosing ->
                when {
                    !committed -> DockPressOutcome.Revert
                    fromContextual -> DockPressOutcome.Close
                    else -> DockPressOutcome.OpenPage(startedOnEntryKey)
                }
            // Once choosing, lifting always commits to the selection, unless the finger was taken well away.
            cancelling -> DockPressOutcome.Revert
            selectedKey != null ->
                when {
                    !fromContextual -> DockPressOutcome.OpenPage(selectedKey)
                    selectedKey == startedNearKey -> DockPressOutcome.Close
                    else -> DockPressOutcome.SwitchPage(selectedKey)
                }
            // Nothing selectable at all: Home falls back to the page a plain press opens; the open Stream has none to
            // fall back to (a plain press closes it, which is not a choice), so nothing changes.
            fromContextual -> DockPressOutcome.Revert
            else -> DockPressOutcome.OpenPage(startedOnEntryKey)
        }
}

/** A Dock item along the run: its [key] and where it lies, [startPx] up to [endPx], in the finger's pixels. */
data class DockChoiceItem(
    val key: String,
    val startPx: Float,
    val endPx: Float,
)

/** What the release of a Dock press does to the Dock. */
sealed interface DockPressOutcome {
    /** Nothing changes; the Dock bounces back. */
    data object Revert : DockPressOutcome

    /** From Home: the Stream fans out on [entryKey]'s page, or on the last page when it is null or has none. */
    data class OpenPage(val entryKey: String?) : DockPressOutcome

    /** From the contextual mode: the Stream stays out and switches to [entryKey]'s page. */
    data class SwitchPage(val entryKey: String) : DockPressOutcome

    /** From the contextual mode: everything goes back into the Dock and Home returns. */
    data object Close : DockPressOutcome
}

/**
 * When a press turns into choosing. Fed every sample of the finger while the Dock is pressed ([next]) and, when the
 * finger is still, the moment [deadlineMillis] names: pressed past [DockPress.COMMIT_DP], choosing starts once the
 * finger has rested for the hold time, or at once when it slides more than [DockMotionConstants.CHOOSE_SLIDE_DP]
 * along the run. Never before the threshold, and drawing the press back above it before choosing starts begins again.
 * Once started it stays started: the press ends at the lift.
 *
 * Holding only chooses where the press started from Home. With the Stream already open ([next]'s `holdStarts` false) a
 * finger that rests, however long, is a person taking their time over the press that closes it: only sliding along the
 * run chooses there, so a press held for half a second still returns Home.
 */
data class DockChoiceStart(
    val choosing: Boolean = false,
    /** When the finger last came to rest past the threshold, or null while it has not. */
    val restingSinceMillis: Long? = null,
    private val restTravelDp: Float = 0f,
    private val restAlongDp: Float = 0f,
) {
    /**
     * The state after the finger is at [travelDp] pressed down and [alongDp] along the run from where it landed
     * (signed; only how far counts) at [timeMillis]. [holdMillis] is the hold time; [holdStarts] says whether resting
     * for it chooses at all (from Home) or only a slide does (the open Stream).
     */
    fun next(
        timeMillis: Long,
        travelDp: Float,
        alongDp: Float,
        holdMillis: Long = DockMotionConstants.DEFAULT_HOLD_MILLIS.toLong(),
        holdStarts: Boolean = true,
    ): DockChoiceStart =
        when {
            choosing -> this
            !DockPress.commits(travelDp) -> DockChoiceStart()
            abs(alongDp) > DockMotionConstants.CHOOSE_SLIDE_DP -> copy(choosing = true)
            !holdStarts -> this
            restingSinceMillis == null || movedFromRest(travelDp, alongDp) ->
                copy(restingSinceMillis = timeMillis, restTravelDp = travelDp, restAlongDp = alongDp)
            timeMillis - restingSinceMillis >= holdMillis -> copy(choosing = true)
            else -> this
        }

    /** When choosing will start if the finger stays where it is, or null when it cannot (not resting, or started). */
    fun deadlineMillis(holdMillis: Long = DockMotionConstants.DEFAULT_HOLD_MILLIS.toLong()): Long? =
        restingSinceMillis?.takeIf { !choosing }?.plus(holdMillis)

    private fun movedFromRest(
        travelDp: Float,
        alongDp: Float,
    ): Boolean = hypot(travelDp - restTravelDp, alongDp - restAlongDp) > DockMotionConstants.CHOOSE_STILL_DP
}
