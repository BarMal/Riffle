package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppProfileId

/** Deterministically combines durable pin intent with live notification/media stage content. */
class AppStagePlanner {
    fun reconcile(
        identitySnapshot: AppStageIdentitySnapshot,
        contentSnapshot: AppStageContentSnapshot = AppStageContentSnapshot(),
        preferences: AppStagePreferences = AppStagePreferences(),
        previous: AppStageSnapshot? = null,
    ): AppStageSnapshot {
        val installedIds = identitySnapshot.installedStageIds.toSet()
        val profileStates = identitySnapshot.profileStates
        val pins = validPins(preferences, installedIds, profileStates)
        val content = dynamicContent(contentSnapshot, installedIds, profileStates)
        val requestedSelection = preferences.selectedStageId
        val retainedId =
            retainedEmptyDynamicId(requestedSelection, pins, installedIds, profileStates, previous)
        val stages = buildStages(pins, content, profileStates, retainedId).sortedWith(stageOrder(pins))
        return AppStageSnapshot(
            stages = stages,
            preferences =
                AppStagePreferences(
                    pinnedStageIds = pins,
                    selectedStageId = selectedStageId(requestedSelection, stages),
                ),
        )
    }
}

private fun validPins(
    preferences: AppStagePreferences,
    installedIds: Set<AppStageId>,
    profileStates: Map<AppProfileId, AppStageProfileState>,
): List<AppStageId> =
    preferences.pinnedStageIds.distinct().filter { id ->
        id in installedIds && profileStates[id.profileId] != AppStageProfileState.REMOVED
    }

private fun dynamicContent(
    snapshot: AppStageContentSnapshot,
    installedIds: Set<AppStageId>,
    profileStates: Map<AppProfileId, AppStageProfileState>,
): Map<AppStageId, List<AppStageContent>> =
    normalize(snapshot.content)
        .filter { content ->
            content.stageId in installedIds &&
                profileStates[content.stageId.profileId] !in
                setOf(AppStageProfileState.LOCKED, AppStageProfileState.REMOVED)
        }
        .groupBy(AppStageContent::stageId)
        .mapValues { (_, content) -> content.sortedWith(contentOrder) }

private fun retainedEmptyDynamicId(
    selection: AppStageId?,
    pinnedIds: List<AppStageId>,
    installedIds: Set<AppStageId>,
    profileStates: Map<AppProfileId, AppStageProfileState>,
    previous: AppStageSnapshot?,
): AppStageId? =
    selection?.takeIf { selected ->
        selected == previous?.preferences?.selectedStageId &&
            selected !in pinnedIds &&
            selected in installedIds &&
            profileStates[selected.profileId] != AppStageProfileState.REMOVED &&
            previous.stages.any { it.id == selected && !it.isPinned }
    }

private fun buildStages(
    pinnedIds: List<AppStageId>,
    dynamicContent: Map<AppStageId, List<AppStageContent>>,
    profileStates: Map<AppProfileId, AppStageProfileState>,
    retainedId: AppStageId?,
): List<AppStage> =
    (pinnedIds + dynamicContent.keys + listOfNotNull(retainedId)).distinct().mapNotNull { id ->
        stageFor(id, pinnedIds, dynamicContent, profileStates, id == retainedId)
    }

private fun stageFor(
    id: AppStageId,
    pinnedIds: List<AppStageId>,
    dynamicContent: Map<AppStageId, List<AppStageContent>>,
    profileStates: Map<AppProfileId, AppStageProfileState>,
    retainEmptyDynamic: Boolean,
): AppStage? {
    val content = dynamicContent[id].orEmpty()
    val pinned = id in pinnedIds
    return when (profileStates[id.profileId]) {
        AppStageProfileState.REMOVED -> null
        AppStageProfileState.LOCKED ->
            id.takeIf { pinned }?.let { stageId ->
                AppStage(stageId, setOf(AppStageOrigin.PINNED), AppStageLifecycle.PROFILE_LOCKED)
            }
        AppStageProfileState.AVAILABLE,
        null,
        -> activeStage(id, pinned, content, retainEmptyDynamic)
    }
}

private fun activeStage(
    id: AppStageId,
    pinned: Boolean,
    content: List<AppStageContent>,
    retainEmptyDynamic: Boolean,
): AppStage? =
    id.takeIf { pinned || content.isNotEmpty() || retainEmptyDynamic }?.let { stageId ->
        AppStage(
            id = stageId,
            origins = originsFor(pinned, content, retainEmptyDynamic),
            lifecycle = if (content.isEmpty()) AppStageLifecycle.EMPTY else AppStageLifecycle.ACTIVE,
            content = content,
        )
    }

private fun originsFor(
    pinned: Boolean,
    content: List<AppStageContent>,
    retainEmptyDynamic: Boolean,
): Set<AppStageOrigin> =
    buildSet {
        if (pinned) add(AppStageOrigin.PINNED)
        if (content.isNotEmpty() || retainEmptyDynamic) add(AppStageOrigin.DYNAMIC)
    }

private fun selectedStageId(selection: AppStageId?, stages: List<AppStage>): AppStageId? =
    selection.takeIf { selected -> stages.any { it.id == selected } } ?: stages.firstOrNull()?.id

private fun normalize(content: List<AppStageContent>): List<AppStageContent> =
    content.groupBy(AppStageContent::id).values.map { duplicates ->
        duplicates.maxWithOrNull(
            compareBy<AppStageContent> { it.meaningfulActivityAtEpochMillis }
                .thenBy { it.stageId.stableKey }
                .thenBy { it.kind.name },
        )!!
    }

private fun stageOrder(pinnedIds: List<AppStageId>): Comparator<AppStage> =
    compareBy<AppStage> { stage -> pinnedIds.indexOf(stage.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        .thenByDescending { stage ->
            stage.content.maxOfOrNull(AppStageContent::meaningfulActivityAtEpochMillis) ?: Long.MIN_VALUE
        }
        .thenBy { stage -> stage.id }

private val contentOrder: Comparator<AppStageContent> =
    compareByDescending<AppStageContent>(AppStageContent::meaningfulActivityAtEpochMillis)
        .thenBy { item -> item.id.value }
