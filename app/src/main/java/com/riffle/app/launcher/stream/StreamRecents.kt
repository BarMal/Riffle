package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction

private val CardAppIconSize = StreamHeaderIconSize

/**
 * A recent app as a card of the Recents stack, like every other page's: its icon (in the app's own colour), its
 * name and when it was last used ("Often used" first, for the apps used most). A tap launches the app; Hide is in
 * the card's menu (and its accessibility actions).
 */
@Composable
internal fun RecentAppCard(
    content: RecentAppContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    modifier: Modifier = Modifier,
    /** Said before when it was used ("Often used"), or null. */
    caption: String? = null,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    StreamCardSurface(content.id.value, content.app, content.icon, env, modifier) { sourceModifier ->
        Column(
            // From the top: the icon and the name are what the card's strip shows.
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .padding(start = RiffleSpacing.l, top = StreamHeaderTopPadding, bottom = RiffleSpacing.s),
        ) {
            StreamCardHeader(
                icon = { StreamIcon(content.icon, env, CardAppIconSize, lightsCard = true) },
                title = content.label ?: stringResource(R.string.stream_hidden_content),
                subtitle = recentSubtitle(content, env, caption),
                contentModifier = Modifier.streamClickable({ callbacks.onAction(content, StreamAction.Open) }),
                trailing = { StreamHideMenuButton(hideChoices, content, groupKey, callbacks) },
            )
        }
    }
}

/** "Often used, 2 h. ago", "Last used 5 min. ago", or just one of them; null when the app has neither. */
@Composable
private fun recentSubtitle(
    content: RecentAppContent,
    env: StreamEnvironment,
    caption: String?,
): String? {
    val used =
        content.lastUsedEpochMillis?.let {
            stringResource(
                R.string.stream_recent_last_used,
                env.formats.age(it),
            )
        }
    return listOfNotNull(caption, used).joinToString(", ").ifEmpty { null }
}
