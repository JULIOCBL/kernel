package kernel.multisurface

/**
 * Define una surface reusable disponible para la aplicacion.
 *
 * Representa una superficie visual con una configuracion base que luego puede
 * materializarse en un descriptor concreto listo para abrir.
 */
data class SurfaceDefinition(
    val id: String,
    val role: SurfaceRole,
    val title: String,
    val targetDisplayIndex: Int = 0,
    val fullscreen: Boolean = false,
    val externalDisplayPreferred: Boolean = false,
    val visible: Boolean = true,
    val capabilities: SurfaceCapabilities = SurfaceCapabilities(),
    val defaultProjection: SurfaceProjection = SurfaceProjection()
)
