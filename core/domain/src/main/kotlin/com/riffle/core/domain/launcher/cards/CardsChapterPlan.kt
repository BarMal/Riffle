package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup

/**
 * Stable identifiers for the Cards navigator.
 *
 * App chapters deliberately include the profile identifier. An application installed in Personal
 * and Work must therefore produce two independent chapters even when their package names match.
 */
sealed interface CardsChapterId {
    data object Overview : CardsChapterId

    data class App(
        val packageName: AppPackageName,
        val profileId: AppProfileId,
    ) : CardsChapterId
}

/** A navigator-ready Cards snapshot built from one notification snapshot and stored pin intent. */
data class CardsChapterPlan(
    val chapters: List<CardsChapter>,
) {
    init {
        require(chapters.firstOrNull()?.id == CardsChapterId.Overview) {
            "Cards chapter plans must begin with Overview."
        }
        require(chapters.map(CardsChapter::id).distinct().size == chapters.size) {
            "Cards chapter plans must not contain duplicate identities."
        }
    }

    val chapterIds: List<CardsChapterId>
        get() = chapters.map(CardsChapter::id)

    /**
     * The canonical Overview order for active app chapters.
     *
     * Pinned chapters retain their user-defined order, followed by transient chapters ordered by
     * recency and stable identity. Empty pinned chapters remain navigator destinations but do not
     * duplicate content in Overview.
     */
    val activeAppChapters: List<CardsChapter.App>
        get() = chapters.filterIsInstance<CardsChapter.App>().filter { chapter -> chapter.notificationGroup != null }
}

/**
 * Persistable user intent for Cards chapters.
 *
 * It intentionally excludes notification groups and their content. Those source snapshots are
 * transient and are supplied to [CardsChapterPlanner] each time a plan is refreshed.
 */
data class CardsChapterPreferences(
    val pinnedChapterIds: List<CardsChapterId.App> = emptyList(),
    val selectedChapterId: CardsChapterId = CardsChapterId.Overview,
) {
    init {
        require(pinnedChapterIds.distinct().size == pinnedChapterIds.size) {
            "Pinned Cards chapters must have unique identities."
        }
    }

    fun pin(chapterId: CardsChapterId.App): CardsChapterPreferences =
        if (chapterId in pinnedChapterIds) this else copy(pinnedChapterIds = pinnedChapterIds + chapterId)

    fun unpin(chapterId: CardsChapterId.App): CardsChapterPreferences {
        return copy(pinnedChapterIds = pinnedChapterIds - chapterId)
    }

    fun movePinnedChapter(
        chapterId: CardsChapterId.App,
        targetIndex: Int,
    ): CardsChapterPreferences {
        val sourceIndex = pinnedChapterIds.indexOf(chapterId)
        if (sourceIndex < 0) return this

        val reordered = pinnedChapterIds.toMutableList()
        reordered.removeAt(sourceIndex)
        reordered.add(targetIndex.coerceIn(0, reordered.size), chapterId)
        return copy(pinnedChapterIds = reordered)
    }

    fun select(chapterId: CardsChapterId): CardsChapterPreferences = copy(selectedChapterId = chapterId)
}

/** A refreshed navigator snapshot paired with the user intent needed to recreate it. */
data class CardsChapterState(
    val plan: CardsChapterPlan,
    val preferences: CardsChapterPreferences,
) {
    init {
        require(preferences.selectedChapterId in plan.chapterIds) {
            "Cards chapter state must select an available chapter."
        }
    }

    val selectedChapter: CardsChapter
        get() = plan.chapters.first { chapter -> chapter.id == preferences.selectedChapterId }
}

sealed interface CardsChapter {
    val id: CardsChapterId

    data object Overview : CardsChapter {
        override val id: CardsChapterId = CardsChapterId.Overview
    }

    data class App(
        override val id: CardsChapterId.App,
        val notificationGroup: AppNotificationGroup?,
        val isPinned: Boolean,
    ) : CardsChapter
}

/**
 * Plans Cards chapters without persisting notification content.
 *
 * Pins are supplied in the user's stored order. A pinned chapter remains in the result without an
 * active notification group; non-pinned chapters exist only while their group is active.
 */
class CardsChapterPlanner {
    fun state(
        notificationGroups: List<AppNotificationGroup>,
        preferences: CardsChapterPreferences = CardsChapterPreferences(),
    ): CardsChapterState {
        val plan = plan(notificationGroups, preferences.pinnedChapterIds)
        val selectedChapterId = reconcileSelectedChapter(preferences.selectedChapterId, plan)
        return CardsChapterState(
            plan = plan,
            preferences = preferences.select(selectedChapterId),
        )
    }

    fun plan(
        notificationGroups: List<AppNotificationGroup>,
        pinnedChapterIds: List<CardsChapterId.App> = emptyList(),
    ): CardsChapterPlan {
        val groupsById =
            notificationGroups
                .map { group -> group.chapterId to group }
                .toMap()
        val pinnedIds = pinnedChapterIds.distinct()
        val pinnedChapters =
            pinnedIds.map { id ->
                CardsChapter.App(
                    id = id,
                    notificationGroup = groupsById[id],
                    isPinned = true,
                )
            }
        val transientChapters =
            groupsById
                .asSequence()
                .filter { (id, _) -> id !in pinnedIds }
                .sortedWith(transientChapterOrder)
                .map { (id, group) ->
                    CardsChapter.App(
                        id = id,
                        notificationGroup = group,
                        isPinned = false,
                    )
                }.toList()

        return CardsChapterPlan(chapters = listOf(CardsChapter.Overview) + pinnedChapters + transientChapters)
    }

    /**
     * Keeps the selected chapter through refreshes when possible. If a transient chapter vanished,
     * Overview is the deterministic and always-available recovery destination.
     */
    fun reconcileSelectedChapter(
        selectedChapterId: CardsChapterId,
        plan: CardsChapterPlan,
    ): CardsChapterId = selectedChapterId.takeIf { id -> id in plan.chapterIds } ?: CardsChapterId.Overview

    private companion object {
        val transientChapterOrder: Comparator<Map.Entry<CardsChapterId.App, AppNotificationGroup>> =
            compareByDescending<Map.Entry<CardsChapterId.App, AppNotificationGroup>> { (_, group) ->
                group.latestPostedAtEpochMillis
            }.thenBy { (id, _) -> id.packageName.value }
                .thenBy { (id, _) -> id.profileId.value }
    }
}

private val AppNotificationGroup.chapterId: CardsChapterId.App
    get() = CardsChapterId.App(packageName = packageName, profileId = profileId)
