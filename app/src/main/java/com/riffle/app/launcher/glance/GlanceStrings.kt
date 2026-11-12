package com.riffle.app.launcher.glance

import androidx.annotation.StringRes
import com.riffle.app.R
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind

@StringRes
internal fun GlanceWidgetKind.labelRes(): Int =
    when (this) {
        GlanceWidgetKind.NOW_PLAYING -> R.string.glance_now_playing_label
        GlanceWidgetKind.NEXT_EVENT -> R.string.glance_next_event_label
        GlanceWidgetKind.TOP_STORY -> R.string.glance_top_story_label
    }

@StringRes
internal fun GlanceWidgetKind.descriptionRes(): Int =
    when (this) {
        GlanceWidgetKind.NOW_PLAYING -> R.string.glance_now_playing_description
        GlanceWidgetKind.NEXT_EVENT -> R.string.glance_next_event_description
        GlanceWidgetKind.TOP_STORY -> R.string.glance_top_story_description
    }

@StringRes
internal fun GlanceWidgetKind.emptyRes(): Int =
    when (this) {
        GlanceWidgetKind.NOW_PLAYING -> R.string.glance_now_playing_empty
        GlanceWidgetKind.NEXT_EVENT -> R.string.glance_next_event_empty
        GlanceWidgetKind.TOP_STORY -> R.string.glance_top_story_empty
    }
