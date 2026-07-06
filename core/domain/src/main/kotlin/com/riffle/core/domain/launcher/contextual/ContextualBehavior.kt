package com.riffle.core.domain.launcher.contextual

import com.riffle.core.domain.launcher.cards.LauncherCardKind
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind

data class ContextualSettings(
    val enabled: Boolean = false,
)

data class ContextualSignalPlanInput(
    val personalInstalledAppCount: Int = 0,
    val workInstalledAppCount: Int = 0,
    val notificationGroupCount: Int = 0,
    val notificationCount: Int = 0,
    val isDayStart: Boolean = false,
) {
    init {
        require(personalInstalledAppCount >= 0) { "Personal installed app count must not be negative." }
        require(workInstalledAppCount >= 0) { "Work installed app count must not be negative." }
        require(notificationGroupCount >= 0) { "Notification group count must not be negative." }
        require(notificationCount >= 0) { "Notification count must not be negative." }
    }
}

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

object ContextualSignalPlanner {
    fun plan(input: ContextualSignalPlanInput = ContextualSignalPlanInput()): Set<ContextualSignal> =
        ContextualSignal.entries
            .filter { signal -> signal.matches(input) }
            .toSet()
}

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

private fun ContextualSignal.matches(input: ContextualSignalPlanInput): Boolean =
    when (this) {
        ContextualSignal.DAY_START -> input.isDayStart
        ContextualSignal.WORK_PROFILE_ACTIVE -> input.workInstalledAppCount > 0
        ContextualSignal.PERSONAL_PROFILE_ACTIVE -> input.personalInstalledAppCount > 0
        ContextualSignal.APP_ACTIVITY -> false
        ContextualSignal.NOTIFICATION_ACTIVITY -> input.notificationGroupCount > 0 || input.notificationCount > 0
    }
