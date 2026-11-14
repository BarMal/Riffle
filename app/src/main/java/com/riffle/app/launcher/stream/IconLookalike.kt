package com.riffle.app.launcher.stream

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs

/**
 * Whether two icons are the same picture, to tell a notification's large icon that is the app's own logo from a person's
 * photo. Both are shrunk to a small grid and compared by their mean colour difference, so a logo drawn at another size
 * or a little differently (masked, say) still matches, and a photo of a face does not.
 */
internal object IconLookalike {
    private const val GRID = 8
    private const val MAX_MEAN_DIFFERENCE = 18

    fun same(
        first: ImageBitmap?,
        second: ImageBitmap?,
    ): Boolean {
        if (first == null || second == null) return false
        return runCatching { same(first.asAndroidBitmap(), second.asAndroidBitmap()) }.getOrDefault(false)
    }

    fun same(
        first: Bitmap,
        second: Bitmap,
    ): Boolean {
        val a = grid(first)
        val b = grid(second)
        var total = 0
        for (index in a.indices) {
            for (channel in 0..2) {
                total += abs(((a[index] shr (channel * 8)) and 0xFF) - ((b[index] shr (channel * 8)) and 0xFF))
            }
        }
        return total / (a.size * 3) <= MAX_MEAN_DIFFERENCE
    }

    private fun grid(bitmap: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), GRID, GRID, true)
        return IntArray(GRID * GRID).also { small.getPixels(it, 0, GRID, 0, 0, GRID, GRID) }
    }
}

/** What an avatar is made of, decided apart from drawing it so the rule is tested. */
internal data class AvatarPlan(val face: AvatarFace, val badge: Boolean)

internal enum class AvatarFace { PHOTO, INITIALS, APP_ICON }

internal object AvatarPlanner {
    /**
     * A [person] (a conversation, or a contact photo) is a [photo] if the app published one, else their [hasInitial],
     * with the app's icon badged at the corner; anything else has no person to show, so the face is the app's icon, no
     * initials and no badge. The badge is only for a person's photo or initials: not on the app's own icon, nor on a
     * photo that [photoIsAppIcon] (a large icon an app set to its own logo).
     */
    fun plan(
        person: Boolean,
        hasPhoto: Boolean,
        hasInitial: Boolean,
        hasAppIcon: Boolean,
        photoIsAppIcon: Boolean,
    ): AvatarPlan {
        if (!person) return AvatarPlan(AvatarFace.APP_ICON, badge = false)
        val face =
            when {
                hasPhoto -> AvatarFace.PHOTO
                hasInitial -> AvatarFace.INITIALS
                else -> AvatarFace.APP_ICON
            }
        val badge = hasAppIcon && face != AvatarFace.APP_ICON && !(face == AvatarFace.PHOTO && photoIsAppIcon)
        return AvatarPlan(face, badge)
    }
}
