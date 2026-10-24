package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.wizard.AdvancedHandoff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A content wizard opened from Settings (drawn over it by the workspace layer). [layout] is the layout it edits. */
internal sealed interface WizardRequest {
    val layout: HomeLayoutDeviceClass

    /** Settings > Saved lenses > New. */
    data class NewSavedLens(override val layout: HomeLayoutDeviceClass) : WizardRequest

    /** A source page's "Use this source": [source] pre-selected, added to [layout]'s active workspace. */
    data class FromSource(
        val source: SourceId,
        override val layout: HomeLayoutDeviceClass,
    ) : WizardRequest
}

/**
 * Which Settings-opened wizard is showing, with no Android types. The editor's own wizard is part of the editor's
 * state instead. "Open advanced builder" hands the draft to the request's own handler (the Saved lenses builder) or,
 * for a source page, holds it for the editor the layer opens next ([takeEditorHandoff]).
 */
internal class ContentWizardRequests {
    private val mutableCurrent = MutableStateFlow<WizardRequest?>(null)
    private var onAdvanced: ((AdvancedHandoff) -> Unit)? = null

    @Volatile
    private var editorHandoff: AdvancedHandoff? = null

    /** The wizard to show, or null. */
    val current: StateFlow<WizardRequest?> = mutableCurrent.asStateFlow()

    fun open(
        request: WizardRequest,
        onAdvanced: ((AdvancedHandoff) -> Unit)? = null,
    ) {
        this.onAdvanced = onAdvanced
        mutableCurrent.value = request
    }

    fun close() {
        onAdvanced = null
        mutableCurrent.value = null
    }

    /**
     * Closes the wizard and gives [handoff] to the request's handler. Without one it is kept for the editor
     * ([takeEditorHandoff]) and false is returned, so the caller opens the editor.
     */
    fun advance(handoff: AdvancedHandoff): Boolean {
        val handler = onAdvanced
        close()
        return if (handler != null) {
            handler(handoff)
            true
        } else {
            editorHandoff = handoff
            false
        }
    }

    /** The draft the next editor starts its advanced flow on, once. */
    fun takeEditorHandoff(): AdvancedHandoff? = editorHandoff.also { editorHandoff = null }
}
