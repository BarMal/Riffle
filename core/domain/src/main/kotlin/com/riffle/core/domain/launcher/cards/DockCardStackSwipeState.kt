package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey

enum class DockCardStackContent {
    APPS,
    NOTIFICATIONS,
}

enum class DockCardStackSwipeDirection(
    internal val indexDelta: Int,
) {
    NEXT(1),
    PREVIOUS(-1),
}

data class DockCardStackSwipeState(
    val content: DockCardStackContent,
    val direction: DockCardStackSwipeDirection,
    val outgoingCardIndex: Int,
    val incomingCardIndex: Int,
    val newBackCardIndex: Int?,
) {
    init {
        require(outgoingCardIndex >= 0) { "Outgoing card index must not be negative." }
        require(incomingCardIndex >= 0) { "Incoming card index must not be negative." }
        require(outgoingCardIndex != incomingCardIndex) { "Outgoing and incoming cards must differ." }
        require(newBackCardIndex == null || newBackCardIndex >= 0) { "Back card index must not be negative." }
        require(newBackCardIndex !in setOf(outgoingCardIndex, incomingCardIndex)) {
            "Back card must differ from outgoing and incoming cards."
        }
    }

    companion object {
        fun create(
            cardCount: Int,
            activeCardIndex: Int,
            direction: DockCardStackSwipeDirection,
            content: DockCardStackContent,
            wrapAround: Boolean = false,
        ): DockCardStackSwipeState? {
            require(cardCount >= 0) { "Card count must not be negative." }
            if (cardCount > 0) {
                require(activeCardIndex in 0 until cardCount) { "Active card index must be in the stack." }
            }
            if (cardCount == 0 || (wrapAround && cardCount == 1)) return null

            val incomingCardIndex =
                (activeCardIndex + direction.indexDelta).let { candidate ->
                    if (wrapAround) candidate.floorMod(cardCount) else candidate.takeIf { it in 0 until cardCount }
                }
            return incomingCardIndex?.let { incoming ->
                DockCardStackSwipeState(
                    content = content,
                    direction = direction,
                    outgoingCardIndex = activeCardIndex,
                    incomingCardIndex = incoming,
                    newBackCardIndex =
                        (incoming + direction.indexDelta)
                            .let { candidate ->
                                if (wrapAround) {
                                    candidate.floorMod(cardCount)
                                } else {
                                    candidate.takeIf { it in 0 until cardCount }
                                }
                            }
                            ?.takeUnless { it == activeCardIndex },
                )
            }
        }
    }
}

/** Stable Hybrid Dock selection that survives reorder and notification refresh by identity. */
data class HybridDockFocus(
    val appIdentity: AppIdentity,
    val notificationKey: LauncherNotificationKey? = null,
    val appPosition: Int = 0,
    val notificationPosition: Int = 0,
) {
    init {
        require(appPosition >= 0) { "App position must not be negative." }
        require(notificationPosition >= 0) { "Notification position must not be negative." }
    }
}

/**
 * Reconciles persisted Hybrid Dock focus with currently eligible Dock apps and their notifications.
 *
 * When an identity disappears, the persisted position selects the nearest remaining item. The
 * focused notification follows the same rule, while an app without notifications retains its app card.
 */
fun reconcileHybridDockFocus(
    focus: HybridDockFocus?,
    eligibleAppIdentities: List<AppIdentity>,
    notificationKeysByApp: Map<AppIdentity, List<LauncherNotificationKey>>,
): HybridDockFocus? {
    require(eligibleAppIdentities.distinct().size == eligibleAppIdentities.size) {
        "Eligible Hybrid Dock apps must be unique."
    }
    if (eligibleAppIdentities.isEmpty()) return null

    val selectedAppPosition =
        focus
            ?.appIdentity
            ?.let(eligibleAppIdentities::indexOf)
            ?.takeIf { it >= 0 }
            ?: focus?.appPosition?.coerceAtMost(eligibleAppIdentities.lastIndex)
            ?: 0
    val appIdentity = eligibleAppIdentities[selectedAppPosition]
    val notificationKeys = notificationKeysByApp[appIdentity].orEmpty()
    val selectedNotificationPosition =
        if (notificationKeys.isEmpty()) {
            0
        } else {
            focus
                ?.takeIf { it.appIdentity == appIdentity }
                ?.notificationKey
                ?.let(notificationKeys::indexOf)
                ?.takeIf { it >= 0 }
                ?: focus?.notificationPosition?.coerceAtMost(notificationKeys.lastIndex)
                ?: 0
        }

    return HybridDockFocus(
        appIdentity = appIdentity,
        notificationKey = notificationKeys.getOrNull(selectedNotificationPosition),
        appPosition = selectedAppPosition,
        notificationPosition = selectedNotificationPosition,
    )
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
