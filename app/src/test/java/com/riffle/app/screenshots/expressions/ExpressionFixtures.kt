package com.riffle.app.screenshots.expressions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.riffle.app.launcher.expressions.ExpressionEnvironment
import com.riffle.app.launcher.expressions.ExpressionTimeFormatter
import com.riffle.app.launcher.expressions.FakeExpressionImageLoader
import com.riffle.app.screenshots.ScreenshotBackdrop
import com.riffle.app.screenshots.ScreenshotFixtures
import com.riffle.app.screenshots.captureScreen
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.testing.fakeItem

/** Deterministic lens results and collaborators shared by the expression screenshot tests. */
internal object ExpressionFixtures {
    val imageLoader = FakeExpressionImageLoader()

    /** Ages measured from the fixed fixture "now", never the wall clock. */
    val timeFormatter =
        ExpressionTimeFormatter { millis ->
            "${(ScreenshotFixtures.FIXED_NOW_EPOCH_MILLIS - millis) / MILLIS_PER_MINUTE} min"
        }

    val environment = ExpressionEnvironment(imageLoader = imageLoader, timeFormatter = timeFormatter)

    private const val MILLIS_PER_MINUTE = 60_000L

    private val appNames =
        listOf(
            "Alarms", "Bank", "Calendar", "Camera", "Chat", "Clock", "Drive", "Files", "Gallery", "Maps",
            "Mail", "Music", "Notes", "Phone", "Photos", "Settings", "Weather", "1Password", "Wallet", "Zoom",
        )

    private val apps: List<Item> =
        appNames.map { name ->
            fakeItem(id = name.lowercase(), sourceId = "apps", title = name)
                .copy(icon = ItemImageHandle(name.lowercase()))
        }

    /** The first [count] apps, each with an icon, in a flat result. */
    fun flatApps(count: Int = appNames.size): LensResult = LensResult.Flat(apps.take(count))

    /** Apps without icons, for expressions that use the icon only as an optional enrichment. */
    fun flatAppsWithoutIcons(count: Int = appNames.size): LensResult =
        LensResult.Flat(apps.take(count).map { it.copy(icon = null) })

    /** Messages with subtitle, body snippet and a timestamp. */
    fun flatMessages(): LensResult = LensResult.Flat(messages())

    /** The same messages grouped by the app that posted them. */
    fun groupedMessages(): LensResult {
        val groups =
            messages().groupBy { it.groupKey.orEmpty() }.entries.map { entry ->
                ItemGroup(entry.key, entry.value.first().groupLabel, entry.value)
            }
        return LensResult.Grouped(groups)
    }

    /** One group per category, each holding icons, for the Categories expression. */
    fun groupedApps(): LensResult {
        val groups = listOf("Social", "Productivity", "Utilities", "Media")
        return LensResult.Grouped(
            groups.mapIndexed { index, label ->
                val members =
                    apps.filterIndexed { position, _ -> position % groups.size == index }
                        .map { it.copy(groupKey = label.lowercase(), groupLabel = label) }
                ItemGroup(label.lowercase(), label, members)
            },
        )
    }

    private fun messages(): List<Item> =
        listOf(
            message("m1", "Alex", "Dinner tonight?", "Are we still on for 7? I can book the corner table.", "Chat", 5),
            message("m2", "Build pipeline", "Deploy finished", "All 312 checks passed on main.", "Mail", 22),
            message(
                "m3",
                "Priya",
                "Photos from the trip",
                "Uploaded the full album, the sunset ones are best.",
                "Chat",
                48,
            ),
            message("m4", "Calendar", "Design review at 15:00", "Room 4B, bring the updated mocks.", "Calendar", 95),
            message("m5", "Bank", "Payment received", "A payment of 120.00 arrived in your account.", "Mail", 180),
            message("m6", "Sam", "Running late", "Stuck on the train, start without me.", "Chat", 240),
        )

    private fun message(
        id: String,
        title: String,
        subtitle: String,
        body: String,
        group: String,
        minutesAgo: Long,
    ): Item =
        fakeItem(
            id = id,
            sourceId = "notifications",
            title = title,
            groupKey = group.lowercase(),
            timeEpochMillis = ScreenshotFixtures.FIXED_NOW_EPOCH_MILLIS - minutesAgo * MILLIS_PER_MINUTE,
        ).copy(subtitle = subtitle, body = body, groupLabel = group, icon = ItemImageHandle(group.lowercase()))
}

/** Renders [content] on the theme's surface colour (the screenshot backdrop's wallpaper would hurt contrast). */
internal fun ComposeContentTestRule.renderExpression(content: @Composable () -> Unit) {
    setContent {
        ScreenshotBackdrop {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                content()
            }
        }
    }
    captureScreen()
}
