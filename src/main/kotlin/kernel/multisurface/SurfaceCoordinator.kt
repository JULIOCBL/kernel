package kernel.multisurface

/**
 * Punto de entrada para intenciones emitidas por surfaces interactivas.
 *
 * La surface no modifica el estado critico directamente: delega en un
 * coordinador que decide si la accion se acepta y como se proyecta al resto del
 * sistema.
 */
fun interface SurfaceCoordinator {
    fun dispatch(surfaceId: String, action: SurfaceAction): Boolean
}

object NoOpSurfaceCoordinator : SurfaceCoordinator {
    override fun dispatch(surfaceId: String, action: SurfaceAction): Boolean = false
}
