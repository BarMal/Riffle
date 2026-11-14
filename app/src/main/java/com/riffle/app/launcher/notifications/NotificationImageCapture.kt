package com.riffle.app.launcher.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys

/** What a notification publishes besides its text that a card can draw. */
internal class CapturedNotificationImages(
    val person: Bitmap?,
    val picture: Bitmap?,
)

/**
 * Reads the contact photo and the picture out of a notification and sizes them down for the in-memory
 * [NotificationImageStore]. Every read is wrapped: a payload of a shape this does not know (an Icon where a Bitmap was
 * expected, a malformed Person) reads as no image rather than failing the notification.
 */
internal object NotificationImageCapture {
    fun capture(
        context: Context,
        notification: Notification,
    ): CapturedNotificationImages =
        CapturedNotificationImages(
            person = runCatching { person(context, notification) }.getOrNull(),
            picture = runCatching { picture(context, notification) }.getOrNull(),
        )

    @Suppress("DEPRECATION")
    private fun picture(
        context: Context,
        notification: Notification,
    ): Bitmap? {
        val extras = notification.extras ?: return null
        val bitmap =
            extras.parcelableOrNull<Bitmap>(Notification.EXTRA_PICTURE)
                ?: extras.parcelableOrNull<Icon>(Notification.EXTRA_PICTURE_ICON)
                    ?.let { icon -> icon.loadDrawable(context)?.toBitmap() }
        return bitmap?.sizedDownTo(NotificationImageStore.MAX_PICTURE_EDGE_PX)
    }

    /** The newest message's sender photo, else null: a conversation's own person icon is not read. */
    private fun person(
        context: Context,
        notification: Notification,
    ): Bitmap? {
        val icon =
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages?.lastOrNull { message -> message.person?.icon != null }?.person?.icon
        val edge = NotificationImageStore.MAX_PERSON_EDGE_PX
        return icon?.toIcon(context)?.loadDrawable(context)?.toBitmap(edge, edge)
    }

    private inline fun <reified T> Bundle.parcelableOrNull(key: String): T? =
        runCatching { BundleCompat.getParcelable(this, key, T::class.java) }.getOrNull()

    private fun Bitmap.sizedDownTo(edge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= edge) return this
        val scale = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** The handle of [sbn]'s contact photo and its picture, as the item mapper names them. */
    fun handlesOf(
        profileId: com.riffle.core.domain.launcher.apps.AppProfileId,
        key: String,
    ): Pair<String, String> =
        ItemImageKeys.notificationPerson(profileId, key).key to ItemImageKeys.notificationPicture(profileId, key).key
}
