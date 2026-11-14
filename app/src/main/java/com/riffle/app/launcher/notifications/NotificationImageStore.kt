package com.riffle.app.launcher.notifications

import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * The contact photos and pictures of the active notifications, kept in memory only: never written to disk, logged or
 * put in diagnostics, and dropped as soon as the notification goes (the listener replaces the whole set at every change
 * and clears it when it disconnects). Resolving a handle is [bitmapFor], off the main thread like every item image.
 *
 * Memory is bounded in three ways: each image is sized down when it is captured ([NotificationImageCapture]: a
 * picture to [MAX_PICTURE_EDGE_PX], a contact photo to [MAX_PERSON_EDGE_PX]), the whole set is held to
 * [NotificationImageBudget.MAX_BYTES] and [NotificationImageBudget.MAX_PICTURES] by [NotificationImageBudget] (the
 * newest notifications keep theirs), and the card draws it again sized to what it shows.
 */
internal object NotificationImageStore {
    private val images = ConcurrentHashMap<String, Bitmap>()

    fun bitmapFor(handleKey: String): Bitmap? = images[handleKey]

    /** Makes [byHandle] the only images held. */
    fun replaceAll(byHandle: Map<String, Bitmap>) {
        images.keys.retainAll(byHandle.keys)
        images.putAll(byHandle)
    }

    fun clear() = images.clear()

    /** The handle prefixes this store answers for. */
    fun holds(handleKey: String): Boolean = handleKey.startsWith(PERSON_PREFIX) || handleKey.startsWith(PICTURE_PREFIX)

    const val PERSON_PREFIX = "notification-person:"
    const val PICTURE_PREFIX = "notification-picture:"
    const val MAX_PICTURE_EDGE_PX = 512
    const val MAX_PERSON_EDGE_PX = 128
}

/**
 * Which captured images the store keeps. Pure, so it can be tested without a device: contact photos are small and
 * kept first, then pictures newest first, until the byte budget or the count is spent.
 */
internal object NotificationImageBudget {
    /** The most the store holds, in bytes of pixels. */
    const val MAX_BYTES = 8 * 1024 * 1024

    /** The most pictures it holds (a contact photo is small and has no count of its own). */
    const val MAX_PICTURES = 8

    class Candidate(
        val handleKey: String,
        val bytes: Int,
        val isPicture: Boolean,
        /** When the notification was posted; the newest keep their pictures. */
        val postedAtEpochMillis: Long,
    )

    private class Tally(
        val kept: Set<String> = emptySet(),
        val bytes: Int = 0,
        val pictures: Int = 0,
    )

    fun select(candidates: List<Candidate>): Set<String> {
        val ordered =
            candidates.sortedWith(compareBy<Candidate> { it.isPicture }.thenByDescending { it.postedAtEpochMillis })
        return ordered.fold(Tally()) { tally, candidate ->
            val fits =
                tally.bytes + candidate.bytes <= MAX_BYTES && (!candidate.isPicture || tally.pictures < MAX_PICTURES)
            if (fits) {
                Tally(
                    kept = tally.kept + candidate.handleKey,
                    bytes = tally.bytes + candidate.bytes,
                    pictures = tally.pictures + if (candidate.isPicture) 1 else 0,
                )
            } else {
                tally
            }
        }.kept
    }
}
