package kernel.multisurface

/**
 * Foto actual de displays disponibles y surfaces registradas.
 */
data class SurfaceTopology(
    val displayCount: Int = 1,
    val surfaces: List<SurfaceDescriptor> = emptyList()
)
