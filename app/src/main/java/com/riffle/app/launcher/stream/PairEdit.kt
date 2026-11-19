package com.riffle.app.launcher.stream

/** What the owner can do to the pairs of the Pairs stack besides opening and removing one (see [StreamCallbacks]). */
internal sealed interface PairEdit {
    /** The "New pair" card: put a draft card in front. */
    data object NewPair : PairEdit

    /** A half of the draft: open the app picker for it. */
    data class ChooseDraftHalf(val secondHalf: Boolean) : PairEdit

    /** Cancel: the draft is dropped. */
    data object DiscardDraft : PairEdit

    data class SwapSides(val pairId: String) : PairEdit

    /** Change the first or the second app of a saved pair: the picker opens for it. */
    data class ChangeApp(val pairId: String, val secondHalf: Boolean) : PairEdit
}
