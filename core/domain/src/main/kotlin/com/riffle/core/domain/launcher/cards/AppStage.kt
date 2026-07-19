package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId

/** Stable, profile-aware identity for one TimeScape app stage. */
data class AppStageId(
    val packageName: AppPackageName,
    val profileId: AppProfileId,
) : Comparable<AppStageId> {
    override fun compareTo(other: AppStageId): Int = stableKey.compareTo(other.stableKey)

    internal val stableKey: String
        get() = "${profileId.value}:${packageName.value}"
}

/** Why a stage is currently present. A pinned stage with live content has both origins. */
enum class AppStageOrigin {
    PINNED,
    DYNAMIC,
}

/** Lifecycle state suitable for a renderer without exposing platform profile APIs. */
enum class AppStageLifecycle {
    ACTIVE,
    EMPTY,
    PROFILE_LOCKED,
}

/** The source category of transient content. Payload stays in the source adapter. */
enum class AppStageContentKind {
    NOTIFICATION,
    MEDIA,
}

/**
 * Small source-facing contract shared by notification and media adapters.
 *
 * It deliberately carries no notification text, artwork, Android object, or action. Source
 * adapters own those transient payloads and may use [id] to join them to a rendered stage.
 */
data class AppStageContent(
    val id: LauncherCardId,
    val stageId: AppStageId,
    val kind: AppStageContentKind,
    val meaningfulActivityAtEpochMillis: Long,
) {
    init {
        require(meaningfulActivityAtEpochMillis >= 0L) { "Stage activity timestamps cannot be negative." }
    }
}

/** Current process-only dynamic input. It is intentionally not a durable settings model. */
data class AppStageContentSnapshot(
    val content: List<AppStageContent> = emptyList(),
)

/** Profile availability supplied by the platform boundary without Android framework types. */
enum class AppStageProfileState {
    AVAILABLE,
    LOCKED,
    REMOVED,
}

/** Platform-facing inventory needed to safely reconcile installed apps and profile availability. */
data class AppStageIdentitySnapshot(
    val installedStageIds: List<AppStageId> = emptyList(),
    val profileStates: Map<AppProfileId, AppStageProfileState> = emptyMap(),
)

/** Persistable user choices only. Duplicate restored pins are normalized by [AppStagePlanner]. */
data class AppStagePreferences(
    val pinnedStageIds: List<AppStageId> = emptyList(),
    val selectedStageId: AppStageId? = null,
) {
    fun pin(stageId: AppStageId): AppStagePreferences =
        if (stageId in pinnedStageIds) this else copy(pinnedStageIds = pinnedStageIds + stageId)

    fun unpin(stageId: AppStageId): AppStagePreferences = copy(pinnedStageIds = pinnedStageIds - stageId)

    fun movePinnedStage(
        stageId: AppStageId,
        targetIndex: Int,
    ): AppStagePreferences {
        val sourceIndex = pinnedStageIds.indexOf(stageId)
        if (sourceIndex < 0) return this
        val reordered = pinnedStageIds.toMutableList()
        reordered.removeAt(sourceIndex)
        reordered.add(targetIndex.coerceIn(0, reordered.size), stageId)
        return copy(pinnedStageIds = reordered)
    }

    fun select(stageId: AppStageId?): AppStagePreferences = copy(selectedStageId = stageId)
}

/** One reconciled stage. [content] is transient and must never be serialized. */
data class AppStage(
    val id: AppStageId,
    val origins: Set<AppStageOrigin>,
    val lifecycle: AppStageLifecycle,
    val content: List<AppStageContent> = emptyList(),
) {
    init {
        require(origins.isNotEmpty()) { "A stage must have at least one origin." }
        require(content.map(AppStageContent::id).distinct().size == content.size) {
            "Stage content ids must be unique."
        }
    }

    val isPinned: Boolean
        get() = AppStageOrigin.PINNED in origins
}

/** Reconciled transient projection paired with the sanitized durable intent needed to recreate it. */
data class AppStageSnapshot(
    val stages: List<AppStage>,
    val preferences: AppStagePreferences,
) {
    init {
        require(stages.map(AppStage::id).distinct().size == stages.size) { "Stage ids must be unique." }
        require(preferences.selectedStageId == null || preferences.selectedStageId in stages.map(AppStage::id)) {
            "Selected stage must be available."
        }
    }

    val selectedStage: AppStage?
        get() = stages.firstOrNull { it.id == preferences.selectedStageId }
}
