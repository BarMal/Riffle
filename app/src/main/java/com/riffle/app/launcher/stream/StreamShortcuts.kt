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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionPolicy

private val ShortcutIconSize = RiffleSpacing.xxxl
private const val DISABLED_ALPHA = 0.6f

/** The test tag of a shortcut card's tap target, by the shortcut's id (empty for the app's own "Open" card). */
internal fun streamShortcutTag(content: AppShortcutContent): String = "stream-shortcut:${content.shortcutId.orEmpty()}"

/**
 * One launcher shortcut of a pinned app as a card of its shortcuts stack (ADR 0003, Amendment 3, "Every icon has a
 * stack"): the shortcut's icon, its short label and, when it differs, its long label, lit by the app's colour. A tap
 * launches it; the app's own "Open <app>" card (for an app with no shortcuts) opens the app. A disabled shortcut says
 * so and does nothing.
 */
@Composable
internal fun AppShortcutCard(
    content: AppShortcutContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val title = shortcutTitle(content)
    val canLaunch = StreamActionPolicy.offers(content, StreamAction.Open)
    val launchLabel = stringResource(R.string.stream_shortcut_launch, title)
    StreamCardSurface(content.id.value, content.app, null, env, modifier) { sourceModifier ->
        Row(
            modifier =
                sourceModifier
                    .fillMaxSize()
                    .alpha(if (content.enabled || content.opensApp) 1f else DISABLED_ALPHA)
                    .testTag(streamShortcutTag(content))
                    .then(
                        if (canLaunch) {
                            Modifier.streamClickable({ callbacks.onAction(content, StreamAction.Open) }, launchLabel)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            StreamIcon(content.icon, env, ShortcutIconSize)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                shortcutDetail(content)?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The line under a shortcut's name: its long label, else why it is off, else (for the open card) what it does. */
@Composable
private fun shortcutDetail(content: AppShortcutContent): String? =
    when {
        content.opensApp -> stringResource(R.string.stream_shortcut_open_app_hint)
        !content.enabled -> content.disabledMessage ?: stringResource(R.string.stream_shortcut_disabled)
        else -> content.longLabel
    }

/** A shortcut in a List: its icon and labels in one row, a tap launches it. */
@Composable
internal fun AppShortcutRow(
    content: AppShortcutContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val canLaunch = StreamActionPolicy.offers(content, StreamAction.Open)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (canLaunch) {
                        Modifier.streamClickable({ callbacks.onAction(content, StreamAction.Open) })
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        StreamIcon(content.icon, env, ShortcutIconSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                shortcutTitle(content),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            shortcutDetail(content)?.let { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
