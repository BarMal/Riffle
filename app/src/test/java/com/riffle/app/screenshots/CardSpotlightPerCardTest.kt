package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.stream.CardSpotlightLightKey
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamCard
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each card is lit by its own header icon. Four shortcut cards of ONE app (as a mail app's shortcuts are) show four
 * different icons; the light each reports is the colour of its icon, not the app's shared colour: at rest, after
 * another card comes to the front, after a card is dismissed, and after a re-post that changes a card's icon. A grey
 * icon gets a neutral light, never a neighbour's colour.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CardSpotlightPerCardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val app = ScreenshotFixtures.mail

    /** Icons by handle key: clearly different vivid colours, and a grey one. */
    private val icons =
        mapOf(
            "icon:red" to Color(0xFFE53935),
            "icon:green" to Color(0xFF2E9E3E),
            "icon:blue" to Color(0xFF1E5BE0),
            "icon:grey" to Color(0xFF8A8A8A),
            "icon:orange" to Color(0xFFF57C00),
        )

    private val loader =
        object : ExpressionImageLoader {
            override suspend fun load(
                handle: ItemImageHandle,
                sizePx: Int,
            ): ImageBitmap {
                val side = sizePx.coerceAtLeast(2)
                val bitmap = ImageBitmap(side, side)
                Canvas(bitmap).drawRect(
                    Rect(0f, 0f, side.toFloat(), side.toFloat()),
                    Paint().apply { color = icons.getValue(handle.key) },
                )
                return bitmap
            }
        }

    private fun shortcut(
        id: String,
        icon: String,
    ) = AppShortcutContent(
        id = StreamContentId("shortcut:$id"),
        app = StreamAppRef(app.identity.packageName.value, "personal"),
        appIdentity = app.identity,
        appLabel = app.label,
        shortcutId = id,
        shortLabel = id,
        longLabel = null,
        icon = ItemImageHandle(icon),
    )

    private fun fourCards() =
        listOf(
            shortcut("a", "icon:red"),
            shortcut("b", "icon:green"),
            shortcut("c", "icon:grey"),
            shortcut("d", "icon:blue"),
        )

    private var cards by mutableStateOf(listOf<AppShortcutContent>())

    private fun show() {
        val env: StreamEnvironment = StreamFixtures.environment().copy(imageLoader = loader)
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    cards.forEach { card ->
                        key(card.id.value) {
                            StreamCard(
                                content = card,
                                env = env,
                                callbacks = StreamCallbacks(),
                                groupKey = null,
                                modifier = Modifier.fillMaxWidth().height(72.dp).testTag(card.id.value),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun lightOf(card: AppShortcutContent): String {
        fun find(node: SemanticsNode): String? =
            node.config.getOrNull(CardSpotlightLightKey) ?: node.children.firstNotNullOfOrNull { find(it) }
        val root = composeRule.onNodeWithTag(card.id.value).fetchSemanticsNode()
        return checkNotNull(find(root)) { "no light reported inside ${card.id.value}" }
    }

    private fun dominantChannel(c: Color): Int = listOf(c.red, c.green, c.blue).let { it.indexOf(it.max()) }

    private fun change(next: () -> List<AppShortcutContent>) {
        composeRule.runOnIdle { cards = next() }
    }

    private fun hex(color: Color) = "#%08X".format(color.toArgb())

    private fun assertEachCardIsLitByItsOwnIcon() {
        composeRule.waitForIdle()
        cards.forEach { card ->
            val expected = icons.getValue(card.icon!!.key)
            val light = lightOf(card)
            if (card.icon!!.key == "icon:grey") {
                assertEquals("a grey icon gets a neutral light: ${card.id.value}", "neutral", light)
            } else {
                // The light is the icon's colour lifted to read as light: same hue, so the dominant channel agrees.
                val reported = Color(light.removePrefix("#").toLong(16))
                assertEquals(
                    "${card.id.value} reports its own icon's hue ($light vs ${hex(expected)})",
                    dominantChannel(expected),
                    dominantChannel(reported),
                )
            }
        }
        val colours = cards.filter { it.icon!!.key != "icon:grey" }.map { lightOf(it) }
        assertEquals("no two cards share a light", colours.size, colours.toSet().size)
    }

    @Test
    fun eachCardReportsItsOwnIconsColourAtRestAfterAReorderADismissalAndARepost() {
        cards = fourCards()
        show()
        assertEachCardIsLitByItsOwnIcon()
        composeRule.captureScreen()

        // Another card comes to the front.
        change { listOf(cards[3], cards[0], cards[1], cards[2]) }
        assertEachCardIsLitByItsOwnIcon()

        // The front card is dismissed.
        change { cards.drop(1) }
        assertEachCardIsLitByItsOwnIcon()

        // A re-post changes the front card's icon: the light follows at once.
        change { listOf(cards[0].copy(icon = ItemImageHandle("icon:orange"))) + cards.drop(1) }
        assertEachCardIsLitByItsOwnIcon()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theSameFourCardsInTheDarkTheme() {
        cards = fourCards()
        show()
        assertEachCardIsLitByItsOwnIcon()
        composeRule.captureScreen()
    }
}
