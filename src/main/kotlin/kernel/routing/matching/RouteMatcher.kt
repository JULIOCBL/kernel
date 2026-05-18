package kernel.routing.matching

internal interface RouteMatcher {
    fun match(
        method: String,
        uriString: String
    ): RouteMatchResult?
}

