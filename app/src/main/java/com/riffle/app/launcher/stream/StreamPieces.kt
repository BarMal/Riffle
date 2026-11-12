package com.riffle.app.launcher.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
