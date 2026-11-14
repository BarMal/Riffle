package com.riffle.app.launcher.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamHideItems
import com.riffle.core.domain.launcher.workspace.settings.ExclusionHideActions
import com.riffle.core.domain.launcher.workspace.settings.HideChoice

/** The smallest touch target (48 dp), the same as the largest spacing token. */
internal val StreamMinTarget: Dp = RiffleSpacing.xxxl

/** Text-heavy content stops growing at this width on tablets and unfolded foldables. */
internal val StreamMaxReadableWidth: Dp = 640.dp

/** A text button with a 48 dp target. */
@Composable
internal fun StreamTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = StreamMinTarget).widthIn(min = StreamMinTarget),
        enabled = enabled,
    ) { Text(text) }
}

/** The Hide choices of [content] (what the existing exclusions flow offers); empty when the shell offers no Hide. */
@Composable
internal fun rememberStreamHideChoices(
    content: StreamContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
): List<HideChoice> =
    remember(content, groupKey, callbacks.onHide != null) {
        if (callbacks.onHide == null) {
            emptyList()
        } else {
            StreamHideItems.itemFor(content, groupKey)?.let(ExclusionHideActions::menuChoicesFor).orEmpty()
        }
    }

/** The accessibility equivalents of Hide, one custom action per choice. */
internal fun Modifier.streamHideActions(
    choices: List<HideChoice>,
    content: StreamContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
): Modifier {
    val onHide = callbacks.onHide
    if (onHide == null || choices.isEmpty()) return this
    return semantics {
        customActions =
            choices.map { choice ->
                CustomAccessibilityAction(choice.label) {
                    onHide(content, groupKey, choice.kind)
                    true
                }
            }
    }
}

/** A 48 dp "More options" button whose menu holds [Hide]'s choices; draws nothing when there are none. */
@Composable
internal fun StreamHideMenuButton(
    choices: List<HideChoice>,
    content: StreamContent,
    groupKey: String?,
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    val onHide = callbacks.onHide
    if (onHide == null || choices.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(StreamMinTarget)) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = stringResource(R.string.stream_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        expanded = false
                        onHide(content, groupKey, choice.kind)
                    },
                )
            }
        }
    }
}

/** A tap target that is a button for accessibility. */
internal fun Modifier.streamClickable(
    onClick: () -> Unit,
    label: String? = null,
): Modifier = clickable(role = Role.Button, onClickLabel = label, onClick = onClick)

/** The test tag of the icon or avatar of a card's header: the part of a card its strip must show. */
internal const val STREAM_CARD_ICON_TAG = "stream-card-icon"

/** The test tag of the one-line title of a card's header: the part of a card its strip must show. */
internal const val STREAM_CARD_TITLE_TAG = "stream-card-title"

/** The size of the icon or avatar of a card's header. */
internal val StreamHeaderIconSize: Dp = 40.dp

/**
 * The top of every card, so the strip of it that peeks out behind the card in front says what the card is: an icon or
 * an avatar and a one-line title (and, under it, one line more). It is at most [StreamMinTarget] tall (its trailing
 * control is a 48 dp target), and a card puts it [RiffleSpacing.xs] below its top edge, so all of it lies in the 52 dp
 * a strip shows ([com.riffle.core.domain.launcher.stream.StreamCardStackSpec.STRIP_HEIGHT_DP]).
 */
@Composable
internal fun StreamCardHeader(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    /** Applied to the icon and the title (a tap target for the card), not to [trailing]'s own control. */
    contentModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = StreamMinTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = contentModifier.weight(1f).heightIn(min = StreamMinTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        ) {
            Box(
                modifier = Modifier.size(StreamHeaderIconSize).testTag(STREAM_CARD_ICON_TAG),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(STREAM_CARD_TITLE_TAG),
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        trailing()
    }
}

/** A glyph for the header of a card that has no app icon or photo of its own (an event, a task, a notice). */
@Composable
internal fun StreamGlyph(
    glyph: ImageVector,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = glyph,
        contentDescription = null,
        modifier = modifier.size(RiffleSpacing.xl + RiffleSpacing.xs),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The padding every card keeps above its header: the header is then wholly inside the card's strip. */
internal val StreamHeaderTopPadding: Dp = RiffleSpacing.xs
