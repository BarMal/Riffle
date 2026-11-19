package com.riffle.app.launcher.stream

import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent

/**
 * An action that did not work, as the Stream says it. [openedApp] is the app's name when the action was opening a
 * player that no route could open ("Couldn't open <app>"); null is the general "That did not work."
 */
internal data class StreamFailure(val openedApp: String? = null) {
    companion object {
        fun of(
            content: StreamContent,
            action: StreamAction,
        ): StreamFailure =
            if (action == StreamAction.Open && content is MediaContent) {
                StreamFailure(content.appLabel ?: content.app.packageName)
            } else {
                StreamFailure()
            }
    }
}
