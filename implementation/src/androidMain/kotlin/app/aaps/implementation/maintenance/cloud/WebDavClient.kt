package app.aaps.implementation.maintenance.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import java.util.Base64

/**
 * The HTTP calls WebDAV needs.
 *
 * Ktor has no WebDAV plugin because WebDAV is HTTP with a few extra methods. `PROPFIND`, `MKCOL`,
 * `PUT` and `GET` are sent as raw methods. Redirects are off in the client: a redirect can send the
 * Basic Auth header to another host, so a 3xx is treated as an error instead.
 */
internal class WebDavClient(private val http: HttpClient) {

    suspend fun testConnection(url: String, authorization: String) {
        val response = execute(
            url = url,
            method = "PROPFIND",
            authorization = authorization,
            body = WebDavXml.PROPFIND_BODY.toByteArray(Charsets.UTF_8),
            contentType = "application/xml; charset=utf-8",
            depth = "0",
            allowed = setOf(200, 207)
        )
        runCatching { response.bodyAsText() }
    }

    suspend fun list(url: String, authorization: String): List<WebDavEntry> {
        val response = execute(
            url = url,
            method = "PROPFIND",
            authorization = authorization,
            body = WebDavXml.PROPFIND_BODY.toByteArray(Charsets.UTF_8),
            contentType = "application/xml; charset=utf-8",
            depth = "1",
            allowed = setOf(200, 207)
        )
        return WebDavXml.parseMultiStatus(response.bodyAsText())
    }

    suspend fun stat(url: String, authorization: String): WebDavEntry? {
        val response = execute(
            url = url,
            method = "PROPFIND",
            authorization = authorization,
            body = WebDavXml.PROPFIND_BODY.toByteArray(Charsets.UTF_8),
            contentType = "application/xml; charset=utf-8",
            depth = "0",
            allowed = setOf(200, 207)
        )
        return WebDavXml.parseMultiStatus(response.bodyAsText()).firstOrNull()
    }

    suspend fun makeCollection(url: String, authorization: String) {
        val response = execute(
            url = url,
            method = "MKCOL",
            authorization = authorization,
            allowed = setOf(200, 201, 204, 405)
        )
        runCatching { response.bodyAsText() }
    }

    suspend fun put(url: String, content: ByteArray, mimeType: String, authorization: String) {
        val response = execute(
            url = url,
            method = "PUT",
            authorization = authorization,
            body = content,
            contentType = mimeType,
            allowed = setOf(200, 201, 204)
        )
        runCatching { response.bodyAsText() }
    }

    suspend fun get(url: String, authorization: String): ByteArray =
        execute(
            url = url,
            method = "GET",
            authorization = authorization,
            allowed = setOf(200)
        ).bodyAsBytes()

    private suspend fun execute(
        url: String,
        method: String,
        authorization: String,
        body: ByteArray? = null,
        contentType: String? = null,
        depth: String? = null,
        allowed: Set<Int>
    ): HttpResponse {
        val response = http.request(url) {
            this.method = HttpMethod(method)
            header(HttpHeaders.Authorization, authorization)
            depth?.let { header("Depth", it) }
            contentType?.let { header(HttpHeaders.ContentType, it) }
            body?.let { setBody(it) }
        }
        if (response.status.value !in allowed) {
            val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            throw WebDavException(response.status.value, text)
        }
        return response
    }

    companion object {

        fun basicAuthorization(username: String, password: String): String {
            val value = "$username:$password".toByteArray(Charsets.UTF_8)
            return "Basic " + Base64.getEncoder().encodeToString(value)
        }
    }
}

internal class WebDavException(val status: Int, body: String) : Exception(
    "WebDAV HTTP $status" + body.trim().take(200).let { if (it.isEmpty()) "" else ": $it" }
)
