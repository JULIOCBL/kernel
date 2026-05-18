package kernel.routing.matching

internal class NamedRouteRegistry(
    routes: List<CompiledRoute>
) {
    private data class NamedRoute(
        val path: String,
        val paramNames: List<String>
    )

    private val namedRoutes: Map<String, NamedRoute> = routes
        .filter { !it.name.isNullOrBlank() }
        .associate { route ->
            route.name!! to NamedRoute(
                path = route.path,
                paramNames = route.compiledPath.paramNames
            )
        }

    fun pathFor(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val route = namedRoutes[name]
            ?: error("No existe una ruta nombrada `$name`.")

        val missing = route.paramNames.filterNot(params::containsKey)
        require(missing.isEmpty()) {
            "Faltan parametros para la ruta `$name`: ${missing.joinToString(", ")}."
        }

        val resolved = route.path
            .split('/')
            .joinToString("/") { segment ->
                when {
                    segment.startsWith("{") && segment.endsWith("}") -> {
                        val key = segment.substring(1, segment.length - 1)
                        params[key]
                            ?: error("No existe valor para el parametro `$key` de la ruta `$name`.")
                    }

                    segment.startsWith("*") -> {
                        val key = segment.removePrefix("*")
                        params[key]
                            ?: error("No existe valor para el wildcard `$key` de la ruta `$name`.")
                    }

                    else -> segment
                }
            }

        return when {
            resolved.isBlank() -> "/"
            resolved.startsWith("/") -> resolved
            else -> "/$resolved"
        }
    }

    fun hasNamedRoute(name: String): Boolean = namedRoutes.containsKey(name)
}

