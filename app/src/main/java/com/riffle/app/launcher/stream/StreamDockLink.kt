package com.riffle.app.launcher.stream

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.core.domain.launcher.dock.DockDynamicSide
import com.riffle.core.domain.launcher.dock.DockDynamicSideEntry
import com.riffle.core.domain.launcher.stream.CardAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.OpenCardEvent
import com.riffle.core.domain.launcher.stream.OpenCardReducer
import com.riffle.core.domain.launcher.stream.OpenCardState
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamPageRef
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView

/** A page option the Dock asks the Stream to apply (a long-press of a tile): the Stream draws the Undo it offers. */
internal sealed interface StreamPageOption {
    data class ShowAs(val section: StreamSection, val view: StreamView) : StreamPageOption

    data class Move(val section: StreamSection, val delta: Int) : StreamPageOption

    data class Hide(val section: StreamSection) : StreamPageOption

    data class Show(val section: StreamSection) : StreamPageOption
}

/** A request to apply [option]; [id] tells repeated requests for the same option apart. */
internal class StreamPageOptionRequest(val id: Long, val option: StreamPageOption)

/**
 * The card that is open (ADR 0003, Amendment 1, "Stream: browse, then open"): which one ([state], moved only by the
 * pure [OpenCardReducer]) and what the Dock needs to draw its actions row ([shown]: the open card's content and the
 * way to act on it). The Stream's stacks write both; the Dock reads [shown] and closes the card by a press
 * ([closeByPress]). Transient, never stored.
 */
@Stable
internal class OpenCardHolder {
    var state: OpenCardState by mutableStateOf(OpenCardState())
        private set

    /** What the Dock's actions row draws, or null while no card is open (or its actions sit on the card). */
    var shown: OpenCardActions? by mutableStateOf(null)
        private set

    fun dispatch(event: OpenCardEvent) {
        val next = OpenCardReducer.reduce(state, event)
        if (next != state) state = next
    }

    /** A press of the Dock while a card is open closes it, and the Stream stays; false when none is open. */
    fun closeByPress(): Boolean {
        if (!OpenCardReducer.takesPressOrBack(state)) return false
        dispatch(OpenCardEvent.CloseByPress)
        return true
    }

    fun publish(actions: OpenCardActions?) {
        if (shown != actions) shown = actions
    }
}

/**
 * What the Dock's actions row needs from the open card: the content, its controls, how to act and how to show or hide
 * the player's queue. Equal only to itself: a new value is published whenever the card's content or queue changes, so
 * the row redraws with it.
 */
@Stable
internal class OpenCardActions(
    val content: StreamContent,
    val actions: List<CardAction>,
    val callbacks: StreamCallbacks,
    val onToggleQueue: (Boolean) -> Unit,
)

/**
 * Whether the open card's actions sit in the Dock's row (the setting, where the Dock can hold one) rather than on the
 * card. False (on the card) wherever there is no Dock to hold them: the Stream alone, in tests and previews.
 */
internal val LocalActionsInDock = staticCompositionLocalOf { false }

/** The open card of the Stream composed here: a stack reads and writes it; the surface publishes it to the Dock. */
internal val LocalOpenCards = staticCompositionLocalOf { OpenCardHolder() }

/**
 * What the Stream and the Dock tell each other while the Dock is in the contextual mode (ADR 0003, Amendment 3): the
 * Stream publishes its pages in the Dock's order and which one is open (so the Dock can draw a tile for each and
 * ring the
 * open one), and the Dock asks for a page option to be applied. Landing on a page goes through
 * [StreamFocusRequests], as
 * it always has. Transient, never stored.
 */
@Stable
internal class StreamDockLink {
    /** The Stream's pages in the Dock's order; empty while the Stream is not composed. */
    var pages: List<StreamPageRef> by mutableStateOf(emptyList())
        private set

    /** The key of the page in front (the pager's, which moves as a swipe passes halfway); null while there is none. */
    var currentKey: String? by mutableStateOf(null)
        private set

    /** The page option the Dock asked for and the Stream has not applied yet. */
    var pendingOption: StreamPageOptionRequest? by mutableStateOf(null)
        private set

    /** The card open on the page in front, which the Dock's actions row and its press read. */
    val openCards: OpenCardHolder = OpenCardHolder()

    private var lastOptionId = 0L

    fun publishPages(pages: List<StreamPageRef>) {
        if (this.pages != pages) this.pages = pages
    }

    fun publishCurrent(key: String?) {
        if (currentKey != key) currentKey = key
    }

    /** The Stream left composition: nothing is open and no page is listed. */
    fun clear() {
        pages = emptyList()
        currentKey = null
        pendingOption = null
        openCards.dispatch(OpenCardEvent.StreamLeft)
        openCards.publish(null)
    }

    fun requestOption(option: StreamPageOption) {
        pendingOption = StreamPageOptionRequest(++lastOptionId, option)
    }

    fun optionHandled(id: Long) {
        if (pendingOption?.id == id) pendingOption = null
    }
}

/**
 * The Dock's live entries as the Stream's results give them (the Notifications and Now sections, so Hide rules and
 * content levels have already applied): the one place both the Dock and the Stream's page order read them from, so the
 * pager follows the Dock.
 */
internal fun Map<StreamSection, StreamSectionResult>.dockLiveEntries(
    liveAppSlots: Int,
    /** The pinned apps' keys (`package:profile`): a pinned app is drawn once, as its pinned icon, not live. */
    pinnedGroupKeys: Set<String> = emptySet(),
): List<DockDynamicSideEntry> {
    val groups = (this[StreamSection.NOTIFICATIONS] as? StreamSectionResult.Ready)?.groups.orEmpty()
    val media =
        (this[StreamSection.NOW] as? StreamSectionResult.Ready)?.items?.filterIsInstance<MediaContent>()?.firstOrNull()
    return DockDynamicSide.entries(groups, media, liveAppSlots, pinnedGroupKeys = pinnedGroupKeys)
}
