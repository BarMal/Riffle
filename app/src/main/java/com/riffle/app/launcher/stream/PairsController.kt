package com.riffle.app.launcher.stream

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.riffle.app.launcher.pairs.AppPairsRepository
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.dock.PairApp
import com.riffle.core.domain.launcher.dock.PairLaunch
import com.riffle.core.domain.launcher.dock.PairLauncher
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairDraft
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.StreamAction

/** A pair the owner removed, for the "Pair removed" message and its Undo; [serial] tells one removal from the next. */
internal class RemovedPair(
    val pair: AppPair,
    val index: Int,
    val serial: Long,
)

/** Which half of which pair the app picker is choosing for: a saved pair's, or the draft's ([pairId] null). */
internal data class PairPick(
    val pairId: String?,
    val secondHalf: Boolean,
    /** The app the other half already holds, which the picker leaves out. */
    val exclude: AppIdentity?,
)

/**
 * What the Pairs page does (ADR 0003, Amendment 7), apart from drawing: the pair being made, the app picker, a
 * removal that can be undone, opening a pair and the edits of the long-press menu. The pairs themselves are the
 * [repository]'s. Main thread, like the Stream's callbacks; nothing here persists the draft.
 */
@Stable
internal class PairsController(
    private val repository: AppPairsRepository?,
    private val launcher: PairLauncher?,
) {
    /** The pair being made; dropped when the page is left, the Stream closes or Cancel is pressed. */
    var draft: PairDraft? by mutableStateOf(null)
        private set

    /** The picker, while one is open. */
    var picker: PairPick? by mutableStateOf(null)
        private set

    /** The last removal, until its message is gone. */
    var removed: RemovedPair? by mutableStateOf(null)
        private set

    private var removals = 0L

    /** A tap on a pair: both apps side by side. On a successful start the pair is the one opened last. */
    fun open(content: AppPairContent) {
        val first = content.first
        val second = content.second
        val id = content.pairId
        if (first == null || second == null || id == null) return
        val started = launcher?.launch(PairLaunch(first.toPairApp(), second.toPairApp())) ?: false
        if (started) repository?.opened(id)
    }

    /** The one entry of the stack's actions on a pair: a tap opens it, a swipe or Dismiss removes it. */
    fun perform(
        content: AppPairContent,
        action: StreamAction,
    ) {
        when (action) {
            StreamAction.Open -> open(content)
            StreamAction.Dismiss -> remove(content)
            else -> Unit
        }
    }

    /** A swipe, the menu's Remove or the accessibility action: the pair goes, and Undo brings it back. */
    fun remove(content: AppPairContent) {
        val id = content.pairId ?: return
        val before = repository?.remove(id) ?: return
        val index = before.indexOfFirst { it.id == id }
        if (index >= 0) removed = RemovedPair(before[index], index, ++removals)
    }

    /** Undo of [remove]: the pair is back at the place it had. */
    fun restore(removal: RemovedPair) {
        repository?.restore(removal.pair, removal.index)
        if (removed === removal) removed = null
    }

    fun edit(edit: PairEdit) {
        when (edit) {
            PairEdit.NewPair -> draft = PairDraft()
            PairEdit.DiscardDraft -> draft = null
            is PairEdit.ChooseDraftHalf ->
                picker = PairPick(null, edit.secondHalf, draft?.otherHalf(edit.secondHalf))
            is PairEdit.SwapSides -> repository?.swap(edit.pairId)
            is PairEdit.ChangeApp -> {
                val pair = repository?.current()?.firstOrNull { it.id == edit.pairId } ?: return
                val other = if (edit.secondHalf) pair.first else pair.second
                picker = PairPick(edit.pairId, edit.secondHalf, other)
            }
        }
    }

    /** An app was picked: the draft's half is set (and the pair is saved once both are), or a saved pair changes. */
    fun pick(app: AppIdentity) {
        val pick = picker ?: return
        picker = null
        if (pick.pairId == null) {
            val next = draft?.choose(app, pick.secondHalf) ?: return
            val first = next.first
            val second = next.second
            if (first != null && second != null) {
                repository?.add(first, second)
                draft = null
            } else {
                draft = next
            }
        } else if (pick.secondHalf) {
            repository?.changeSecond(pick.pairId, app)
        } else {
            repository?.changeFirst(pick.pairId, app)
        }
    }

    fun dismissPicker() {
        picker = null
    }

    /** The page was left or the Stream closed: a half-made pair is dropped, never stored. */
    fun leave() {
        draft = null
        picker = null
    }

    private fun PairSide.toPairApp() = PairApp(identity, label)
}
