package kernel.windowing

/**
 * Define una ventana reutilizable disponible para la aplicacion.
 *
 * Representa una pantalla hija funcional con una configuracion base que luego
 * puede materializarse en instancias concretas.
 */
data class WindowDefinition(
    val id: String,
    val title: String,
    val titleKey: String? = null,
    val widthDp: Int = 860,
    val heightDp: Int = 620,
    val resizable: Boolean = true,
    val defaultProps: Map<String, Any?> = emptyMap()
)
