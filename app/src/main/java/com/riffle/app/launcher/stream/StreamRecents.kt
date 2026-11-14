package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction

private val CardAppIconSize = RiffleSpacing.xxxl + RiffleSpacing.s

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
        Row(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .padding(start = RiffleSpacing.l, top = RiffleSpacing.s, bottom = RiffleSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .streamClickable({ callbacks.onAction(content, StreamAction.Open) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                StreamIcon(content.icon, env, CardAppIconSize)
                Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
                    Text(
                        text = content.label ?: stringResource(R.string.stream_hidden_content),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recentSubtitle(content, env, caption)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
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
