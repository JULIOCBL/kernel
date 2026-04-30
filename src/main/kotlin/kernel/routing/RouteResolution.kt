package kernel.routing

/**
 * Resultado materializado de resolver una ruta.
 */
data class RouteResolution(
    val scheme: String,
    val path: String,
    val params: Map<String, String>,
    val payload: Any?
)
