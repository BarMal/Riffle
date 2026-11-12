package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.ItemTarget
import java.time.ZoneId

/** Pure mapping from [RemoteTask] to [TaskContent], and the order and cap the Tasks section is given. */
object TaskMapping {
    const val DEFAULT_LIMIT = 50

    fun toContent(
        task: RemoteTask,
        nowEpochMillis: Long,
        zone: ZoneId,
        stale: Boolean = false,
    ): TaskContent {
        val due = task.dueInstant?.toEpochMilli() ?: task.dueDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val hasTime = task.dueInstant != null
        val today = java.time.Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val overdue =
            when {
                task.dueInstant != null -> task.dueInstant.toEpochMilli() < nowEpochMillis
                task.dueDate != null -> task.dueDate.isBefore(today)
                else -> false
            }
        return TaskContent(
            id = TodoistLinks.contentId(task.id),
            title = task.title,
            listName = task.projectName?.takeIf { it.isNotBlank() },
            dueEpochMillis = due,
            dueHasTime = hasTime,
            overdue = overdue,
            priority = task.priority.coerceIn(1, 4),
            stale = stale,
            target = ItemTarget.DeepLink(TodoistLinks.webLink(task.id)),
        )
    }

    /** Overdue first, then by due time (none last), then most urgent first, then by id so the order is stable. */
    fun order(
        tasks: List<TaskContent>,
        limit: Int = DEFAULT_LIMIT,
    ): List<TaskContent> =
        tasks
            .sortedWith(
                compareBy<TaskContent> { !it.overdue }
                    .thenBy { it.dueEpochMillis ?: Long.MAX_VALUE }
                    .thenByDescending { it.priority }
                    .thenBy { it.id.value },
            )
            .take(limit)
}
