package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.rss.FeedValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AndroidFeedTransportTest {
    @Test
    fun sendsConditionalHeadersAndReturnsBodyAndValidators() {
        val connection =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                status = 200
                body = "<rss/>".toByteArray()
                headers["ETag"] = "etag-1"
                headers["Last-Modified"] = "Wed, 29 Jul 2026 10:00:00 GMT"
            }
        val result = transport(connection).fetch(request(validators = FeedValidators("old", "older")))

        assertEquals(
            FeedTransportResult.Content(
                body = "<rss/>",
                validators = FeedValidators("etag-1", "Wed, 29 Jul 2026 10:00:00 GMT"),
            ),
            result,
        )
        assertEquals("old", connection.sentProperties["If-None-Match"])
        assertEquals("older", connection.sentProperties["If-Modified-Since"])
    }

    @Test
    fun returnsNotModifiedWithoutReadingBody() {
        val connection = FakeHttpURLConnection(URL("https://example.com/feed")).apply { status = 304 }

        assertEquals(
            FeedTransportResult.NotModified(FeedValidators()),
            transport(connection).fetch(request()),
        )
        assertTrue(!connection.inputStreamRequested)
    }

    @Test
    fun followsHttpsRedirectAndRejectsHttpRedirect() {
        val first =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                status = 302
                headers["Location"] = "/next"
            }
        val second =
            FakeHttpURLConnection(URL("https://example.com/next")).apply {
                status = 200
                body = "ok".toByteArray()
            }
        val connections = mapOf(first.url.toString() to first, second.url.toString() to second)
        assertEquals(
            FeedTransportResult.Content("ok", FeedValidators()),
            AndroidFeedTransport(openConnection = { connections.getValue(it.toString()) }).fetch(request()),
        )

        val insecure =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                status = 302
                headers["Location"] = "http://example.com/unsafe"
            }
        assertEquals(
            FeedTransportResult.Failure(FeedSourceError.INVALID_REDIRECT),
            transport(insecure).fetch(request()),
        )
    }

    @Test
    fun mapsTimeoutNetworkHttpAndOversizedResponsesToNormalizedErrors() {
        val timeout =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                responseFailure = java.net.SocketTimeoutException("timeout")
            }
        assertEquals(FeedTransportResult.Failure(FeedSourceError.TIMEOUT), transport(timeout).fetch(request()))

        val network =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                responseFailure = IOException("network")
            }
        assertEquals(FeedTransportResult.Failure(FeedSourceError.NETWORK), transport(network).fetch(request()))

        val http = FakeHttpURLConnection(URL("https://example.com/feed")).apply { status = 500 }
        assertEquals(FeedTransportResult.Failure(FeedSourceError.HTTP), transport(http).fetch(request()))

        val oversized =
            FakeHttpURLConnection(URL("https://example.com/feed")).apply {
                status = 200
                body = ByteArray(5) { 1 }
            }
        assertEquals(
            FeedTransportResult.Failure(FeedSourceError.RESPONSE_TOO_LARGE),
            AndroidFeedTransport(openConnection = { oversized }, maxResponseBytes = 4).fetch(request()),
        )
    }

    @Test
    fun resolvesOnlyHttpsRedirects() {
        assertEquals("https://example.com/next", resolveHttpsRedirect("https://example.com/feed", "/next"))
        assertEquals(null, resolveHttpsRedirect("https://example.com/feed", "javascript:alert(1)"))
    }

    private fun transport(connection: FakeHttpURLConnection) = AndroidFeedTransport(openConnection = { connection })

    private fun request(validators: FeedValidators? = null) =
        FeedFetchRequest(
            configuration =
                FeedConfiguration(
                    id = FeedId("feed"),
                    url = FeedUrl.parse("https://example.com/feed").getOrThrow(),
                ),
            validators = validators,
        )

    private class FakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
        var status = 200
        var body = ByteArray(0)
        var responseFailure: IOException? = null
        val headers = mutableMapOf<String, String>()
        val sentProperties = mutableMapOf<String, String>()
        var inputStreamRequested = false

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            responseFailure?.let { throw it }
            return status
        }

        override fun getHeaderField(name: String): String? = headers[name]

        override fun setRequestProperty(
            key: String,
            value: String,
        ) {
            sentProperties[key] = value
        }

        override fun getInputStream() = ByteArrayInputStream(body).also { inputStreamRequested = true }
    }
}
