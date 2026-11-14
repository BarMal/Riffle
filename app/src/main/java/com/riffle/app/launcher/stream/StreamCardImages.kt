package com.riffle.app.launcher.stream

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/*
 * The images a Stream card shows (ADR 0003, Amendment 1, "Images"): the contact photo as a round avatar with the app's
 * icon as a small badge at its corner, one small thumbnail on a browse card and the picture in full on an open one.
 * All of it comes from what the app publishes; when it publishes none the avatar is the sender's initials (or the app's
 * icon when the card has no name) and there is no thumbnail. Every image is loaded sized to what is drawn, held in the
 * composition for as long as the card is composed and no longer, and never written anywhere.
 */

private val BadgeBorder = 1.5.dp

/**
 * A round avatar of [size]: [photo] when the app published one, else [name]'s initials, else the app's own icon. The
 * app's icon sits at the avatar's corner as a badge when there is a photo or initials for it to badge. Decorative: the
 * name is shown beside it, so it is no node for assistive technology.
 */
@Composable
internal fun StreamAvatar(
    photo: ItemImageHandle?,
    appIcon: ItemImageHandle?,
    name: String?,
    env: StreamEnvironment,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val image = rememberStreamImage(photo, env.imageLoader, size)
    val loaded = image.value
    val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
    // A badge only where there is something to badge: a photo, or initials. Without either the app icon is the avatar.
    val badged = photo != null || initial != null
    Box(modifier = modifier.size(size).cardSpotlightSource("avatar").clearAndSetSemantics {}) {
        when {
            loaded != null ->
                Image(
                    bitmap = loaded,
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            initial != null ->
                Box(
                    modifier =
                        Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            else -> StreamIcon(appIcon, env, size, Modifier, CircleShape)
        }
        if (badged && appIcon != null) {
            val badge = size * BADGE_FRACTION
            StreamIcon(
                handle = appIcon,
                env = env,
                size = badge,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = badge * BADGE_OVERHANG, y = badge * BADGE_OVERHANG)
                        .border(BadgeBorder, MaterialTheme.colorScheme.surface, RiffleShapes.small),
                shape = RiffleShapes.small,
            )
        }
    }
}

/** The badge is this share of the avatar's diameter, and hangs this share of itself past its corner. */
private const val BADGE_FRACTION = 0.42f
private const val BADGE_OVERHANG = 0.2f

/**
 * The picture a notification carries, small, at the trailing edge of a browse card. Draws nothing until it has loaded
 * and nothing at all when it cannot be resolved. Its description is generic (who it is from, never what it shows).
 */
@Composable
internal fun StreamThumbnail(
    picture: ItemImageHandle?,
    sender: String?,
    env: StreamEnvironment,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val image = rememberStreamImage(picture, env.imageLoader, size)
    val loaded = image.value ?: return
    val description = pictureDescription(sender)
    Image(
        bitmap = loaded,
        contentDescription = description,
        modifier = modifier.size(size).clip(RiffleShapes.medium),
        contentScale = ContentScale.Crop,
    )
}

/** A picture in full width on an open card, at its own proportions up to [maxHeight]. */
@Composable
internal fun StreamPicture(
    picture: ItemImageHandle?,
    sender: String?,
    env: StreamEnvironment,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // Loaded at the size it is drawn: the width of the card (at most the readable width) and no more.
    val image = rememberStreamImage(picture, env.imageLoader, StreamMaxReadableWidth)
    val loaded = image.value ?: return
    val description = pictureDescription(sender)
    Image(
        bitmap = loaded,
        contentDescription = description,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RiffleShapes.medium)
                .semantics { this.contentDescription = description },
        contentScale = ContentScale.Fit,
        alignment = Alignment.TopStart,
    )
}

@Composable
private fun pictureDescription(sender: String?): String =
    sender?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.stream_picture_from, it) }
        ?: stringResource(R.string.stream_picture)
