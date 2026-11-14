package com.riffle.app.launcher.stream

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * A round avatar of [size]. For a [person] (a conversation, or a contact photo): [photo] when the app published one,
 * else [name]'s initials, with the app's icon badged at its corner. For anything else (a promotion, a download, a
 * status) there is no person to show: the avatar is the app's own icon, with no initials and no badge. The badge is
 * only for a person's photo or initials: it is not drawn on the app's own icon, nor on a photo that is the app's icon
 * (a large icon an app sets to its own logo). Decorative: the name is shown beside it, so it is no node for assistive
 * technology.
 */
@Composable
internal fun StreamAvatar(
    photo: ItemImageHandle?,
    appIcon: ItemImageHandle?,
    name: String?,
    env: StreamEnvironment,
    size: Dp,
    modifier: Modifier = Modifier,
    person: Boolean = true,
) {
    val image = rememberStreamImage(photo.takeIf { person }, env.imageLoader, size)
    val loaded = image.value
    val appImage = rememberStreamImage(appIcon.takeIf { person && photo != null }, env.imageLoader, size).value
    val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.takeIf { person }
    // A photo that is the app's own icon is the app's icon: it takes no badge of the app's icon.
    val looksLikeAppIcon = remember(loaded, appImage) { IconLookalike.same(loaded, appImage) }
    val plan =
        AvatarPlanner.plan(
            person = person,
            hasPhoto = loaded != null,
            hasInitial = initial != null,
            hasAppIcon = appIcon != null,
            photoIsAppIcon = looksLikeAppIcon,
        )
    Box(modifier = modifier.size(size).cardSpotlightSource("avatar").clearAndSetSemantics {}) {
        when (plan.face) {
            AvatarFace.PHOTO ->
                Image(
                    bitmap = checkNotNull(loaded),
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            AvatarFace.INITIALS ->
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
            AvatarFace.APP_ICON -> StreamIcon(appIcon, env, size, Modifier, CircleShape)
        }
        if (plan.badge && appIcon != null) {
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

/**
 * A picture across the open card's whole content width, at its own proportions: as tall as its width and its aspect
 * ratio make it up to [maxHeight] (a taller one is cropped about its centre, never squeezed), with all four corners
 * rounded to the card's inner radius (the card's corner less the padding the picture sits in), so it sits in the card
 * like a part of it and leaves no empty room beside or below it.
 */
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height = PictureLayout.heightFor(maxWidth, loaded.width, loaded.height, maxHeight)
        Image(
            bitmap = loaded,
            contentDescription = description,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(PictureInnerShape)
                    .semantics { this.contentDescription = description },
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }
}

/** The picture's corners: the card's corner (24 dp) less the 16 dp of padding it sits in. */
private val PictureInnerShape = RiffleShapes.small

/** How tall a picture is drawn. Pure, so it is tested. */
internal object PictureLayout {
    /** The height of a [pixelWidth] x [pixelHeight] image drawn [width] wide: its own proportions, at most [max]. */
    fun heightFor(
        width: Dp,
        pixelWidth: Int,
        pixelHeight: Int,
        max: Dp,
    ): Dp {
        if (pixelWidth <= 0 || pixelHeight <= 0) return 0.dp
        return (width * (pixelHeight.toFloat() / pixelWidth)).coerceAtMost(max)
    }
}

@Composable
private fun pictureDescription(sender: String?): String =
    sender?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.stream_picture_from, it) }
        ?: stringResource(R.string.stream_picture)
