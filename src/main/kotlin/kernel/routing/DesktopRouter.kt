package kernel.routing

import java.net.URI

/**
 * Representa una ruta de escritorio procesada.
 */
private data class CompiledRoute(
    val pattern: Regex,
    val paramNames: List<String>,
    val action: (Map<String, String>) -> Any
)

class DesktopRouter {
    // Rutas sin parámetros para acceso O(1)
    private val staticRoutes = HashMap<String, (Map<String, String>) -> Any>()

    // Rutas dinámicas pre-compiladas
    private val dynamicRoutes = mutableListOf<CompiledRoute>()

    /**
     * Registra una ruta. Detecta automáticamente si es estática o dinámica.
     */
    fun get(path: String, action: (Map<String, String>) -> Any) {
        val normalized = path.trim('/')
        if (!normalized.contains('{')) {
            staticRoutes[normalized] = action
        } else {
            compileDynamicRoute(normalized, action)
        }
    }

    /**
     * Resuelve una URI de forma ultra-rápida.
     */
    fun resolve(uriString: String): Pair<((Map<String, String>) -> Any), Map<String, String>>? {
        val uri = URI.create(uriString)
        if (uri.scheme != "myapp") return null

        val path = (uri.authority ?: "") + uri.path
        val normalizedPath = path.trim('/')

        // 1. Intento de búsqueda estática (O(1))
        val staticAction = staticRoutes[normalizedPath]
        if (staticAction != null) {
            return staticAction to extractQueryParams(uri)
        }

        // 2. Búsqueda dinámica con Regex pre-compilados
        for (route in dynamicRoutes) {
            val match = route.pattern.matchEntire(normalizedPath)
            if (match != null) {
                val params = extractQueryParams(uri).toMutableMap()
                route.paramNames.forEachIndexed { index, name ->
                    params[name] = match.groupValues[index + 1]
                }
                return route.action to params
            }
        }

        return null
    }

    private fun extractQueryParams(uri: URI): Map<String, String> {
        val query = uri.query ?: return emptyMap()
        val params = mutableMapOf<String, String>()
        query.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) params[pair.substring(0, idx)] = pair.substring(idx + 1)
        }
        return params
    }

    private fun compileDynamicRoute(path: String, action: (Map<String, String>) -> Any) {
        val paramNames = mutableListOf<String>()
        val regexString = path.replace(Regex("\\{([^}]+)\\}")) {
            paramNames.add(it.groupValues[1])
            "([^/]+)"
        }
        dynamicRoutes.add(CompiledRoute(Regex("^$regexString$"), paramNames, action))
    }
}