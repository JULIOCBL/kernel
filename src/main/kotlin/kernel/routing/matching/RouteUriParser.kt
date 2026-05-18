package kernel.routing.matching

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class ParsedRouteTarget(
    val normalizedPath: String,
    val segments: List<String>,
    val queryParams: Map<String, String>
)

internal object RouteUriParser {
    fun parse(
        expectedScheme: String,
        uriString: String
    ): ParsedRouteTarget? {
        val uri = URI.create(uriString)
        if (!uri.scheme.equals(expectedScheme, ignoreCase = true)) {
            return null
        }

        val path = buildPath(uri)
        val normalizedPath = normalizePath(path)
        val segments = if (normalizedPath.isBlank()) {
            emptyList()
        } else {
            normalizedPath.split('/').filter(String::isNotBlank)
        }

        return ParsedRouteTarget(
            normalizedPath = normalizedPath,
            segments = segments,
            queryParams = extractQueryParams(uri)
        )
    }

    fun normalizePath(path: String): String {
        return path.trim().trim('/').ifBlank { "" }
    }

    private fun buildPath(uri: URI): String {
        return (uri.authority ?: "") + (uri.path ?: "")
    }

    private fun extractQueryParams(uri: URI): Map<String, String> {
        val query = uri.rawQuery ?: return emptyMap()
        val params = linkedMapOf<String, String>()

        query.split('&')
            .filter(String::isNotBlank)
            .forEach { pair ->
                val idx = pair.indexOf('=')
                when {
                    idx > 0 -> {
                        val key = decode(pair.substring(0, idx))
                        val value = decode(pair.substring(idx + 1))
                        params[key] = value
                    }

                    else -> params[decode(pair)] = ""
                }
            }

        return params
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }
}

