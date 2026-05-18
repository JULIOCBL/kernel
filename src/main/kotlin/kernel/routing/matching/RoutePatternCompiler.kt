package kernel.routing.matching

internal object RoutePatternCompiler {
    fun compile(normalizedPath: String): CompiledPath {
        if (normalizedPath.isBlank()) {
            return CompiledPath(
                normalizedPath = "",
                segments = emptyList()
            )
        }

        val segments = normalizedPath.split('/')
            .filter(String::isNotBlank)
            .map(::compileSegment)

        validateSegments(segments, normalizedPath)

        return CompiledPath(
            normalizedPath = normalizedPath,
            segments = segments
        )
    }

    private fun compileSegment(rawSegment: String): PathSegment {
        return when {
            rawSegment.startsWith("{") && rawSegment.endsWith("}") -> {
                val name = rawSegment.substring(1, rawSegment.length - 1).trim()
                require(name.isNotBlank()) {
                    "El parametro dinamico `$rawSegment` no puede estar vacio."
                }
                PathSegment.Param(name)
            }

            rawSegment.startsWith("*") -> {
                val name = rawSegment.removePrefix("*").trim().ifBlank { "wildcard" }
                PathSegment.Wildcard(name)
            }

            else -> PathSegment.Static(rawSegment)
        }
    }

    private fun validateSegments(
        segments: List<PathSegment>,
        normalizedPath: String
    ) {
        val wildcardIndex = segments.indexOfFirst { it is PathSegment.Wildcard }
        require(wildcardIndex == -1 || wildcardIndex == segments.lastIndex) {
            "La ruta `$normalizedPath` solo puede declarar wildcard en el ultimo segmento."
        }
    }
}

