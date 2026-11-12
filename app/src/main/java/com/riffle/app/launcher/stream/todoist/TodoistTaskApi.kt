package com.riffle.app.launcher.stream.todoist

import com.riffle.core.domain.launcher.stream.RemoteTask
import com.riffle.core.domain.launcher.stream.TaskApi
import com.riffle.core.domain.launcher.stream.TaskApiResult
import java.io.IOException
import java.time.ZoneId

/**
 * [TaskApi] over Todoist's REST API. The token is read per request from [token] and goes only in the Authorization
 * header of requests to the one Todoist host; it is never put in a URL, a log line or an exception message. Blocking.
 */
internal class TodoistTaskApi(
    private val transport: HttpTransport,
    private val token: () -> TodoistToken?,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : TaskApi {
    /** Project names seen so far, so most refreshes need no project request. */
    private val projectNames = HashMap<String, String>()

    @Suppress("ReturnCount")
    override fun listDueTasks(): TaskApiResult<List<RemoteTask>> {
        val tasks = ArrayList<RemoteTask>()
        var cursor: String? = null
        repeat(TodoistApiMapping.MAX_PAGES) {
            val response =
                when (val result = send("GET", TodoistApiMapping.filterUrl(cursor))) {
                    is TaskApiResult.Ok -> result.value
                    TaskApiResult.AuthRejected -> return TaskApiResult.AuthRejected
                    TaskApiResult.Offline -> return TaskApiResult.Offline
                    TaskApiResult.Failed -> return TaskApiResult.Failed
                }
            val page = TodoistApiMapping.parseTasksPage(response.body, zone()) ?: return TaskApiResult.Failed
            tasks += page.tasks
            cursor = page.nextCursor
            if (cursor == null) return TaskApiResult.Ok(withProjectNames(tasks))
        }
        return TaskApiResult.Ok(withProjectNames(tasks))
    }

    override fun complete(id: String): TaskApiResult<Unit> =
        if (!TodoistApiMapping.isSafeId(id)) {
            TaskApiResult.Failed
        } else {
            send("POST", TodoistApiMapping.closeUrl(id)).unit()
        }

    override fun add(text: String): TaskApiResult<Unit> =
        send("POST", TodoistApiMapping.addUrl(), TodoistApiMapping.addBody(text)).unit()

    /** Names are a nicety: any failure leaves the tasks without a project name. */
    private fun withProjectNames(tasks: List<RemoteTask>): List<RemoteTask> {
        val unknown = tasks.any { it.projectId != null && it.projectId !in projectNames }
        if (unknown) {
            (send("GET", TodoistApiMapping.projectsUrl()) as? TaskApiResult.Ok)
                ?.let { TodoistApiMapping.parseProjects(it.value.body) }
                ?.let(projectNames::putAll)
        }
        return tasks.map { it.copy(projectName = it.projectId?.let(projectNames::get)) }
    }

    @Suppress("ReturnCount")
    private fun send(
        method: String,
        url: String,
        body: String? = null,
    ): TaskApiResult<HttpResponse> {
        val bearer = token() ?: return TaskApiResult.AuthRejected
        val headers =
            buildMap {
                put("Authorization", "Bearer ${bearer.reveal()}")
                put("Accept", "application/json")
                if (body != null) put("Content-Type", "application/json")
            }
        val response =
            try {
                transport.execute(HttpRequest(method, url, headers, body))
            } catch (_: IOException) {
                return TaskApiResult.Offline
            } catch (_: RuntimeException) {
                return TaskApiResult.Failed
            }
        return when (response.status) {
            in 200..299 -> TaskApiResult.Ok(response)
            401, 403 -> TaskApiResult.AuthRejected
            else -> TaskApiResult.Failed
        }
    }

    private fun TaskApiResult<HttpResponse>.unit(): TaskApiResult<Unit> =
        when (this) {
            is TaskApiResult.Ok -> TaskApiResult.Ok(Unit)
            TaskApiResult.AuthRejected -> TaskApiResult.AuthRejected
            TaskApiResult.Offline -> TaskApiResult.Offline
            TaskApiResult.Failed -> TaskApiResult.Failed
        }
}
