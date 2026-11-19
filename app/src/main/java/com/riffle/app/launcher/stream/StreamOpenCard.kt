package com.riffle.app.launcher.stream

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationBody
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.TaskContent

private val OpenAvatarSize = 56.dp
private val OpenPictureMaxHeight = 280.dp
private val OpenArtworkMaxWidth = 320.dp

/**
 * A card opened in place (ADR 0003, Amendment 1, "Stream: browse, then open"): its full content, on the same tinted
 * glass as its browse card. A conversation is its thread and its picture in full; the player is its artwork, scrubber
 * and queue; an event its detail; a task its title and due date. Its actions are in the Dock's row, or, when
 * [actionsOnCard], on the card under its content; either way the card carries them as accessibility actions, so a
 * screen reader on it never has to leave it. A tap on the card closes it ([onClose]); the controls on it keep their
 * own taps. Taller than the room there is, it scrolls.
 */
@Composable
@Suppress("LongParameterList")
internal fun OpenStreamCard(
    content: StreamContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    actionsOnCard: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The scroll state of the card's body: the layer reads whether it scrolls to decide who takes a vertical drag. */
    bodyScroll: ScrollState = rememberScrollState(),
    /** Previous and Next card as accessibility actions of this card (a stack of more than one card); else null. */
    cardSteps: CardSteps? = null,
    /** How open the card is while the stack moves from one card to the next: its body fades and its header scales. */
    presence: OpenCardPresence? = null,
    /** The drag past the body's edge toward a neighbour, and the neighbours' titles it names; null: no cue. */
    cue: HandoffCueState? = null,
    olderTitle: String? = null,
    newerTitle: String? = null,
) {
    val hideChoices = rememberStreamHideChoices(content, groupKey, callbacks)
    val actions = StreamCardActions.forContent(content)
    // A notification's card is swiped away by its header: the whole card follows the finger.
    val swipe = rememberSwipeDismissState(content, callbacks, env.reducedMotion)
    StreamCardSurface(
        content.id.value,
        content.appRef(),
        content.iconHandle(),
        env,
        modifier.swipeLayer(swipe),
    ) { sourceModifier ->
        // Three parts, so that a card that is shorter than its content (the keyboard is up, the window is short) gives
        // up its middle and not its ends: the header stays at the top and the actions (the reply field) at the bottom,
        // and the thread between them scrolls.
        Column(
            modifier =
                sourceModifier
                    .fillMaxWidth()
                    .streamHideActions(hideChoices, content, groupKey, callbacks)
                    .cardActionAccessibility(content, actions, callbacks, cardSteps.actions())
                    .streamClickable(onClose, stringResource(R.string.stream_card_close))
                    // A card measured shorter than its content (the stack is moving) is cut at its own edge.
                    .clipToBounds()
                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.m),
            verticalArrangement = PinnedLastArrangement(RiffleSpacing.m, pinLast = actionsOnCard),
        ) {
            if (content is NotificationContent) {
                OpenNotificationHeader(
                    content,
                    env,
                    groupKey,
                    callbacks,
                    hideChoices,
                    Modifier.swipeToDismiss(swipe).headerScale(presence),
                )
            }
            Box(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    // The body dims as the finger goes past its edge toward a neighbour, so the cue reads over it.
                    modifier = Modifier.bodyFade(presence, cue).verticalScroll(bodyScroll),
                    verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
                ) {
                    when (content) {
                        is NotificationContent -> OpenNotificationBody(content, env)
                        is MediaContent ->
                            OpenMedia(content, env, callbacks, groupKey, hideChoices)
                        is EventContent -> OpenEvent(content, env, callbacks, groupKey, hideChoices)
                        is TaskContent -> OpenTask(content, env)
                        else -> Unit
                    }
                }
                if (cue != null && presence != null) {
                    HandoffCue(cue, presence, olderTitle, newerTitle)
                }
            }
            if (actionsOnCard) {
                CardActionControls(content, actions, callbacks, Modifier.bodyFade(presence))
            }
        }
    }
}

/** The labels of an open card's Previous and Next card actions and what they do ([step] true: the older card). */
internal class CardSteps(
    val previousLabel: String,
    val nextLabel: String,
    val step: (older: Boolean) -> Boolean,
)

/** Previous and Next card as the open card's own custom accessibility actions: the neighbour opens in place. */
private fun CardSteps?.actions(): List<CustomAccessibilityAction> =
    if (this == null) {
        emptyList()
    } else {
        listOf(
            CustomAccessibilityAction(previousLabel) { step(false) },
            CustomAccessibilityAction(nextLabel) { step(true) },
        )
    }

/** The header of an open conversation: who, from which app and when. It stays put while the thread scrolls. */
@Composable
private fun OpenNotificationHeader(
    content: NotificationContent,
    env: StreamEnvironment,
    groupKey: String?,
    callbacks: StreamCallbacks,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        StreamAvatar(
            content.avatar,
            content.icon,
            content.headline,
            env,
            OpenAvatarSize,
            person = content.hasPerson,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.headline ?: hiddenText(),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    listOfNotNull(
                        content.secondaryLabel,
                        env.formats.age(content.postedAtEpochMillis),
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
}

/** The body of an open conversation: its thread (or its text), the picture the app attached and "Open". */
@Composable
private fun OpenNotificationBody(
    content: NotificationContent,
    env: StreamEnvironment,
) {
    when (val body = content.body) {
        is NotificationBody.Thread -> NotificationThread(content)
        is NotificationBody.Text -> Text(text = body.text, style = MaterialTheme.typography.bodyLarge)
        is NotificationBody.Custom ->
            if (body.content.embed) {
                // The app's own (expanded) layout: nothing in it could be drawn in the card's own style.
                EmbeddedNotificationLayout(
                    notificationKey = content.notificationKey.orEmpty(),
                    appLabel = content.appLabel,
                    big = true,
                    revision = body.content.revision,
                    maxHeight = EmbeddedOpenMaxHeight,
                    interactive = true,
                )
            } else {
                body.content.bigLines.ifEmpty { body.content.lines }.forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyLarge)
                }
            }
        NotificationBody.None -> Unit
    }
    // The picture the app attached, across the card under the conversation it belongs to.
    val sender = content.thread.lastOrNull()?.sender ?: content.headline
    StreamPicture(content.picture ?: content.custom?.thumbnail, sender, env, OpenPictureMaxHeight)
    // "Open" is one of the card's actions (the Dock's row, or the card's own row), never also in the body.
}

@Composable
private fun OpenMedia(
    content: MediaContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
) {
    // The artwork large and square, only once it has loaded: a player that publishes none leaves no empty square.
    val artwork = rememberStreamImage(content.artwork, env.imageLoader, OpenArtworkMaxWidth).value
    if (artwork != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                bitmap = artwork,
                contentDescription = stringResource(R.string.stream_artwork),
                modifier =
                    Modifier
                        .widthIn(max = OpenArtworkMaxWidth)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RiffleShapes.medium),
                contentScale = ContentScale.Crop,
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        StreamIcon(content.icon, env, RiffleSpacing.xl, shape = RiffleShapes.small, lightsCard = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.title ?: stringResource(R.string.stream_hidden_content),
                style = MaterialTheme.typography.titleLarge,
            )
            content.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
    MediaScrubber(content, callbacks)
}

@Composable
private fun OpenEvent(
    content: EventContent,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    groupKey: String?,
    hideChoices: List<com.riffle.core.domain.launcher.workspace.settings.HideChoice>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        Text(
            text = eventSpanText(content, env),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        StreamHideMenuButton(hideChoices, content, groupKey, callbacks)
    }
    Text(text = eventTitle(content), style = MaterialTheme.typography.headlineSmall)
    (content.location ?: content.calendarName)?.takeIf { it.isNotBlank() }?.let { place ->
        Text(
            text = place,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = eventCountdownText(content, env.nowEpochMillis),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun OpenTask(
    content: TaskContent,
    env: StreamEnvironment,
) {
    Text(text = content.title ?: hiddenText(), style = MaterialTheme.typography.headlineSmall)
    content.listName?.takeIf { it.isNotBlank() }?.let { list ->
        Text(
            text = list,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    taskDueLabel(content, env)?.let { due -> Text(text = due, style = MaterialTheme.typography.bodyMedium) }
}
