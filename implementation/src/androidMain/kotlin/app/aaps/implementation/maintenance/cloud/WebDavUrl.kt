package app.aaps.implementation.maintenance.cloud

import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import java.net.URI

/**
 * Builds WebDAV URLs from one server root.
 *
 * The user enters the root once, for example
 * `https://cloud.example.com/remote.php/dav/files/alice`. Every AAPS path is relative to it. A file
 * id is also relative, so changing the host name later does not break saved folders and files as
 * long as the path below the root stays the same.
 */
internal class WebDavUrl private constructor(val baseUrl: String) {

    /** Resolves an AAPS path below [baseUrl]. A leading slash does not leave the base path. */
    fun resolve(path: String): String {
        val value = path.trim()
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            return value
        }
        val builder = URLBuilder(baseUrl)
        builder.appendPathSegments(value.trimStart('/').split('/').filter { it.isNotBlank() })
        return builder.buildString()
    }

    /** Resolves a link returned by the server. A link that starts with `/` is from the host root. */
    fun resolveHref(href: String): String {
        val value = href.trim()
        if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
            return value
        }
        if (value.startsWith("/")) return URI(origin).resolve(value).toString()
        return resolve(value)
    }

    /** A collection URL with the trailing slash most servers expect for PROPFIND. */
    fun collection(path: String): String = resolve(path).trimEnd('/') + "/"

    fun child(parent: String, name: String): String =
        URLBuilder(resolve(parent)).apply { appendPathSegments(listOf(name)) }.buildString()

    fun relative(url: String): String {
        val target = runCatching { URI(resolveHref(url)) }.getOrNull() ?: return url
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return url
        val basePath = base.path.trimEnd('/')
        val targetPath = target.path
        return when {
            basePath.isEmpty()         -> targetPath.ifEmpty { "/" }
            targetPath == basePath     -> "/"
            targetPath == "$basePath/" -> "/"
            targetPath.startsWith("$basePath/") -> targetPath.removePrefix(basePath)
            else                       -> targetPath
        }
    }

    fun name(url: String): String =
        runCatching { URI(resolveHref(url)).path.substringAfterLast('/') }.getOrDefault("")

    private val origin: String
        get() = URI(baseUrl).let { "${it.scheme}://${it.authority}" }

    companion object {

        /**
         * A safe HTTPS root, or null when the text is not a plain HTTPS URL.
         *
         * User info, query strings and fragments are rejected because credentials belong in the
         * Basic Auth header and the other parts have no meaning for a folder root.
         */
        fun from(value: String): WebDavUrl? {
            val trimmed = value.trim()
            if (!trimmed.startsWith("https://", ignoreCase = true)) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host.isNullOrBlank()) return null
            if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
            val path = uri.path.orEmpty().trimEnd('/')
            val normalized = runCatching {
                URI(uri.scheme, uri.authority, path.ifEmpty { "/" }, null, null).toString().trimEnd('/')
            }.getOrNull() ?: return null
            return WebDavUrl(normalized)
        }
    }
}
