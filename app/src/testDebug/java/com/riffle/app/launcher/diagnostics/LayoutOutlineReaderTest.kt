package com.riffle.app.launcher.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The outline of a notification's own layout on the diagnostics page (device fixes, round 6, item 6), read from real
 * `RemoteViews` of this (debug) app: one line per view in drawing order with its depth, class, id name, whether it
 * takes a tap, its visibility, a text view's text length and size and an image view's picture size; and none of the
 * layout's text in what is shown or copied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class LayoutOutlineReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun trip(): RemoteViews =
        RemoteViews(context.packageName, R.layout.test_notification_custom).apply {
            setTextViewText(R.id.test_notification_title, TITLE)
            setTextViewText(R.id.test_notification_line_one, LINE)
            setTextViewText(R.id.test_notification_button, "Next")
            setImageViewBitmap(R.id.test_notification_image, Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888))
        }

    @Test
    fun theOutlineListsEveryViewInDrawingOrderWithItsStructure() {
        val lines =
            LayoutOutlineReader.outlineOf(context, trip())!!.map { it.toShape().outlineLine() }

        assertEquals(
            listOf(
                "  LinearLayout, clickable no, visible",
                "    ImageView #test_notification_image, clickable no, visible, image 120 x 90 px",
                "    LinearLayout, clickable no, visible",
                "      TextView #test_notification_line_one, clickable no, visible, text ${LINE.length} chars, 14 sp",
                "      TextView #test_notification_title, clickable no, visible, text ${TITLE.length} chars, 20 sp",
                "      TextView #test_notification_line_two, clickable no, visible, text 0 chars, 14 sp",
                "      TextView #test_notification_gone, clickable no, gone, text 0 chars, 30 sp",
                "    Button #test_notification_button, clickable yes, visible, text 4 chars, 14 sp",
            ),
            lines,
        )
    }

    @Test
    fun noneOfTheLayoutsTextIsInWhatIsShownOrCopied() {
        val shapes = LayoutOutlineReader.outlineOf(context, trip())!!.map { it.toShape() }
        val shown = shapes.joinToString("\n") { it.outlineLine() } + shapes.joinToString { it.toString() }
        assertFalse(shown.contains(TITLE))
        assertFalse(shown.contains(LINE))
        assertTrue(shown.contains("text ${TITLE.length} chars"))
    }

    @Test
    fun aLayoutThatCannotBeInflatedHasNoOutline() {
        assertNull(LayoutOutlineReader.outlineOf(context, RemoteViews(context.packageName, 0x7f0f_ffff)))
    }

    private companion object {
        const val TITLE = "Get off at St Thomas' Hospital"
        const val LINE = "3 stops away"
    }
}
