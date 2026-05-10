package kernel.foundation

import kernel.http.ExceptionHandler
import kernel.http.HttpMiddleware
import kernel.http.HttpRequestRuntime
import kernel.http.JsonResponse
import kernel.http.KernelResponse
import kernel.http.Request
import kernel.routing.ApiRouter

typealias HttpMiddlewareFactory = (Application) -> HttpMiddleware

open class HttpKernel(
    protected val app: Application
) {
    open val middleware: List<HttpMiddlewareFactory> = emptyList()
    open val middlewareGroups: Map<String, List<String>> = emptyMap()
    open val routeMiddleware: Map<String, HttpMiddlewareFactory> = emptyMap()
    open val middlewarePriority: List<String> = emptyList()
    open val exceptionHandler: ExceptionHandler = ExceptionHandler()

    fun handle(
        request: Request,
        router: ApiRouter
    ): KernelResponse {
        return try {
            val match = router.match(request.method, request.target)
                ?: return JsonResponse(
                    payload = mapOf(
                        "status" to "error",
                        "message" to "Ruta API no encontrada.",
                        "path" to request.path
                    ),
                    status = 404
                )

            val routedRequest = request.withRouteParams(match.params)
            val stack = resolveMiddleware(match.middleware)
            var pipeline: (Request) -> KernelResponse = { currentRequest ->
                HttpRequestRuntime.withRequest(currentRequest) {
                    val payload = match.action(currentRequest.all())
                    when (payload) {
                        is KernelResponse -> payload
                        else -> JsonResponse(payload = payload, status = 200)
                    }
                }
            }

            stack.asReversed().forEach { factory ->
                val middleware = factory(app)
                val next = pipeline
                pipeline = { currentRequest ->
                    middleware.handle(currentRequest, next)
                }
            }

            pipeline(routedRequest)
        } catch (error: Throwable) {
            exceptionHandler.render(request, error)
        }
    }

    fun resolveMiddleware(routeMiddlewareAliases: List<String>): List<HttpMiddlewareFactory> {
        val resolvedAliases = prioritizeAliases(expandAliases(routeMiddlewareAliases))
        return middleware + resolvedAliases.mapNotNull { alias ->
            routeMiddleware[alias]
        }
    }

    private fun expandAliases(aliases: List<String>): List<String> {
        return aliases.flatMap { alias ->
            middlewareGroups[alias] ?: listOf(alias)
        }
    }

    private fun prioritizeAliases(aliases: List<String>): List<String> {
        if (middlewarePriority.isEmpty()) {
            return aliases.distinct()
        }

        val indexedPriority = middlewarePriority.withIndex().associate { it.value to it.index }

        return aliases
            .distinct()
            .sortedWith(compareBy({ indexedPriority[it] ?: Int.MAX_VALUE }, { aliases.indexOf(it) }))
    }
}
