package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

data class LauncherCard(
    val id: LauncherCardId,
    val sourceRef: LauncherCardSourceRef,
    val size: LauncherCardSize = LauncherCardSize(),
) {
    val kind: LauncherCardKind
        get() = sourceRef.kind
}

@JvmInline
value class LauncherCardId(val value: String)

enum class LauncherCardKind {
    APP,
    WIDGET_PROVIDER,
    NOTIFICATION_GROUP,
}

sealed interface LauncherCardSourceRef {
    val kind: LauncherCardKind

    data class App(
        val identity: AppIdentity,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.APP
    }

    data class WidgetProvider(
        val identity: WidgetProviderIdentity,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.WIDGET_PROVIDER
    }

    data class AppNotificationGroup(
        val key: AppNotificationGroupKey,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.NOTIFICATION_GROUP
    }
}

data class LauncherCardSize(
    val columns: Int = DEFAULT_LAUNCHER_CARD_SPAN_COLUMNS,
    val rows: Int = DEFAULT_LAUNCHER_CARD_SPAN_ROWS,
) {
    init {
        require(columns in MIN_LAUNCHER_CARD_SPAN_COLUMNS..MAX_LAUNCHER_CARD_SPAN_COLUMNS) {
            "Card columns must be between $MIN_LAUNCHER_CARD_SPAN_COLUMNS and $MAX_LAUNCHER_CARD_SPAN_COLUMNS."
        }
        require(rows in MIN_LAUNCHER_CARD_SPAN_ROWS..MAX_LAUNCHER_CARD_SPAN_ROWS) {
            "Card rows must be between $MIN_LAUNCHER_CARD_SPAN_ROWS and $MAX_LAUNCHER_CARD_SPAN_ROWS."
        }
    }

    val cellCount: Int = columns * rows
}

const val DEFAULT_LAUNCHER_CARD_SPAN_COLUMNS = 1
const val DEFAULT_LAUNCHER_CARD_SPAN_ROWS = 1
const val MIN_LAUNCHER_CARD_SPAN_COLUMNS = 1
const val MIN_LAUNCHER_CARD_SPAN_ROWS = 1
const val MAX_LAUNCHER_CARD_SPAN_COLUMNS = 6
const val MAX_LAUNCHER_CARD_SPAN_ROWS = 6
