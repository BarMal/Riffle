package com.riffle.app.launcher.rss

import com.riffle.core.domain.launcher.rss.FeedFetchRequest
import com.riffle.core.domain.launcher.rss.FeedSourceError
import com.riffle.core.domain.launcher.rss.FeedTransport
import com.riffle.core.domain.launcher.rss.FeedTransportResult
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.rss.FeedValidators
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLConnection

private const val MAX_REDIRECTS = 5
private const val MAX_RESPONSE_BYTES = 1_048_576
private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 15_000

class AndroidFeedTransport(
    private val openConnection: (URL) -> HttpURLConnection = ::openHttpConnection,
    private val maxResponseBytes: Int = MAX_RESPONSE_BYTES,
    private val connectTimeoutMillis: Int = CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
) : FeedTransport {
    init {
        require(maxResponseBytes > 0) { "Response limit must be positive." }
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive." }
        require(readTimeoutMillis > 0) { "Read timeout must be positive." }
    }

    @Suppress("NestedBlockDepth", "ReturnCount")
    override fun fetch(request: FeedFetchRequest): FeedTransportResult {
        var currentUrl = request.configuration.url.value
        var redirectCount = 0
        while (true) {
            val connection =
                runCatching { openConnection(URL(currentUrl)) }
                    .getOrElse { return FeedTransportResult.Failure(FeedSourceError.NETWORK) }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml, text/xml",
                )
                request.validators?.etag?.let { value ->
                    connection.setRequestProperty("If-None-Match", value)
                }
                request.validators?.lastModified?.let { value ->
                    connection.setRequestProperty("If-Modified-Since", value)
                }

                val status = connection.responseCode
                val validators = connection.feedValidators()
                if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return FeedTransportResult.NotModified(validators)
                }
                if (status in 300..399) {
                    val location =
                        connection.getHeaderField("Location")
                            ?: return FeedTransportResult.Failure(FeedSourceError.INVALID_REDIRECT)
                    if (redirectCount >= MAX_REDIRECTS) {
                        return FeedTransportResult.Failure(FeedSourceError.REDIRECT_LIMIT)
                    }
                    currentUrl = resolveHttpsRedirect(currentUrl, location)
                        ?: return FeedTransportResult.Failure(FeedSourceError.INVALID_REDIRECT)
                    redirectCount += 1
                    continue
                }
                if (status !in 200..299) {
                    return FeedTransportResult.Failure(FeedSourceError.HTTP)
                }
                val body =
                    connection.readBoundedBody(maxResponseBytes)
                        ?: return FeedTransportResult.Failure(FeedSourceError.RESPONSE_TOO_LARGE)
                return FeedTransportResult.Content(body, validators)
            } catch (_: SocketTimeoutException) {
                return FeedTransportResult.Failure(FeedSourceError.TIMEOUT)
            } catch (_: IOException) {
                return FeedTransportResult.Failure(FeedSourceError.NETWORK)
            } finally {
                connection.disconnect()
            }
        }
    }
}

private fun openHttpConnection(url: URL): HttpURLConnection =
    (url.openConnection() as URLConnection).let { connection ->
        require(connection is HttpURLConnection) { "Feed URL must use HTTP transport." }
        connection
    }

internal fun resolveHttpsRedirect(
    currentUrl: String,
    location: String,
): String? =
    runCatching { URI(currentUrl).resolve(location).toString() }
        .getOrNull()
        ?.let { resolved -> FeedUrl.parse(resolved).getOrNull()?.value }

private fun HttpURLConnection.feedValidators(): FeedValidators =
    FeedValidators(
        etag = getHeaderField("ETag"),
        lastModified = getHeaderField("Last-Modified"),
    )

private fun HttpURLConnection.readBoundedBody(maxBytes: Int): String? {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    inputStream.use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > maxBytes) return null
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}
