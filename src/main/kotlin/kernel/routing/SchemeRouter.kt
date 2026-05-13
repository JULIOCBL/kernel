package kernel.routing

import kernel.http.Request
import java.net.URI

private data class CompiledRoute(
    val method: String,
    val path: String,
    val pattern: Regex,
    val paramNames: List<String>,
    val middleware: List<String>,
    val name: String?,
    val action: (Map<String, String>) -> Any?
)

private data class RegisteredRoute(
    val method: String,
    val middleware: List<String>,
    val name: String?,
    val action: (Map<String, String>) -> Any?
)

private data class NamedRoute(
    val path: String,
    val paramNames: List<String>
)

data class RouteDefinition(
    val scheme: String,
    val method: String,
    val path: String,
    val middleware: List<String> = emptyList(),
    val name: String? = null,
    val requestType: Class<out Request>? = null
)

data class RouteMatch(
    val scheme: String,
    val method: String,
    val path: String,
    val params: Map<String, String>,
    val middleware: List<String> = emptyList(),
    val action: (Map<String, String>) -> Any?
)

/**
 * Router base para esquemas internos del kernel.
 */
open class SchemeRouter(
    private val scheme: String
) {
    private val staticRoutes = HashMap<String, MutableMap<String, RegisteredRoute>>()
    private val dynamicRoutes = mutableListOf<CompiledRoute>()
    private val namedRoutes = HashMap<String, NamedRoute>()
    private val routeDefinitions = mutableListOf<RouteDefinition>()

    fun get(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("GET", path, action, middleware, name, requestType)
    }

    fun post(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("POST", path, action, middleware, name, requestType)
    }

    fun put(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("PUT", path, action, middleware, name, requestType)
    }

    fun delete(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("DELETE", path, action, middleware, name, requestType)
    }

    fun patch(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("PATCH", path, action, middleware, name, requestType)
    }

    fun head(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("HEAD", path, action, middleware, name, requestType)
    }

    fun options(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("OPTIONS", path, action, middleware, name, requestType)
    }

    fun trace(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("TRACE", path, action, middleware, name, requestType)
    }

    fun connect(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register("CONNECT", path, action, middleware, name, requestType)
    }

    fun method(
        method: String,
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        register(method, path, action, middleware, name, requestType)
    }

    private fun register(
        method: String,
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String>,
        name: String?,
        requestType: Class<out Request>?
    ) {
        val normalized = normalizePath(path)
        val normalizedMethod = method.trim().uppercase()

        if (!normalized.contains('{')) {
            staticRoutes
                .getOrPut(normalized) { linkedMapOf() }[normalizedMethod] =
                RegisteredRoute(
                    method = normalizedMethod,
                    middleware = middleware,
                    name = name,
                    action = action
                )
        } else {
            compileDynamicRoute(normalizedMethod, normalized, action, middleware, name)
        }

        routeDefinitions.removeAll { definition ->
            definition.method == normalizedMethod && definition.path == normalized
        }
        routeDefinitions += RouteDefinition(
            scheme = scheme,
            method = normalizedMethod,
            path = normalized,
            middleware = middleware,
            name = name,
            requestType = requestType
        )

        if (!name.isNullOrBlank()) {
            namedRoutes[name] = NamedRoute(
                path = normalized,
                paramNames = extractParamNames(normalized)
            )
        }
    }

    fun resolve(uriString: String): RouteResolution? {
        return resolve("GET", uriString)
    }

    fun resolve(method: String, uriString: String): RouteResolution? {
        val match = match(method, uriString) ?: return null
        return RouteResolution(
            scheme = match.scheme,
            path = match.path,
            params = match.params,
            payload = match.action(match.params),
            middleware = match.middleware
        )
    }

    fun match(method: String, uriString: String): RouteMatch? {
        val uri = URI.create(uriString)
        if (!uri.scheme.equals(scheme, ignoreCase = true)) {
            return null
        }

        val path = buildPath(uri)
        val normalizedPath = normalizePath(path)
        val queryParams = extractQueryParams(uri)
        val normalizedMethod = method.trim().uppercase()

        staticRoutes[normalizedPath]?.get(normalizedMethod)?.let { route ->
            return RouteMatch(
                scheme = scheme,
                method = normalizedMethod,
                path = normalizedPath,
                params = queryParams,
                middleware = route.middleware,
                action = route.action
            )
        }

        for (route in dynamicRoutes) {
            if (route.method != normalizedMethod) {
                continue
            }
            val match = route.pattern.matchEntire(normalizedPath) ?: continue
            val params = queryParams.toMutableMap()
            route.paramNames.forEachIndexed { index, name ->
                params[name] = match.groupValues[index + 1]
            }

            return RouteMatch(
                scheme = scheme,
                method = normalizedMethod,
                path = normalizedPath,
                params = params,
                middleware = route.middleware,
                action = route.action
            )
        }

        return null
    }

    fun scheme(): String = scheme

    fun routes(): List<RouteDefinition> {
        return routeDefinitions.toList()
    }

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

    fun hasNamedRoute(name: String): Boolean {
        return namedRoutes.containsKey(name)
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
        method: String,
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
            method = method,
            path = path,
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
