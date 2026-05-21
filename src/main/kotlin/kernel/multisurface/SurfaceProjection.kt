package kernel.multisurface

/**
 * Estado visual proyectado para una surface.
 *
 * `viewId` representa la vista o ruta logica que la surface debe renderizar.
 * `payload` permite pasar datos libres sin acoplar el kernel a un dominio.
 */
data class SurfaceProjection(
    val viewId: String = "",
    val title: String = "",
    val headline: String = "",
    val message: String = "",
    val details: List<String> = emptyList(),
    val payload: Map<String, Any?> = emptyMap()
)
