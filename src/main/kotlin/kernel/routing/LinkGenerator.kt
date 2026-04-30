package kernel.routing

/**
 * Genera enlaces desktop a partir de rutas lógicas de la app.
 *
 * La app define su scheme una sola vez y el resto del sistema trabaja con
 * paths neutrales como `/dashboard` o `docs/detalle`.
 */
class LinkGenerator(
    private val scheme: String
) {
    fun desktop(pathOrUri: String): String {
        val trimmed = pathOrUri.trim()
        if (matches(trimmed)) {
            return trimmed
        }

        val normalizedPath = when {
            trimmed.isBlank() -> "/"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }

        return "$scheme://$normalizedPath"
    }

    fun matches(candidate: String): Boolean {
        return candidate.trim().startsWith("$scheme://")
    }

    fun scheme(): String = scheme
}
