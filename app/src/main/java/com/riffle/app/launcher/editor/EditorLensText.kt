package com.riffle.app.launcher.editor

import com.riffle.app.launcher.LensesMessageText
import com.riffle.app.launcher.LensesSettingsText
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.editor.AdoptOffer
import com.riffle.core.domain.launcher.workspace.editor.ExpressionChoice
import com.riffle.core.domain.launcher.workspace.editor.LensPreset
import com.riffle.core.domain.launcher.workspace.editor.SavedLensBlock
import com.riffle.core.domain.launcher.workspace.editor.SavedLensChoice

/** Wording of the editor's saved-lens parts (use, Save as lens, adopt, detach). Plain strings for JVM tests. */
internal object EditorLensText {
    const val USE_SAVED = "Use a saved lens"
    const val SAVED_HEADING = "Saved lenses"
    const val BUILD_OWN = "Build my own instead"
    const val NONE_SAVED =
        "No saved lenses on this layout yet. Save one from the last step, or build them in Settings > Saved lenses."
    const val DETACH = "Detach"
    const val CHOOSE_ANOTHER = "Choose a different saved lens"
    const val USING_NOTE =
        "A saved lens is shared: changing it changes every container that uses it. Change it in Settings > Saved " +
            "lenses, which shows what an edit would affect. To change it only here, detach it first."
    const val SAVE_AS = "Save as a lens"
    const val SAVE_AS_HELP =
        "Keep this choice as a named lens that other containers can use too. Changing the saved lens later " +
            "changes every container that uses it."
    const val NAME_LABEL = "Lens name"
    const val SAVE_AS_FULL =
        "This layout already has $MAX_SAVED_LENSES saved lenses. " +
            "Delete one in Settings > Saved lenses to save another."
    const val OFFER_ACTION = "Use it there too"
    const val MISSING = "Saved lens missing: using a copy"
    const val SAVED_LENS_FIELD = "Saved lens"

    fun usingTitle(name: String): String = "Using the saved lens “$name”"

    fun detachDescription(
        name: String?,
        where: String,
    ): String = "$DETACH $where from saved lens ${name ?: "that is missing"}"

    fun overviewLine(name: String?): String = if (name == null) MISSING else "$SAVED_LENS_FIELD: $name"

    fun nameCounter(length: Int): String = "$length of $MAX_SAVED_LENS_NAME"

    fun listLabel(count: Int): String = "$USE_SAVED ($count)"

    /** Sources, shape and where it is used: the row's second line. */
    fun summary(choice: SavedLensChoice): String =
        listOf(
            choice.saved.lens.sources.joinToString(", ") { EditorText.sourceLabel(it) },
            if (choice.saved.lens.group != LensGroup.None) "grouped" else "flat",
            LensesSettingsText.usedIn(choice.usedIn),
        ).joinToString(" · ")

    /** What a screen reader says for a row: name, summary, why it is unavailable, and where it is in the list. */
    fun spoken(
        choice: SavedLensChoice,
        reason: String?,
        position: Int,
        total: Int,
    ): String =
        listOfNotNull(
            choice.saved.name,
            summary(choice),
            reason?.let { "Not available here: $it" },
            "Saved lens $position of $total",
        ).joinToString(". ")
}

/** The confirmations and the offer shown after Save as lens, adopt and detach. */
internal object EditorLensMessages {
    fun libraryProblem(problem: LibraryProblem): String = LensesMessageText.problem(problem)

    fun savedAsLens(name: String): String = "Saved as the lens “$name”. Undo takes it back."

    fun offer(offer: AdoptOffer): String = "Use “${offer.name}” in the ${others(offer.count)} with an identical lens?"

    fun adopted(
        name: String,
        count: Int,
    ): String = "“$name” is now used in the ${others(count)}. Undo reverts them together."

    fun detached(name: String): String = "Detached from “$name”. It keeps its lens as its own. Undo reattaches it."

    private fun others(count: Int): String = if (count == 1) "1 other container" else "$count other containers"
}

/** Why a saved lens is listed but cannot be used for the container being configured. */
internal object EditorLensReasons {
    fun savedLens(block: SavedLensBlock): String =
        when (block) {
            is SavedLensBlock.UnavailableSource -> "Uses ${EditorText.sourceLabel(
                block.source,
            )}, which is not available"
            SavedLensBlock.Unsupported -> "Can only be changed in Settings > Saved lenses"
            is SavedLensBlock.CannotDraw -> block.nearest?.let { cannotDraw(it) } ?: NOTHING_CAN_DRAW
        }

    private fun cannotDraw(choice: ExpressionChoice): String =
        when {
            choice.containerIssues.isNotEmpty() ->
                choice.containerIssues.joinToString(
                    "; ",
                ) { EditorReasonText.workspace(it) }
            choice.lensIssues.isNotEmpty() -> choice.lensIssues.joinToString("; ") { EditorReasonText.lens(it) }
            else -> NOTHING_CAN_DRAW
        }

    private const val NOTHING_CAN_DRAW = "Nothing here can draw it"
}

/** The name suggested when saving a lens: where it comes from and how it is shaped, cut to the library's limit. */
internal object EditorLensNames {
    fun suggest(
        lens: Lens,
        preset: LensPreset?,
    ): String {
        val from = lens.sources.joinToString(" + ") { EditorText.sourceLabel(it) }
        val shape = preset?.takeIf { it != LensPreset.EVERYTHING }?.let { EditorText.presetLabel(it).lowercase() }
        return listOfNotNull(from, shape).joinToString(", ").take(MAX_SAVED_LENS_NAME).trim()
    }
}
