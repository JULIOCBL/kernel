package kernel.multisurface

/**
 * Capacidades declarativas de una surface.
 *
 * El kernel puede usar estas banderas para decidir si una surface solo
 * proyecta estado o si tambien puede emitir acciones hacia el coordinador.
 */
data class SurfaceCapabilities(
    val interactionMode: SurfaceInteractionMode = SurfaceInteractionMode.PASSIVE,
    val canDispatchActions: Boolean = false,
    val canNavigate: Boolean = false
) {
    fun isInteractive(): Boolean = interactionMode == SurfaceInteractionMode.INTERACTIVE
}
