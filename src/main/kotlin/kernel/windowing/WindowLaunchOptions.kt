package kernel.windowing

/**
 * Overrides opcionales al abrir una instancia de ventana.
 */
data class WindowLaunchOptions(
    val title: String? = null,
    val titleKey: String? = null,
    val props: Map<String, Any?> = emptyMap(),
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val resizable: Boolean? = null
)
