package kernel.routing

import java.net.URI

private data class CompiledRoute(
    val pattern: Regex,
    val paramNames: List<String>,
    val middleware: List<String>,
    val name: String?,
    val action: (Map<String, String>) -> Any?
)

private data class RegisteredRoute(
    val middleware: List<String>,
    val name: String?,
    val action: (Map<String, String>) -> Any?
)

private data class NamedRoute(
    val path: String,
    val paramNames: List<String>
)

/**
 * Router base para esquemas internos del kernel.
 */
open class SchemeRouter(
    private val scheme: String
) {
    private val staticRoutes = HashMap<String, RegisteredRoute>()
    private val dynamicRoutes = mutableListOf<CompiledRoute>()
    private val namedRoutes = HashMap<String, NamedRoute>()

    fun get(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        val normalized = normalizePath(path)

        if (!normalized.contains('{')) {
            staticRoutes[normalized] = RegisteredRoute(
                middleware = middleware,
                name = name,
                action = action
            )
        } else {
            compileDynamicRoute(normalized, action, middleware, name)
        }

        if (!name.isNullOrBlank()) {
            namedRoutes[name] = NamedRoute(
                path = normalized,
                paramNames = extractParamNames(normalized)
            )
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

        staticRoutes[normalizedPath]?.let { route ->
            return RouteResolution(
                scheme = scheme,
                path = normalizedPath,
                params = queryParams,
                payload = route.action(queryParams),
                middleware = route.middleware
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
                payload = route.action(params),
                middleware = route.middleware
            )
        }

        return null
    }

    fun scheme(): String = scheme

    fun pathFor(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val route = namedRoutes[name]
            ?: error("No existe una ruta nombrada `$name` en el router `$scheme`.")

        val missing = route.paramNames.filterNot(params::containsKey)
        require(missing.isEmpty()) {
            "Faltan parametros para la ruta `$name`: ${missing.joinToString(", ")}."
        }

        val resolved = route.path.replace(Regex("\\{([^}]+)\\}")) { match ->
            val key = match.groupValues[1]
            params[key]
                ?: error("No existe valor para el parametro `$key` de la ruta `$name`.")
        }

        return when {
            resolved.isBlank() -> "/"
            resolved.startsWith("/") -> resolved
            else -> "/$resolved"
        }
    }

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

    private fun compileDynamicRoute(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String>,
        name: String?
    ) {
        val paramNames = mutableListOf<String>()
        val regexString = path.replace(Regex("\\{([^}]+)\\}")) {
            paramNames += it.groupValues[1]
            "([^/]+)"
        }

        dynamicRoutes += CompiledRoute(
            pattern = Regex("^$regexString$"),
            paramNames = paramNames,
            middleware = middleware,
            name = name,
            action = action
        )
    }

    private fun extractParamNames(path: String): List<String> {
        return Regex("\\{([^}]+)\\}")
            .findAll(path)
            .map { match -> match.groupValues[1] }
            .toList()
    }
}
