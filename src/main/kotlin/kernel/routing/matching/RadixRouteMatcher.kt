package kernel.routing.matching

internal class RadixRouteMatcher(
    private val scheme: String,
    routes: List<CompiledRoute>
) : RouteMatcher {
    private val root = RadixNode()

    init {
        routes.forEach(::insert)
    }

    override fun match(
        method: String,
        uriString: String
    ): RouteMatchResult? {
        val target = RouteUriParser.parse(scheme, uriString) ?: return null
        return matchNode(
            node = root,
            method = method.trim().uppercase(),
            segments = target.segments,
            index = 0,
            params = linkedMapOf(),
            queryParams = target.queryParams,
            normalizedPath = target.normalizedPath
        )
    }

    private fun insert(route: CompiledRoute) {
        var node = root

        route.compiledPath.segments.forEach { segment ->
            node = when (segment) {
                is PathSegment.Static -> node.staticChildren.getOrPut(segment.value) {
                    RadixNode()
                }

                is PathSegment.Param -> {
                    val existing = node.paramChild
                    require(existing == null || existing.name == segment.name) {
                        "Conflicto de rutas dinamicas en `${route.path}`: ya existe el parametro `${existing?.name}`."
                    }
                    if (existing != null) {
                        existing.node
                    } else {
                        val child = RadixNode()
                        node.paramChild = ParamChild(segment.name, child)
                        child
                    }
                }

                is PathSegment.Wildcard -> {
                    val existing = node.wildcardChild
                    require(existing == null || existing.name == segment.name) {
                        "Conflicto de wildcard en `${route.path}`: ya existe `${existing?.name}`."
                    }
                    if (existing != null) {
                        existing.node
                    } else {
                        val child = RadixNode()
                        node.wildcardChild = WildcardChild(segment.name, child)
                        child
                    }
                }
            }
        }

        node.handlersByMethod[route.method] = route
    }

    private fun matchNode(
        node: RadixNode,
        method: String,
        segments: List<String>,
        index: Int,
        params: LinkedHashMap<String, String>,
        queryParams: Map<String, String>,
        normalizedPath: String
    ): RouteMatchResult? {
        if (index == segments.size) {
            val route = node.handlersByMethod[method] ?: return null
            return RouteMatchResult(
                route = route,
                path = normalizedPath,
                params = queryParams + params
            )
        }

        val segment = segments[index]

        node.staticChildren[segment]?.let { child ->
            matchNode(child, method, segments, index + 1, params, queryParams, normalizedPath)?.let { return it }
        }

        node.paramChild?.let { child ->
            params[child.name] = segment
            matchNode(child.node, method, segments, index + 1, params, queryParams, normalizedPath)?.let { return it }
            params.remove(child.name)
        }

        node.wildcardChild?.let { child ->
            params[child.name] = segments.drop(index).joinToString("/")
            val route = child.node.handlersByMethod[method]
            if (route != null) {
                return RouteMatchResult(
                    route = route,
                    path = normalizedPath,
                    params = queryParams + params
                )
            }
            params.remove(child.name)
        }

        return null
    }

    private data class ParamChild(
        val name: String,
        val node: RadixNode
    )

    private data class WildcardChild(
        val name: String,
        val node: RadixNode
    )

    private class RadixNode {
        val staticChildren = linkedMapOf<String, RadixNode>()
        var paramChild: ParamChild? = null
        var wildcardChild: WildcardChild? = null
        val handlersByMethod = linkedMapOf<String, CompiledRoute>()
    }
}
