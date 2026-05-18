package kernel.routing

import kernel.http.Request
import kernel.routing.matching.CompiledRoute
import kernel.routing.matching.NamedRouteRegistry
import kernel.routing.matching.RadixRouteMatcher
import kernel.routing.matching.RouteConflictDetector
import kernel.routing.matching.RouteMatcher
import kernel.routing.matching.RoutePatternCompiler

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
 *
 * Registra rutas de forma mutable durante bootstrap, pero compila un catalogo
 * inmutable con matcher tipo radix para el lookup de runtime.
 */
open class SchemeRouter(
    private val scheme: String
) {
    private val lock = Any()
    private val routeEntries = mutableListOf<CompiledRoute>()
    private var frozen: Boolean = false

    @Volatile
    private var compiledState: CompiledRouterState? = null

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

    fun resolve(uriString: String): RouteResolution? {
        return resolve("GET", uriString)
    }

    fun resolve(
        method: String,
        uriString: String
    ): RouteResolution? {
        val match = match(method, uriString) ?: return null
        return RouteResolution(
            scheme = match.scheme,
            path = match.path,
            params = match.params,
            payload = match.action(match.params),
            middleware = match.middleware
        )
    }

    fun match(
        method: String,
        uriString: String
    ): RouteMatch? {
        val result = compiledState().matcher.match(method, uriString) ?: return null
        return RouteMatch(
            scheme = result.route.scheme,
            method = result.route.method,
            path = result.path,
            params = result.params,
            middleware = result.route.middleware,
            action = result.route.action
        )
    }

    fun scheme(): String = scheme

    fun routes(): List<RouteDefinition> = compiledState().definitions

    fun pathFor(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        return compiledState().namedRoutes.pathFor(name, params)
    }

    fun hasNamedRoute(name: String): Boolean {
        return compiledState().namedRoutes.hasNamedRoute(name)
    }

    fun freeze() {
        synchronized(lock) {
            if (frozen) {
                return
            }

            compiledState()
            frozen = true
        }
    }

    fun isFrozen(): Boolean = frozen

    private fun register(
        method: String,
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String>,
        name: String?,
        requestType: Class<out Request>?
    ) {
        val normalizedPath = normalizePath(path)
        val normalizedMethod = method.trim().uppercase()
        val compiledPath = RoutePatternCompiler.compile(normalizedPath)
        val route = CompiledRoute(
            scheme = scheme,
            method = normalizedMethod,
            path = normalizedPath,
            compiledPath = compiledPath,
            middleware = middleware,
            name = name,
            requestType = requestType,
            action = action
        )

        synchronized(lock) {
            check(!frozen) {
                "No se pueden registrar nuevas rutas en el router `$scheme` despues de freeze()."
            }

            routeEntries.removeAll { entry ->
                entry.method == normalizedMethod && entry.path == normalizedPath
            }
            routeEntries += route
            compiledState = null
        }
    }

    private fun compiledState(): CompiledRouterState {
        compiledState?.let { return it }

        return synchronized(lock) {
            compiledState?.let { return it }

            val snapshot = routeEntries.toList()
            RouteConflictDetector.validate(snapshot)

            val state = CompiledRouterState(
                matcher = buildMatcher(snapshot),
                namedRoutes = NamedRouteRegistry(snapshot),
                definitions = snapshot.map { route ->
                    RouteDefinition(
                        scheme = route.scheme,
                        method = route.method,
                        path = route.path,
                        middleware = route.middleware,
                        name = route.name,
                        requestType = route.requestType
                    )
                }
            )

            compiledState = state
            state
        }
    }

    private fun buildMatcher(routes: List<CompiledRoute>): RouteMatcher {
        return RadixRouteMatcher(scheme, routes)
    }



    private fun normalizePath(path: String): String {
        return path.trim().trim('/').ifBlank { "" }
    }

    private data class CompiledRouterState(
        val matcher: RouteMatcher,
        val namedRoutes: NamedRouteRegistry,
        val definitions: List<RouteDefinition>
    )
}
