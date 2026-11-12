package com.riffle.app.launcher.stream.todoist

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One HTTP request. [headers] can hold the token, so [toString] never prints them (or the body, or the URL query). */
class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    override fun toString(): String = "HttpRequest($method <redacted>)"
}

class HttpResponse(
    val status: Int,
    val body: String,
) {
    override fun toString(): String = "HttpResponse($status)"
}

/** The one HTTP seam of the Tasks source, faked in tests. [execute] blocks and throws [IOException] when offline. */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}

private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 15_000
private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

/**
 * `HttpURLConnection`, like the RSS transport, no new dependency. https only; redirects are NOT followed, so the
 * Authorization header can never be sent anywhere but the first host. Responses are size-capped.
 */
class HttpUrlConnectionTransport(
    private val open: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection },
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val url = URL(request.url)
        if (!url.protocol.equals("https", ignoreCase = true)) throw IOException("https only")
        val connection = open(url)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = request.method
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (request.method != "GET") {
                connection.doOutput = true
                val bytes = (request.body ?: "").toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(status, stream?.use { readBounded(it) } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("response too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
