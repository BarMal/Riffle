package com.riffle.app.launcher.stream.todoist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlConnectionTransportTest {
    private class FakeConnection(url: URL, val status: Int, val body: String) : HttpURLConnection(url) {
        val headers = HashMap<String, String>()
        val sent = ByteArrayOutputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun setRequestProperty(
            key: String,
            value: String,
        ) {
            headers[key] = value
        }

        override fun getResponseCode() = status

        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())

        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())

        override fun getOutputStream() = sent
    }

    private fun transport(
        status: Int = 200,
        body: String = "{}",
        capture: (FakeConnection) -> Unit = {},
    ) = HttpUrlConnectionTransport { url -> FakeConnection(url, status, body).also(capture) }

    @Test
    fun sendsHeadersAndBodyAndReadsTheAnswer() {
        var connection: FakeConnection? = null
        val response =
            transport(201, """{"ok":true}""") { connection = it }
                .execute(
                    HttpRequest(
                        "POST",
                        "https://api.todoist.com/api/v1/tasks",
                        mapOf("Authorization" to "Bearer fake"),
                        "{\"a\":1}",
                    ),
                )
        assertEquals(201, response.status)
        assertEquals("""{"ok":true}""", response.body)
        assertEquals("Bearer fake", connection!!.headers["Authorization"])
        assertEquals("POST", connection!!.requestMethod)
        assertEquals("{\"a\":1}", connection!!.sent.toString())
        assertFalse(connection!!.instanceFollowRedirects)
    }

    @Test
    fun anErrorStatusStillGivesAResponse() {
        assertEquals(401, transport(401).execute(HttpRequest("GET", "https://api.todoist.com/x")).status)
    }

    @Test
    fun plainHttpIsRefusedBeforeAnyConnectionIsOpened() {
        var opened = false
        val refused =
            runCatching {
                HttpUrlConnectionTransport { url ->
                    opened = true
                    FakeConnection(url, 200, "")
                }
                    .execute(HttpRequest("GET", "http://api.todoist.com/x", mapOf("Authorization" to "Bearer fake")))
            }
        assertTrue(refused.exceptionOrNull() is IOException)
        assertFalse(opened)
    }
}
