package com.riffle.app.launcher.glance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions

/** Dp per grid cell the picker's dimensions are written in; the span itself is what the picker and the add use. */
private const val CELL_DP = 80

/**
 * Riffle's glance widgets as picker entries, so the existing picker, drag preview, accessible placement and "no room"
 * behaviour apply unchanged. The span comes from [GlanceWidgetKind.defaultSpan] (`targetCellWidth/Height`), and the
 * identity is the reserved one, which the add flow recognises and never binds through the widget host.
 */
@Composable
internal fun rememberRiffleGlanceProviders(): List<InstalledWidgetProvider> {
    val appLabel = stringResource(R.string.glance_section_riffle)
    val labels = GlanceWidgets.catalogue.map { stringResource(it.labelRes()) }
    val descriptions = GlanceWidgets.catalogue.map { stringResource(it.descriptionRes()) }
    return remember(appLabel, labels, descriptions) {
        GlanceWidgets.catalogue.mapIndexed { index, kind ->
            InstalledWidgetProvider(
                identity = GlanceWidgets.identity(kind),
                label = labels[index],
                appLabel = appLabel,
                description = descriptions[index],
                dimensions =
                    WidgetProviderDimensions(
                        minWidthDp = kind.defaultSpan.columns * CELL_DP,
                        minHeightDp = kind.defaultSpan.rows * CELL_DP,
                        minResizeWidthDp = kind.minSpan.columns * CELL_DP,
                        minResizeHeightDp = kind.minSpan.rows * CELL_DP,
                        maxResizeWidthDp = kind.maxSpan.columns * CELL_DP,
                        maxResizeHeightDp = kind.maxSpan.rows * CELL_DP,
                        targetCellWidth = kind.defaultSpan.columns,
                        targetCellHeight = kind.defaultSpan.rows,
                    ),
                supportsHorizontalResize = true,
                supportsVerticalResize = true,
            )
        }
    }
}

/**
 * A small static preview of [kind] for the picker, drawn from the same glass as the widget with sample text. It is not
 * content: nothing is read, and a screen reader skips it (the tile's label and description speak for it).
 */
@Composable
internal fun GlanceWidgetPreview(
    kind: GlanceWidgetKind,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) =
        when (kind) {
            GlanceWidgetKind.NOW_PLAYING ->
                stringResource(R.string.glance_preview_track) to stringResource(R.string.glance_preview_artist)
            GlanceWidgetKind.NEXT_EVENT ->
                stringResource(R.string.glance_preview_event) to stringResource(R.string.glance_preview_event_time)
            GlanceWidgetKind.TOP_STORY ->
                stringResource(R.string.glance_preview_story) to stringResource(R.string.glance_preview_feed)
        }
    GlassSurface(
        modifier = modifier.clearAndSetSemantics {},
        weight = GlassWeight.Regular,
        shape = LocalLauncherCardShape.current,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(RiffleSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            if (kind != GlanceWidgetKind.NEXT_EVENT) {
                GlassSurface(
                    modifier = Modifier.size(RiffleSpacing.xxxl),
                    weight = GlassWeight.Thin,
                    shape = RiffleShapes.small,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
