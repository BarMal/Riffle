package com.riffle.core.domain.launcher.cards

/** Deterministically combines durable pin intent with live notification/media stage content. */
class AppStagePlanner {
    fun reconcile(
        identitySnapshot: AppStageIdentitySnapshot,
        contentSnapshot: AppStageContentSnapshot = AppStageContentSnapshot(),
        preferences: AppStagePreferences = AppStagePreferences(),
        previous: AppStageSnapshot? = null,
    ): AppStageSnapshot {
        val installedIds = identitySnapshot.installedStageIds.toSet()
        val profileState = identitySnapshot.profileStates
        val validPins =
            preferences.pinnedStageIds.distinct().filter { id ->
                id in installedIds && profileState[id.profileId] != AppStageProfileState.REMOVED
            }
        val normalizedContent = normalize(contentSnapshot.content)
            .filter { content ->
                content.stageId in installedIds &&
                    (
                        profileState[content.stageId.profileId] == null ||
                            profileState[content.stageId.profileId] == AppStageProfileState.AVAILABLE
                    )
            }
            .groupBy(AppStageContent::stageId)
            .mapValues { (_, content) ->
                content.sortedWith(
                    compareByDescending<AppStageContent>(AppStageContent::meaningfulActivityAtEpochMillis)
                        .thenBy { item -> item.id.value },
                )
            }
        val requestedSelection = preferences.selectedStageId
        val previousSelection = previous?.preferences?.selectedStageId
        val retainFocusedEmptyDynamic =
            requestedSelection != null &&
                requestedSelection == previousSelection &&
                requestedSelection !in validPins &&
                requestedSelection in installedIds &&
                profileState[requestedSelection.profileId] != AppStageProfileState.REMOVED &&
                previous?.stages?.any { stage -> stage.id == requestedSelection && !stage.isPinned } == true

        val stageIds = (validPins + normalizedContent.keys + listOfNotNull(requestedSelection).filter { retainFocusedEmptyDynamic })
            .distinct()
        val stages = stageIds.mapNotNull { id ->
            val profile = profileState[id.profileId] ?: AppStageProfileState.AVAILABLE
            val content = normalizedContent[id].orEmpty()
            val pinned = id in validPins
            when {
                profile == AppStageProfileState.REMOVED -> null
                profile == AppStageProfileState.LOCKED && !pinned -> null
                profile == AppStageProfileState.LOCKED ->
                    AppStage(id, setOf(AppStageOrigin.PINNED), AppStageLifecycle.PROFILE_LOCKED)
                pinned || content.isNotEmpty() || id == requestedSelection ->
                    AppStage(
                        id = id,
                        origins = buildSet {
                            if (pinned) add(AppStageOrigin.PINNED)
                            if (content.isNotEmpty()) add(AppStageOrigin.DYNAMIC)
                        }.ifEmpty { setOf(AppStageOrigin.DYNAMIC) },
                        lifecycle = if (content.isEmpty()) AppStageLifecycle.EMPTY else AppStageLifecycle.ACTIVE,
                        content = content,
                    )
                else -> null
            }
        }
        val ordered = stages.sortedWith(stageOrder(validPins))
        val selected = requestedSelection.takeIf { it in ordered.map(AppStage::id) } ?: ordered.firstOrNull()?.id
        return AppStageSnapshot(
            stages = ordered,
            preferences = AppStagePreferences(pinnedStageIds = validPins, selectedStageId = selected),
        )
    }

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
            .thenByDescending { stage -> stage.content.maxOfOrNull(AppStageContent::meaningfulActivityAtEpochMillis) ?: Long.MIN_VALUE }
            .thenBy { stage -> stage.id }
}
