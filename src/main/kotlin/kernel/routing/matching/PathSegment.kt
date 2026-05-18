package kernel.routing.matching

internal sealed interface PathSegment {
    data class Static(
        val value: String
    ) : PathSegment

    data class Param(
        val name: String
    ) : PathSegment

    data class Wildcard(
        val name: String
    ) : PathSegment
}

