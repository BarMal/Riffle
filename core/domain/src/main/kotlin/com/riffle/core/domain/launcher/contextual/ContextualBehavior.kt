package com.riffle.core.domain.launcher.contextual

import com.riffle.core.domain.launcher.cards.LauncherCardKind
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind

data class ContextualSettings(
    val enabled: Boolean = false,
)

enum class ContextualSignal(
    internal val pageKind: GeneratedLauncherPageKind?,
    internal val cardKind: LauncherCardKind?,
) {
    DAY_START(
        pageKind = GeneratedLauncherPageKind.TODAY,
        cardKind = null,
    ),
    WORK_PROFILE_ACTIVE(
        pageKind = GeneratedLauncherPageKind.WORK,
        cardKind = null,
    ),
    PERSONAL_PROFILE_ACTIVE(
        pageKind = GeneratedLauncherPageKind.PERSONAL,
        cardKind = null,
    ),
    APP_ACTIVITY(
        pageKind = GeneratedLauncherPageKind.FREQUENTLY_USED,
        cardKind = LauncherCardKind.APP,
    ),
    NOTIFICATION_ACTIVITY(
        pageKind = GeneratedLauncherPageKind.NOTIFICATION_CARDS,
        cardKind = LauncherCardKind.NOTIFICATION_GROUP,
    ),
}

data class ContextualSelection(
    val pageKinds: List<GeneratedLauncherPageKind> = emptyList(),
    val cardKinds: List<LauncherCardKind> = emptyList(),
)

object ContextualBehaviorSelector {
    fun select(
        settings: ContextualSettings,
        signals: Set<ContextualSignal>,
    ): ContextualSelection {
        if (!settings.enabled) {
            return ContextualSelection()
        }

        return ContextualSelection(
            pageKinds = signals.mapInStableSignalOrder { signal -> signal.pageKind },
            cardKinds = signals.mapInStableSignalOrder { signal -> signal.cardKind },
        )
    }
}

private fun <T> Set<ContextualSignal>.mapInStableSignalOrder(transform: (ContextualSignal) -> T?): List<T> =
    ContextualSignal.entries
        .asSequence()
        .filter { signal -> signal in this }
        .mapNotNull(transform)
        .distinct()
        .toList()
