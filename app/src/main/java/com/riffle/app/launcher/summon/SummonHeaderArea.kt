package com.riffle.app.launcher.summon

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.HomeWidgetPlaceholder
import com.riffle.app.launcher.LABEL_TEXT_SHADOW
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.glance.GlanceWidget
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.summon.SummonHeader
import kotlinx.coroutines.delay
import java.util.Date

private val WIDGET_HEADER_HEIGHT: Dp = 180.dp
private const val MILLIS_PER_MINUTE = 60_000L

/**
 * What sits at the top of Summon's Home while nothing is summoned: a clock, a clock with the date, one of Riffle's
 * glance widgets, a widget already placed on Home (drawn again here, live), or nothing. A Home widget that has since
 * been removed falls back to the clock with the date, so the area is never silently empty.
 */
@Composable
internal fun SummonHeaderArea(
    header: SummonHeader,
    homeLayout: HomeLayout,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: () -> Long = System::currentTimeMillis,
) {
    when (header) {
        SummonHeader.None -> Unit
        SummonHeader.Clock -> SummonClock(showDate = false, nowMillis = nowMillis, modifier = modifier)
        SummonHeader.ClockAndDate -> SummonClock(showDate = true, nowMillis = nowMillis, modifier = modifier)
        is SummonHeader.Glance ->
            Box(modifier.fillMaxWidth().height(WIDGET_HEADER_HEIGHT)) {
                GlanceWidget(kind = header.kind, span = header.kind.defaultSpan, interactive = { true })
            }
        is SummonHeader.HomeWidget -> {
            val widget = homeLayout.widgetById(header.itemId.value)
            if (widget == null) {
                SummonClock(showDate = true, nowMillis = nowMillis, modifier = modifier)
            } else {
                Box(modifier.fillMaxWidth().height(WIDGET_HEADER_HEIGHT)) {
                    HomeWidgetPlaceholder(
                        widget = widget,
                        isEditing = false,
                        onAction = onAction,
                        widgetViewFactory = widgetViewFactory,
                    )
                }
            }
        }
    }
}

private fun HomeLayout.widgetById(id: String): WidgetItem? =
    pages.asSequence().flatMap { it.items }.filterIsInstance<WidgetItem>().firstOrNull { it.id.value == id }

@Composable
private fun SummonClock(
    showDate: Boolean,
    nowMillis: () -> Long,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_MINUTE - now % MILLIS_PER_MINUTE)
            now = nowMillis()
        }
    }
    val date = Date(now)
    Column(modifier) {
        Text(
            DateFormat.getTimeFormat(context).format(date),
            style = MaterialTheme.typography.displayLarge.copy(shadow = LABEL_TEXT_SHADOW),
            fontWeight = FontWeight.Light,
            color = Color.White,
        )
        if (showDate) {
            Text(
                DateFormat.format(
                    DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMM"),
                    date,
                ).toString(),
                style = MaterialTheme.typography.titleMedium.copy(shadow = LABEL_TEXT_SHADOW),
                color = Color.White,
            )
        }
    }
}
