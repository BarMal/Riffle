package com.riffle.app.launcher.stream.todoist

import com.riffle.core.domain.launcher.stream.RemoteTask
import com.riffle.core.domain.launcher.stream.TaskApiResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The Tasks source against a FAKE HTTP layer: no network anywhere. The token here is obviously fake. */
class TodoistTaskApiTest {
    private val fakeToken = "fake-token-0000"

    private class FakeHttp : HttpTransport {
        val requests = ArrayList<HttpRequest>()
        var handler: (HttpRequest) -> HttpResponse = { HttpResponse(200, """{"results":[]}""") }

        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler(request)
        }
    }

    private val http = FakeHttp()
    private var token: TodoistToken? = TodoistToken(fakeToken)
    private val api = TodoistTaskApi(http, { token }, { ZoneOffset.UTC })

    private fun task(
        id: String,
        due: String? = "2026-11-12",
        project: String? = null,
        priority: Int = 1,
    ) = JSONObject()
        .put("id", id)
        .put("content", "Task $id")
        .put("priority", priority)
        .put("project_id", project ?: JSONObject.NULL)
        .put("due", due?.let { JSONObject().put("date", it).put("string", "today") } ?: JSONObject.NULL)

    private fun page(
        vararg tasks: JSONObject,
        next: String? = null,
    ) = JSONObject().put("results", org.json.JSONArray(tasks.toList())).put("next_cursor", next ?: JSONObject.NULL)
        .toString()

    @Test
    fun listsDueAndOverdueWithABearerHeaderAndTheFilterQuery() {
        http.handler = { HttpResponse(200, page(task("1"))) }
        val result = api.listDueTasks() as TaskApiResult.Ok
        assertEquals(1, result.value.size)
        val request = http.requests.single()
        assertEquals("GET", request.method)
        assertEquals("Bearer $fakeToken", request.headers["Authorization"])
        assertTrue(request.url.startsWith("https://api.todoist.com/api/v1/tasks/filter?"))
        val query = URLDecoder.decode(request.url.substringAfter("query="), "UTF-8").substringBefore("&limit")
        assertEquals("today | overdue", query)
        assertNull(request.body)
    }

    @Test
    fun followsPaginationCursorsAndStopsWhenThereIsNone() {
        http.handler = { request ->
            if ("cursor=c2" in request.url) {
                HttpResponse(200, page(task("3")))
            } else if ("cursor=c1" in request.url) {
                HttpResponse(200, page(task("2"), next = "c2"))
            } else {
                HttpResponse(200, page(task("1"), next = "c1"))
            }
        }
        val result = api.listDueTasks() as TaskApiResult.Ok
        assertEquals(listOf("1", "2", "3"), result.value.map { it.id })
        assertEquals(3, http.requests.size)
    }

    @Test
    fun readsDueDatesAndTimesAndProjectNamesOnce() {
        http.handler = { request ->
            if ("/projects" in request.url) {
                HttpResponse(200, """{"results":[{"id":"p1","name":"Home"}],"next_cursor":null}""")
            } else {
                HttpResponse(
                    200,
                    page(
                        task("1", "2026-11-12", "p1", priority = 4),
                        task("2", "2026-11-12T14:30:00Z", "p1"),
                        task("3", "2026-11-12T09:00:00", null),
                        task("4", null),
                    ),
                )
            }
        }
        val tasks = (api.listDueTasks() as TaskApiResult.Ok).value.associateBy { it.id }
        assertEquals(LocalDate.parse("2026-11-12"), tasks.getValue("1").dueDate)
        assertNull(tasks.getValue("1").dueInstant)
        assertEquals(4, tasks.getValue("1").priority)
        assertEquals("Home", tasks.getValue("1").projectName)
        assertEquals(Instant.parse("2026-11-12T14:30:00Z"), tasks.getValue("2").dueInstant)
        assertEquals(Instant.parse("2026-11-12T09:00:00Z"), tasks.getValue("3").dueInstant)
        assertNull(tasks.getValue("4").dueDate)
        api.listDueTasks()
        assertEquals(1, http.requests.count { "/projects" in it.url })
    }

    @Test
    fun aFailedProjectRequestStillGivesTheTasks() {
        http.handler = { request ->
            if ("/projects" in request.url) {
                HttpResponse(500, "")
            } else {
                HttpResponse(200, page(task("1", project = "p9")))
            }
        }
        val result = api.listDueTasks() as TaskApiResult.Ok
        assertNull(result.value.single().projectName)
    }

    @Test
    fun completePostsToTheClosePathWithTheBearerHeader() {
        http.handler = { HttpResponse(204, "") }
        assertEquals(TaskApiResult.Ok(Unit), api.complete("abc123"))
        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://api.todoist.com/api/v1/tasks/abc123/close", request.url)
        assertEquals("Bearer $fakeToken", request.headers["Authorization"])
    }

    @Test
    fun completeRefusesAnIdThatCouldEscapeThePath() {
        assertEquals(TaskApiResult.Failed, api.complete("../projects"))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun addPostsJsonWithTheTextDueToday() {
        http.handler = { HttpResponse(200, """{"id":"9"}""") }
        assertEquals(TaskApiResult.Ok(Unit), api.add("Buy \"milk\"\nnow"))
        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://api.todoist.com/api/v1/tasks", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        val body = JSONObject(request.body!!)
        assertEquals("Buy \"milk\"\nnow", body.getString("content"))
        assertEquals("today", body.getString("due_string"))
        assertEquals("Bearer $fakeToken", request.headers["Authorization"])
    }

    @Test
    fun statusesMapToTypedResults() {
        http.handler = { HttpResponse(401, "") }
        assertEquals(TaskApiResult.AuthRejected, api.listDueTasks())
        http.handler = { HttpResponse(403, "") }
        assertEquals(TaskApiResult.AuthRejected, api.complete("1"))
        http.handler = { HttpResponse(429, "") }
        assertEquals(TaskApiResult.Failed, api.listDueTasks())
        http.handler = { HttpResponse(500, "") }
        assertEquals(TaskApiResult.Failed, api.add("x"))
        http.handler = { throw IOException("offline") }
        assertEquals(TaskApiResult.Offline, api.listDueTasks())
        http.handler = { HttpResponse(200, "not json") }
        assertEquals(TaskApiResult.Failed, api.listDueTasks())
    }

    @Test
    fun noTokenMakesNoRequest() {
        token = null
        assertEquals(TaskApiResult.AuthRejected, api.listDueTasks())
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun theTokenIsNeverInAUrlOrABodyOrAPrintedValue() {
        http.handler = { HttpResponse(200, page(task("1"))) }
        api.listDueTasks()
        api.add("x")
        api.complete("1")
        http.requests.forEach { request ->
            assertFalse(request.url.contains(fakeToken))
            assertFalse((request.body ?: "").contains(fakeToken))
            assertFalse(request.toString().contains(fakeToken))
        }
    }

    @Test
    fun skipsTasksWithoutAnIdOrTitle() {
        http.handler = {
            HttpResponse(
                200,
                """{"results":[{"content":"no id"},{"id":"2","content":" "},{"id":"3","content":"ok"}]}""",
            )
        }
        assertEquals(listOf("3"), (api.listDueTasks() as TaskApiResult.Ok<List<RemoteTask>>).value.map { it.id })
    }
}
