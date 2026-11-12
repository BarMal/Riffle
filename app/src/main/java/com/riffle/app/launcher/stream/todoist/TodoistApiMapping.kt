package com.riffle.app.launcher.stream.todoist

import com.riffle.core.domain.launcher.stream.RemoteTask
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * EVERY Todoist endpoint path, query name, request field and response field the app relies on lives in this one
 * file, so a change in the service is a change here only.
 *
 * Source: the official Todoist API v1 (https://developer.todoist.com/api/v1/) and its official Python client
 * (Doist/todoist-api-python), which uses these paths and names: GET /tasks/filter (query, limit, cursor; answer
 * `results` and `next_cursor`), POST /tasks/{id}/close, POST /tasks (content, due_string), GET /projects
 * (limit; answer `results`). A task has `id`, `content`, `description`, `priority` (4 is most urgent), `project_id`
 * and `due` {`date`, `string`, `timezone`, `is_recurring`}; `due.date` is a day ("2026-11-12") or a day and time
 * ("2026-11-12T14:00:00", with a trailing Z when it is a fixed instant, none when it is floating local time).
 * Not confirmed from the reference page itself (it could not be read in full): the date formats above, the web
 * address of a task and the todoist:// deep link ([com.riffle.core.domain.launcher.stream.TodoistLinks]).
 */
@Suppress("TooManyFunctions")
internal object TodoistApiMapping {
    const val BASE_URL = "https://api.todoist.com/api/v1"

    /** Todoist filter syntax: tasks due today and everything overdue. */
    const val FILTER_QUERY = "today | overdue"
    const val PAGE_LIMIT = 200
    const val MAX_PAGES = 5

    /** The text of a new task is its title and it is due today. */
    const val ADD_DUE_STRING = "today"

    private const val TASKS_PATH = "/tasks"
    private const val FILTER_PATH = "/tasks/filter"
    private const val PROJECTS_PATH = "/projects"

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")

    fun isSafeId(id: String): Boolean = SAFE_ID.matches(id)

    fun filterUrl(cursor: String?): String =
        buildString {
            append(BASE_URL).append(FILTER_PATH)
            append("?query=").append(encode(FILTER_QUERY))
            append("&limit=").append(PAGE_LIMIT)
            if (cursor != null) append("&cursor=").append(encode(cursor))
        }

    fun projectsUrl(): String = "$BASE_URL$PROJECTS_PATH?limit=$PAGE_LIMIT"

    fun closeUrl(id: String): String = "$BASE_URL$TASKS_PATH/$id/close"

    fun addUrl(): String = "$BASE_URL$TASKS_PATH"

    fun addBody(text: String): String = JSONObject().put("content", text).put("due_string", ADD_DUE_STRING).toString()

    class TasksPage(
        val tasks: List<RemoteTask>,
        val nextCursor: String?,
    )

    /** Null when the answer is not the expected shape. A task without an id or a title is skipped. */
    fun parseTasksPage(
        json: String,
        zone: ZoneId,
    ): TasksPage? =
        try {
            val root = JSONObject(json)
            val results = root.optJSONArray("results") ?: return null
            val tasks =
                (0 until results.length()).mapNotNull { index ->
                    results.optJSONObject(index)?.let { parseTask(it, zone) }
                }
            TasksPage(
                tasks,
                root.optString("next_cursor", "").takeIf { it.isNotEmpty() && !root.isNull("next_cursor") },
            )
        } catch (_: JSONException) {
            null
        }

    @Suppress("ReturnCount")
    private fun parseTask(
        task: JSONObject,
        zone: ZoneId,
    ): RemoteTask? {
        val id = task.optString("id", "").takeIf { it.isNotEmpty() } ?: return null
        val title = task.optString("content", "").takeIf { it.isNotBlank() } ?: return null
        val dueValue = task.optJSONObject("due")?.optString("date", "")?.takeIf { it.isNotEmpty() }
        val due = dueValue?.let { parseDue(it, zone) }
        return RemoteTask(
            id = id,
            title = title,
            dueDate = due?.first,
            dueInstant = due?.second,
            priority = task.optInt("priority", 1),
            projectId = task.optString("project_id", "").takeIf { it.isNotEmpty() && !task.isNull("project_id") },
        )
    }

    /** A day, or a day and an instant. Floating times (no zone) are read in [zone]. Null when unreadable. */
    fun parseDue(
        value: String,
        zone: ZoneId,
    ): Pair<LocalDate, Instant?>? =
        runCatching {
            if (value.length <= DATE_ONLY_LENGTH) {
                LocalDate.parse(value) to null
            } else {
                val instant =
                    if (value.endsWith("Z") || OFFSET.containsMatchIn(value)) {
                        OffsetDateTime.parse(value).toInstant()
                    } else {
                        LocalDateTime.parse(value).atZone(zone).toInstant()
                    }
                instant.atZone(zone).toLocalDate() to instant
            }
        }.getOrNull()

    fun parseProjects(json: String): Map<String, String>? =
        try {
            val results = JSONObject(json).optJSONArray("results")
            if (results == null) {
                null
            } else {
                (0 until results.length()).mapNotNull { index ->
                    val project = results.optJSONObject(index)
                    val id = project?.optString("id", "")?.takeIf { it.isNotEmpty() }
                    val name = project?.optString("name", "")?.takeIf { it.isNotBlank() }
                    if (id != null && name != null) id to name else null
                }.toMap()
            }
        } catch (_: JSONException) {
            null
        }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private const val DATE_ONLY_LENGTH = 10
    private val OFFSET = Regex("[+-]\\d{2}:\\d{2}$")
}
