package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity

/**
 * Framework-independent, transient representation of content shown on a Cards surface.
 *
 * [content] may contain sensitive source text, so callers must not serialize this model into home
 * layout, settings, backup, diagnostics, or another durable store.
 */
data class LauncherCard(
    val id: LauncherCardId,
    val sourceRef: LauncherCardSourceRef,
    val size: LauncherCardSize = LauncherCardSize(),
    val content: LauncherCardContent? = null,
    val state: LauncherCardState = LauncherCardState.READY,
    val privacy: LauncherCardPrivacy = LauncherCardPrivacy.VISIBLE,
    val chronology: LauncherCardChronology = LauncherCardChronology(),
    val dismissibility: LauncherCardDismissibility = LauncherCardDismissibility.NOT_DISMISSIBLE,
    val supportedActions: Set<LauncherCardAction> = emptySet(),
    val userIntent: LauncherCardUserIntent = LauncherCardUserIntent(),
) {
    val kind: LauncherCardKind
        get() = sourceRef.kind

    val supportsExpansion: Boolean
        get() = LauncherCardAction.EXPAND in supportedActions
}

@JvmInline
value class LauncherCardId(val value: String)

enum class LauncherCardKind {
    APP,
    WIDGET_PROVIDER,
    NOTIFICATION_GROUP,
    MEDIA,
    AGENDA,
    ALARM,
    TASK,
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

    data class Media(
        val sourceId: LauncherCardSourceId,
        val profileId: AppProfileId,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.MEDIA
    }

    data class Agenda(
        val sourceId: LauncherCardSourceId,
        val profileId: AppProfileId,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.AGENDA
    }

    data class Alarm(
        val sourceId: LauncherCardSourceId,
        val profileId: AppProfileId,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.ALARM
    }

    data class Task(
        val sourceId: LauncherCardSourceId,
        val profileId: AppProfileId,
    ) : LauncherCardSourceRef {
        override val kind: LauncherCardKind = LauncherCardKind.TASK
    }
}

@JvmInline
value class LauncherCardSourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Card source ids must not be blank." }
    }
}

/** Source payload for the current process only; it is intentionally independent of UI geometry. */
sealed interface LauncherCardContent {
    val title: String
    val subtitle: String?

    data class Text(
        override val title: String,
        override val subtitle: String? = null,
        val body: String? = null,
    ) : LauncherCardContent

    data class Progress(
        override val title: String,
        override val subtitle: String? = null,
        val current: Int,
        val maximum: Int,
    ) : LauncherCardContent {
        init {
            require(current >= 0) { "Card progress cannot be negative." }
            require(maximum > 0) { "Card progress maximum must be positive." }
            require(current <= maximum) { "Card progress cannot exceed its maximum." }
        }
    }
}

/** Current source/action state. These values are never a substitute for refreshing source truth. */
enum class LauncherCardState {
    LOADING,
    READY,
    EMPTY,
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    PROFILE_LOCKED,
    PROFILE_QUIET,
    REDACTED,
    STALE,
    ACTION_IN_PROGRESS,
    ACTION_FAILED,
    REMOVED,
}

enum class LauncherCardPrivacy {
    VISIBLE,
    REDACTED,
    HIDDEN,
}

data class LauncherCardChronology(
    val updatedAtEpochMillis: Long = 0L,
    val rankingScore: Int = 0,
) {
    init {
        require(updatedAtEpochMillis >= 0L) { "Card update timestamps cannot be negative." }
    }
}

enum class LauncherCardDismissibility {
    DISMISSIBLE,
    NOT_DISMISSIBLE,
}

/** Explicit actions a renderer may expose after its platform boundary confirms availability. */
enum class LauncherCardAction {
    OPEN,
    LAUNCH_APP,
    APP_SHORTCUT,
    APP_INFO,
    DISMISS,
    CLEAR_GROUP,
    PIN,
    UNPIN,
    EXPAND,
    COLLAPSE,
    PLAY,
    PAUSE,
    PREVIOUS,
    NEXT,
    ADD_WIDGET,
    BIND_WIDGET,
    CONFIGURE_WIDGET,
    REMOVE_WIDGET,
    OPEN_ALARM,
    COMPLETE_TASK,
}

/** Persistable user choices only; source text, artwork, and actions remain outside this value. */
data class LauncherCardUserIntent(
    val isPinned: Boolean = false,
    val isFavourite: Boolean = false,
)

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
