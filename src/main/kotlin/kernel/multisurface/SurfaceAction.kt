package kernel.multisurface

/**
 * Contrato base para acciones emitidas desde surfaces interactivas.
 */
interface SurfaceAction {
    val type: String
    val payload: Map<String, Any?>
}

/**
 * Implementacion simple para escenarios donde no hace falta una clase propia.
 */
data class BasicSurfaceAction(
    override val type: String,
    override val payload: Map<String, Any?> = emptyMap()
) : SurfaceAction
