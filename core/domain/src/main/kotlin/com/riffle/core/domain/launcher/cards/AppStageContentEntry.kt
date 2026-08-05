package com.riffle.core.domain.launcher.cards

/**
 * One item in a cross-stage recency merge: [content] paired with the [stage] it came from, so a
 * consumer can attribute it (icon, label, lifecycle) without a second lookup.
 */
data class AppStageContentEntry(
    val stage: AppStage,
    val content: AppStageContent,
)

/**
 * Merges every stage's content into a single recency-ordered list, reusing
 * [AppStageContent.meaningfulActivityAtEpochMillis] as the primary order and an id-based tie-break for
 * determinism -- the same shape as [AppStagePlanner]'s private within-stage `contentOrder` comparator.
 *
 * This is a read-only projection: it does not change [AppStage.content]'s own order, and it does not
 * feed back into [AppStagePlanner.reconcile].
 */
fun List<AppStage>.mergedContentByRecency(): List<AppStageContentEntry> =
    flatMap { stage -> stage.content.map { content -> AppStageContentEntry(stage, content) } }
        .sortedWith(
            compareByDescending<AppStageContentEntry> { entry -> entry.content.meaningfulActivityAtEpochMillis }
                .thenBy { entry -> entry.content.id.value },
        )
