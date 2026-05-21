package kernel.multisurface

/**
 * Overrides opcionales al abrir una surface definida en catalogo.
 */
data class SurfaceLaunchOptions(
    val title: String? = null,
    val targetDisplayIndex: Int? = null,
    val fullscreen: Boolean? = null,
    val externalDisplayPreferred: Boolean? = null,
    val visible: Boolean? = null,
    val capabilities: SurfaceCapabilities? = null,
    val initialProjection: SurfaceProjection? = null
)
