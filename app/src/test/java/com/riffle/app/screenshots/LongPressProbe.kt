package com.riffle.app.screenshots

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType

/** A haptics stand-in that counts the light "a menu opened" tick, which every long-press that opens a menu plays. */
class CountingHaptics : LauncherHaptics {
    var count = 0
        private set

    /** Which part of the launcher played the tick last, for a failure message. */
    var lastCaller = ""
        private set

    override fun longPress() {
        count++
        lastCaller =
            Throwable().stackTrace.filter { it.className.startsWith("com.riffle.app.launcher") }.take(4)
                .joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName }
    }
}

/** Simulates the system bars: a status bar of [topDp] and a navigation bar of [bottomDp], as sent. */
internal fun AndroidComposeTestRule<*, ComponentActivity>.setSystemBarInsets(
    topDp: Int,
    bottomDp: Int,
) {
    val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    val density = density.density
    val insets =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (topDp * density).toInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (bottomDp * density).toInt()))
            .setVisible(WindowInsetsCompat.Type.statusBars(), topDp > 0)
            .setVisible(WindowInsetsCompat.Type.navigationBars(), bottomDp > 0)
            .build()
    runOnUiThread { ViewCompat.dispatchApplyWindowInsets(content, insets) }
    waitForIdle()
}

/** What one probe point did. */
enum class ProbeOutcome { OPENED, DEAD, SKIPPED }

/** The result of long-pressing a grid of points across the whole window. */
class ProbeResult(
    val xs: List<Int>,
    val ys: List<Int>,
    val outcomes: Map<Pair<Int, Int>, ProbeOutcome>,
    /** Who opened the menu at each live point. */
    val callers: Map<Pair<Int, Int>, String> = emptyMap(),
) {
    val dead: List<Pair<Int, Int>> get() = outcomes.filterValues { it == ProbeOutcome.DEAD }.keys.toList()

    /** One character per point: `#` opened a menu, `.` was dead, `o` was left out (an item, an icon, the handle). */
    fun map(): String =
        ys.joinToString("\n") { y ->
            "%4d ".format(y) +
                xs.joinToString("") { x ->
                    when (outcomes[x to y]) {
                        ProbeOutcome.OPENED -> "#"
                        ProbeOutcome.DEAD -> "."
                        else -> "o"
                    }
                }
        } + "\n     x: " + xs.joinToString(" ")

    /** Which part of the launcher opened the menu, with a few of the points it opened it at. */
    fun callerSummary(): String =
        callers.entries.groupBy({ it.value }, { it.key }).entries
            .joinToString("\n") { (caller, points) -> "$caller: ${points.size} points, e.g. ${points.take(4)}" }

    fun deadRegions(): String =
        if (dead.isEmpty()) {
            "no dead point"
        } else {
            "${dead.size} dead of ${outcomes.size} points\n" + map()
        }
}

/** The labels of the long-click actions of a surface's own background, which are not items. */
internal val backgroundLongClickLabels =
    setOf("Show Home actions", "Show panel actions", "Show options for the Library", "Show as")

/** The labels of the click actions of a surface's own scrim, which is not an item either. */
internal val backgroundClickLabels = setOf("Close Library")

/**
 * The rectangles of what is an item rather than empty space: anything with a click or long-click action that is not a
 * surface's own background (those are named by [backgroundLongClickLabels] and [backgroundClickLabels]) and is less
 * than half the window tall, plus the nodes tagged [tags] (the handle).
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.itemRects(tags: List<String> = emptyList()): List<Rect> {
    val root = mainRoot().fetchSemanticsNode().boundsInRoot
    val clickable =
        SemanticsMatcher("has a click or long click action") { node ->
            node.config.contains(SemanticsActions.OnClick) || node.config.contains(SemanticsActions.OnLongClick)
        }
    val items =
        onAllNodes(clickable, useUnmergedTree = true).fetchSemanticsNodes().filter { node ->
            val bounds = node.boundsInRoot
            val label = node.config.getOrNull(SemanticsActions.OnLongClick)?.label
            label !in backgroundLongClickLabels &&
                node.config.getOrNull(SemanticsActions.OnClick)?.label !in backgroundClickLabels &&
                bounds.height < root.height * 0.5f
        }.map { it.boundsInRoot }
    val tagged =
        tags.flatMap { tag ->
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        }
    return items + tagged
}

/**
 * Long-presses a grid of points across the whole window (every [stepDp] dp, plus a point 2 dp inside each edge so the
 * margins and the system bars are probed) and records which ones played the menu's haptic tick. Points inside [skip]
 * are left out.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.probeLongPress(
    haptics: CountingHaptics,
    skip: List<Rect>,
    stepDp: Int = 48,
    onlyInside: Rect? = null,
): ProbeResult {
    val root = mainRoot().fetchSemanticsNode().boundsInRoot
    val density = density.density
    val width = (root.width / density).toInt()
    val height = (root.height / density).toInt()

    fun axis(extent: Int): List<Int> =
        (listOf(2) + (stepDp / 2 + 1 until extent - 2 step stepDp) + (extent - 3)).distinct()
    val xs = axis(width)
    val ys = axis(height)
    val outcomes = LinkedHashMap<Pair<Int, Int>, ProbeOutcome>()
    val callers = LinkedHashMap<Pair<Int, Int>, String>()
    for (y in ys) {
        for (x in xs) {
            val point = Offset(x * density, y * density)
            if (skip.any { it.contains(point) } || (onlyInside != null && !onlyInside.contains(point))) {
                outcomes[x to y] = ProbeOutcome.SKIPPED
                continue
            }
            val before = haptics.count
            mainRoot().performTouchInput { longClick(point) }
            val opened = haptics.count > before
            outcomes[x to y] = if (opened) ProbeOutcome.OPENED else ProbeOutcome.DEAD
            if (opened) callers[x to y] = haptics.lastCaller
        }
    }
    return ProbeResult(xs, ys, outcomes, callers)
}

/**
 * Long-presses the Dock's body (the glass the icons stand on) between its icons, at both ends and just inside its
 * edges: the midpoint of every gap between two neighbouring icons, and a point [insetDp] in from each end of the body,
 * on the line through the icons' centres, and a point at each side of the line.
 */
@Suppress("CyclomaticComplexMethod")
internal fun AndroidComposeTestRule<*, ComponentActivity>.probeDockBody(
    haptics: CountingHaptics,
    bodyTag: String,
    insetDp: Int = 3,
): ProbeResult {
    val density = density.density
    val body = onAllNodesWithTag(bodyTag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
    val icons =
        itemRects().filter { rect -> body.contains(rect.center) }
    val horizontal = body.width >= body.height
    val sorted = icons.sortedBy { if (horizontal) it.center.x else it.center.y }
    val points = mutableListOf<Offset>()
    val mid = body.center

    fun at(
        main: Float,
        cross: Float,
    ) = if (horizontal) Offset(main, cross) else Offset(cross, main)

    val bodyStart = if (horizontal) body.left else body.top
    val bodyEnd = if (horizontal) body.right else body.bottom
    val cross = if (horizontal) mid.y else mid.x
    points += at(bodyStart + insetDp * density, cross)
    points += at(bodyEnd - insetDp * density, cross)
    sorted.zipWithNext().forEach { (a, b) ->
        val aEnd = if (horizontal) a.right else a.bottom
        val bStart = if (horizontal) b.left else b.top
        if (bStart - aEnd > 4 * density) points += at((aEnd + bStart) / 2f, cross)
    }
    // Above and below the icons' line (the body's own padding), at each icon's main-axis centre.
    sorted.forEach { icon ->
        val main = if (horizontal) icon.center.x else icon.center.y
        val crossStart = if (horizontal) body.top else body.left
        val crossEnd = if (horizontal) body.bottom else body.right
        points += at(main, crossStart + insetDp * density)
        points += at(main, crossEnd - insetDp * density)
    }
    val outcomes = LinkedHashMap<Pair<Int, Int>, ProbeOutcome>()
    points.forEach { point ->
        val before = haptics.count
        mainRoot().performTouchInput { longClick(point) }
        outcomes[(point.x / density).toInt() to (point.y / density).toInt()] =
            if (haptics.count > before) ProbeOutcome.OPENED else ProbeOutcome.DEAD
    }
    return ProbeResult(
        xs = outcomes.keys.map { it.first }.distinct().sorted(),
        ys = outcomes.keys.map { it.second }.distinct().sorted(),
        outcomes = outcomes,
    )
}

/** The window's own root: once a menu has opened there is a second root (its popup), which `onRoot` refuses. */
private fun AndroidComposeTestRule<*, ComponentActivity>.mainRoot() = onAllNodes(isRoot()).onFirst()

/** The fixture shell with [page] as Home's only page, selected. */
internal fun LauncherShellState.withOnlyPage(page: LauncherPage): LauncherShellState {
    val layout = homeLayout.copy(pages = listOf(page), selectedPageId = page.id)
    return copy(
        homeLayout = layout,
        homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
    )
}

/** The fixture shell with its second page selected (the fixture's second page is empty). */
internal fun LauncherShellState.onTheSecondPage(): LauncherShellState {
    val layout = homeLayout.copy(selectedPageId = homeLayout.pages[1].id)
    return copy(
        homeLayout = layout,
        homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
    )
}

/** The fixture's first page made a generated page with "More apps" under it. */
internal fun LauncherShellState.withAGeneratedPage(): LauncherShellState =
    withOnlyPage(
        homeLayout.selectedPage.copy(
            type = LauncherPageType.Generated(GeneratedLauncherPageKind.APP),
            items = emptyList(),
            generatedContentOverflowCount = 3,
        ),
    )
