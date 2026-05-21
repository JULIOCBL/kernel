package kernel.multisurface

/**
 * Configuracion resuelta lista para abrir una surface desde catalogo.
 */
data class SurfaceLaunchPlan(
    val definitionId: String,
    val descriptor: SurfaceDescriptor,
    val initialProjection: SurfaceProjection = SurfaceProjection()
)
