package kernel.routing.matching

internal object RouteConflictDetector {
    fun validate(routes: List<CompiledRoute>) {
        val grouped = routes.groupBy { route ->
            route.method to canonicalSignature(route.compiledPath)
        }

        grouped.forEach { (_, candidates) ->
            if (candidates.size <= 1) {
                return@forEach
            }

            val distinctPaths = candidates.map(CompiledRoute::path).distinct()
            require(distinctPaths.size == 1) {
                "Rutas ambiguas detectadas para `${candidates.first().method}`: ${distinctPaths.joinToString(", ")}"
            }
        }
    }

    private fun canonicalSignature(path: CompiledPath): String {
        return path.segments.joinToString("/") { segment ->
            when (segment) {
                is PathSegment.Static -> "static:${segment.value}"
                is PathSegment.Param -> "{}"
                is PathSegment.Wildcard -> "*"
            }
        }
    }
}

