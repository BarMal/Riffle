package com.riffle.core.domain.launcher.cards

/**
 * A bounded, deduplicated Cards snapshot ready for any presentation surface.
 *
 * The collection owns no Compose state or persisted source payload. Consumers should retain focus
 * by [LauncherCard.id] while a new collection replaces an older source snapshot.
 */
data class LauncherCardCollection(
    val cards: List<LauncherCard>,
    val omittedCardCount: Int = 0,
) {
    init {
        require(cards.map(LauncherCard::id).distinct().size == cards.size) {
            "Launcher card collections must not contain duplicate ids."
        }
        require(omittedCardCount >= 0) { "Omitted card counts cannot be negative." }
    }
}

/**
 * Applies a stable diff policy to source snapshots before they reach overview, chapter, or dock UI.
 *
 * Duplicate IDs resolve to the newest source update, then its ranking score and source state. Tied
 * but divergent snapshots are rejected instead of allowing input order to churn a card's content.
 * Removed and hidden cards are omitted, while other unavailable states remain renderable for
 * recovery copy.
 */
class LauncherCardCollectionPlanner {
    fun plan(
        cards: List<LauncherCard>,
        maxCards: Int = DEFAULT_MAX_LAUNCHER_CARDS,
    ): LauncherCardCollection {
        require(maxCards >= 0) { "Maximum card count cannot be negative." }

        val candidates =
            cards
                .groupBy(LauncherCard::id)
                .values
                .map(::resolveDuplicate)
                .filterNot { card ->
                    card.state == LauncherCardState.REMOVED || card.privacy == LauncherCardPrivacy.HIDDEN
                }
                .sortedWith(displayOrder)

        return LauncherCardCollection(
            cards = candidates.take(maxCards),
            omittedCardCount = (candidates.size - maxCards).coerceAtLeast(0),
        )
    }

    private companion object {
        fun resolveDuplicate(duplicates: List<LauncherCard>): LauncherCard {
            val resolved = duplicates.maxWithOrNull(duplicateResolutionOrder)!!
            val tiedSnapshots = duplicates.filter { card -> duplicateResolutionOrder.compare(card, resolved) == 0 }
            require(tiedSnapshots.distinct().size == 1) {
                "Duplicate card snapshots must have a deterministic resolution."
            }
            return resolved
        }

        val duplicateResolutionOrder: Comparator<LauncherCard> =
            compareBy<LauncherCard> { card -> card.chronology.updatedAtEpochMillis }
                .thenBy { card -> card.chronology.rankingScore }
                .thenBy { card -> card.state.duplicateResolutionRank }
                .thenBy { card -> card.sourceRef.stableIdentity }

        val displayOrder: Comparator<LauncherCard> =
            compareByDescending<LauncherCard> { card -> card.chronology.rankingScore }
                .thenByDescending { card -> card.chronology.updatedAtEpochMillis }
                .thenBy { card -> card.id.value }
    }
}

const val DEFAULT_MAX_LAUNCHER_CARDS = 50

private val LauncherCardState.duplicateResolutionRank: Int
    get() =
        when (this) {
            LauncherCardState.READY -> 11
            LauncherCardState.ACTION_IN_PROGRESS -> 10
            LauncherCardState.STALE -> 9
            LauncherCardState.REDACTED -> 8
            LauncherCardState.LOADING -> 7
            LauncherCardState.EMPTY -> 6
            LauncherCardState.PERMISSION_REQUIRED -> 5
            LauncherCardState.PROFILE_LOCKED -> 4
            LauncherCardState.PROFILE_QUIET -> 3
            LauncherCardState.UNAVAILABLE -> 2
            LauncherCardState.ACTION_FAILED -> 1
            LauncherCardState.REMOVED -> 0
        }

private val LauncherCardSourceRef.stableIdentity: String
    get() =
        when (this) {
            is LauncherCardSourceRef.App ->
                "app:${identity.profile.id.value}:${identity.packageName.value}:${identity.activityName.value}"

            is LauncherCardSourceRef.WidgetProvider ->
                "widget:${identity.profile.id.value}:${identity.packageName.value}:${identity.className.value}"

            is LauncherCardSourceRef.AppNotificationGroup ->
                "notification:${key.profileId.value}:${key.packageName.value}"

            is LauncherCardSourceRef.Media -> "media:${profileId.value}:${sourceId.value}"
            is LauncherCardSourceRef.Agenda -> "agenda:${profileId.value}:${sourceId.value}"
            is LauncherCardSourceRef.Alarm -> "alarm:${profileId.value}:${sourceId.value}"
            is LauncherCardSourceRef.Task -> "task:${profileId.value}:${sourceId.value}"
        }
