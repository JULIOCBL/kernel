package kernel.routing

/**
 * Representa una vista de escritorio ya despachada por el kernel.
 *
 * El kernel no conoce Compose ni otra UI concreta; solo materializa una vista
 * declarativa que el consumidor final puede renderizar.
 */
data class DesktopView(
    val name: String,
    val title: String = name,
    val model: Map<String, Any?> = emptyMap()
)
