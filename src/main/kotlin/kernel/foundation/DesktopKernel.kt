package kernel.foundation

import kernel.http.DesktopRequest
import kernel.http.DesktopResponse
import kernel.http.Middleware

typealias MiddlewareFactory = (Application) -> Middleware

/**
 * Kernel base para middlewares desktop estilo Laravel.
 *
 * Permite declarar middlewares globales, grupos y aliases por ruta.
 */
open class DesktopKernel(
    protected val app: Application
) {
    open val middleware: List<MiddlewareFactory> = emptyList()
    open val middlewareGroups: Map<String, List<String>> = emptyMap()
    open val routeMiddleware: Map<String, MiddlewareFactory> = emptyMap()

    fun handle(
        request: DesktopRequest,
        routeMiddlewareAliases: List<String> = emptyList()
    ): DesktopResponse {
        val stack = resolveMiddleware(routeMiddlewareAliases)
        var pipeline: (DesktopRequest) -> DesktopResponse =
            { currentRequest -> DesktopResponse.Success(currentRequest) }

        for (middlewareFactory in stack.asReversed()) {
            val next = pipeline
            val middlewareInstance = middlewareFactory(app)
            pipeline = { currentRequest ->
                middlewareInstance.handle(currentRequest, next)
            }
        }

        return pipeline(request)
    }

    fun resolveMiddleware(routeMiddlewareAliases: List<String>): List<MiddlewareFactory> {
        val resolvedAliases = expandAliases(routeMiddlewareAliases)
        val routeStack = resolvedAliases.map { alias ->
            routeMiddleware[alias]
                ?: error("No existe middleware desktop registrado con el alias `$alias`.")
        }

        return middleware + routeStack
    }

    private fun expandAliases(
        aliases: List<String>,
        stack: MutableSet<String> = linkedSetOf()
    ): List<String> {
        val expanded = mutableListOf<String>()

        aliases.forEach { alias ->
            val normalized = alias.trim()
            if (normalized.isBlank()) {
                return@forEach
            }

            if (middlewareGroups.containsKey(normalized)) {
                if (!stack.add(normalized)) {
                    error("Se detectó una recursión circular en el grupo de middlewares `$normalized`.")
                }

                expanded += expandAliases(middlewareGroups.getValue(normalized), stack)
                stack.remove(normalized)
            } else {
                expanded += normalized
            }
        }

        return expanded
    }
}
