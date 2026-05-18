package kernel.routing.matching

internal data class RouteMatchResult(
    val route: CompiledRoute,
    val path: String,
    val params: Map<String, String>
)

