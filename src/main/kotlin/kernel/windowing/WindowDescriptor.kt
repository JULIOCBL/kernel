package kernel.windowing

/**
 * Instancia abierta de una ventana hija.
 */
data class WindowDescriptor(
    val instanceId: String,
    val definitionId: String,
    val title: String,
    val titleKey: String? = null,
    val props: Map<String, Any?> = emptyMap(),
    val widthDp: Int = 860,
    val heightDp: Int = 620,
    val resizable: Boolean = true
)
