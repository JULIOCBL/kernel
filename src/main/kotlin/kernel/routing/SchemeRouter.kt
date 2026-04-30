package kernel.routing

import java.net.URI

private data class CompiledRoute(
    val pattern: Regex,
    val paramNames: List<String>,
    val action: (Map<String, String>) -> Any?
)

/**
 * Router base para esquemas internos del kernel.
 */
open class SchemeRouter(
    private val scheme: String
) {
    private val staticRoutes = HashMap<String, (Map<String, String>) -> Any?>()
    private val dynamicRoutes = mutableListOf<CompiledRoute>()

    fun get(path: String, action: (Map<String, String>) -> Any?) {
        val normalized = normalizePath(path)

        if (!normalized.contains('{')) {
            staticRoutes[normalized] = action
        } else {
            compileDynamicRoute(normalized, action)
        }
    }

    fun resolve(uriString: String): RouteResolution? {
        val uri = URI.create(uriString)
        if (!uri.scheme.equals(scheme, ignoreCase = true)) {
            return null
        }

        val path = buildPath(uri)
        val normalizedPath = normalizePath(path)
        val queryParams = extractQueryParams(uri)

        staticRoutes[normalizedPath]?.let { action ->
            return RouteResolution(
                scheme = scheme,
                path = normalizedPath,
                params = queryParams,
                payload = action(queryParams)
            )
        }

        for (route in dynamicRoutes) {
            val match = route.pattern.matchEntire(normalizedPath) ?: continue
            val params = queryParams.toMutableMap()
            route.paramNames.forEachIndexed { index, name ->
                params[name] = match.groupValues[index + 1]
            }

            return RouteResolution(
                scheme = scheme,
                path = normalizedPath,
                params = params,
                payload = route.action(params)
            )
        }

        return null
    }

    fun scheme(): String = scheme

    private fun buildPath(uri: URI): String {
        return (uri.authority ?: "") + (uri.path ?: "")
    }

    private fun normalizePath(path: String): String {
        return path.trim().trim('/').ifBlank { "" }
    }

    private fun extractQueryParams(uri: URI): Map<String, String> {
        val query = uri.query ?: return emptyMap()
        val params = linkedMapOf<String, String>()

        query.split('&')
            .filter(String::isNotBlank)
            .forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    params[pair.substring(0, idx)] = pair.substring(idx + 1)
                }
            }

        return params
    }

    private fun compileDynamicRoute(path: String, action: (Map<String, String>) -> Any?) {
        val paramNames = mutableListOf<String>()
        val regexString = path.replace(Regex("\\{([^}]+)\\}")) {
            paramNames += it.groupValues[1]
            "([^/]+)"
        }

        dynamicRoutes += CompiledRoute(
            pattern = Regex("^$regexString$"),
            paramNames = paramNames,
            action = action
        )
    }
}
