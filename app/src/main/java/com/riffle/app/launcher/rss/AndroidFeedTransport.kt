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
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

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

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    override fun fetch(request: FeedFetchRequest): FeedTransportResult {
        var currentUrl = request.configuration.url.value
        var currentValidators = request.validators
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
                currentValidators?.etag?.let { value ->
                    connection.setRequestProperty("If-None-Match", value)
                }
                currentValidators?.lastModified?.let { value ->
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
                    val nextUrl =
                        resolveHttpsRedirect(currentUrl, location)
                            ?: return FeedTransportResult.Failure(FeedSourceError.INVALID_REDIRECT)
                    if (httpsOrigin(currentUrl) != httpsOrigin(nextUrl)) {
                        currentValidators = null
                    }
                    currentUrl = nextUrl
                    redirectCount += 1
                    continue
                }
                if (status !in 200..299) {
                    return FeedTransportResult.Failure(FeedSourceError.HTTP)
                }
                val bytes =
                    connection.readBoundedBody(maxResponseBytes)
                        ?: return FeedTransportResult.Failure(FeedSourceError.RESPONSE_TOO_LARGE)
                val body =
                    decodeFeedBody(bytes, connection.contentType)
                        ?: return FeedTransportResult.Failure(FeedSourceError.INVALID_ENCODING)
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

private fun httpsOrigin(url: String): String? =
    runCatching {
        URI(url).let { parsed ->
            if (!parsed.scheme.equals("https", ignoreCase = true) || parsed.host == null) {
                null
            } else {
                val port = if (parsed.port == -1) 443 else parsed.port
                "https://${parsed.host.lowercase()}:$port"
            }
        }
    }.getOrNull()

private fun HttpURLConnection.feedValidators(): FeedValidators =
    FeedValidators(
        etag = getHeaderField("ETag"),
        lastModified = getHeaderField("Last-Modified"),
    )

private fun HttpURLConnection.readBoundedBody(maxBytes: Int): ByteArray? {
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
    return output.toByteArray()
}

private fun decodeFeedBody(
    bytes: ByteArray,
    contentType: String?,
): String? =
    runCatching {
        val charset =
            declaredHttpCharset(contentType)
                ?: declaredXmlCharset(bytes)
                ?: bomCharset(bytes)
                ?: StandardCharsets.UTF_8
        String(bytes, charset)
    }.getOrNull()

private fun declaredHttpCharset(contentType: String?): Charset? =
    contentType
        ?.let { value -> HTTP_CHARSET_PATTERN.find(value)?.groupValues?.get(1) }
        ?.let { value -> Charset.forName(value) }

private fun declaredXmlCharset(bytes: ByteArray): Charset? =
    String(bytes, 0, min(bytes.size, XML_DECLARATION_SCAN_BYTES), StandardCharsets.US_ASCII)
        .let { prefix -> XML_CHARSET_PATTERN.find(prefix)?.groupValues?.get(1) }
        ?.let { value -> Charset.forName(value) }

private fun bomCharset(bytes: ByteArray): Charset? =
    when {
        bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) -> StandardCharsets.UTF_16
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) -> StandardCharsets.UTF_16
        bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) -> StandardCharsets.UTF_8
        else -> null
    }

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

private const val XML_DECLARATION_SCAN_BYTES = 512
private val HTTP_CHARSET_PATTERN = Regex("charset\\s*=\\s*[\\\"']?([^;\\s\\\"']+)", RegexOption.IGNORE_CASE)
private val XML_CHARSET_PATTERN = Regex("encoding\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
