package kernel.foundation

import kernel.debug.dump
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
                ?: error(
                    "\n\n" + """
                [Kotavel] Error de middlewares desktop

                No existe middleware desktop registrado con el alias `$alias`.

                Verifica que:
                • El alias exista en `routeMiddleware`.
                • El nombre del middleware esté bien escrito.
                • Si proviene de un grupo, ese grupo no esté referenciando un alias inexistente.
                """.trimIndent() + "\n\n"
                )
        }

        return middleware + routeStack
    }

    private fun expandAliases(
        aliases: List<String>,
        stack: MutableSet<String> = linkedSetOf(),
        parentGroup: String? = null
    ): List<String> {
        val expanded = mutableListOf<String>()

        aliases.forEach { alias ->
            val normalized = alias.trim()
            if (normalized.isBlank()) {
                return@forEach
            }

            when {
                middlewareGroups.containsKey(normalized) -> {
                    if (!stack.add(normalized)) {
                        error(
                            "\n\n" + """
                        [Kotavel] Error de middlewares desktop

                        Se detectó una recursión circular en el grupo `$normalized`.

                        Verifica que:
                        • El grupo `$normalized` no se referencie a sí mismo.
                        • No exista un ciclo entre `middlewareGroups`.
                        • La composición de grupos desktop sea válida.
                        """.trimIndent() + "\n\n"
                        )
                    }

                    expanded += expandAliases(
                        aliases = middlewareGroups.getValue(normalized),
                        stack = stack,
                        parentGroup = normalized
                    )

                    stack.remove(normalized)
                }

                routeMiddleware.containsKey(normalized) -> {
                    expanded += normalized
                }

                parentGroup != null -> {
                    error(
                        "\n\n" + """
                    [Kotavel] Error de middlewares desktop

                    El grupo `$parentGroup` referencia el middleware `$normalized`,
                    pero ese alias no está registrado en `routeMiddleware`.

                    Verifica que:
                    • El middleware `$normalized` exista en `routeMiddleware`.
                    • El nombre esté bien escrito.
                    • El grupo `$parentGroup` no tenga referencias incompletas.
                    """.trimIndent() + "\n\n"
                    )
                }

                else -> {
                    error(
                        "\n\n" + """
                    [Kotavel] Error de middlewares desktop

                    No existe un middleware ni grupo registrado con el alias `$normalized`.

                    Verifica que:
                    • El alias exista en `routeMiddleware`.
                    • O el grupo exista en `middlewareGroups`.
                    • El nombre usado en la ruta esté bien escrito.
                    """.trimIndent() + "\n\n"
                    )
                }
            }
        }

        return expanded
    }
}
