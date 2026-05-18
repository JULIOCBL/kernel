package kernel.routing.matching

internal data class CompiledPath(
    val normalizedPath: String,
    val segments: List<PathSegment>
) {
    val paramNames: List<String> = segments.mapNotNull { segment ->
        when (segment) {
            is PathSegment.Param -> segment.name
            is PathSegment.Wildcard -> segment.name
            is PathSegment.Static -> null
        }
    }
}

